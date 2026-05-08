-- ============================================================================
-- V1__baseline.sql — initial schema for the school photo-management system.
--
-- Conventions:
--   * UUIDv7 primary keys are generated app-side (no DB sequences for entity PKs).
--   * Every domain row has school_id (school scope) + soft-delete via deleted_at.
--   * `photo` is partitioned BY HASH (event_id) into 16 partitions.
--   * PG-specific features (HASH partitioning, partial unique indexes, JSONB,
--     SKIP LOCKED) live ONLY in this DDL or behind repository-interface impls.
-- ============================================================================

-- ============ updated_at trigger helper ============
CREATE OR REPLACE FUNCTION touch_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============ school ============
CREATE TABLE school (
  id            UUID PRIMARY KEY,
  name          TEXT NOT NULL,
  address       TEXT,
  contact_email TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ
);
CREATE TRIGGER trg_school_updated BEFORE UPDATE ON school
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ============ app_user (login identity) ============
CREATE TABLE app_user (
  id             UUID PRIMARY KEY,
  school_id      UUID NOT NULL REFERENCES school(id),
  email          TEXT NOT NULL,
  phone          TEXT,
  password_hash  TEXT,
  role           TEXT NOT NULL CHECK (role IN ('ADMIN', 'TEACHER', 'STUDENT')),
  status         TEXT NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE', 'SUSPENDED', 'PENDING')),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at     TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_app_user_school_email_lower
  ON app_user(school_id, lower(email));
CREATE INDEX ix_app_user_school_role
  ON app_user(school_id, role) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_app_user_updated BEFORE UPDATE ON app_user
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ============ student ============
CREATE TABLE student (
  id            UUID PRIMARY KEY,
  school_id     UUID NOT NULL REFERENCES school(id),
  user_id       UUID REFERENCES app_user(id),
  first_name    TEXT NOT NULL,
  last_name     TEXT NOT NULL,
  roll_number   TEXT,
  date_of_birth DATE,
  face_embedding_status TEXT NOT NULL DEFAULT 'PENDING'
                  CHECK (face_embedding_status IN ('PENDING', 'ENROLLED', 'FAILED')),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ,
  UNIQUE (school_id, roll_number)
);
CREATE INDEX ix_student_school ON student(school_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_student_user   ON student(user_id);
CREATE TRIGGER trg_student_updated BEFORE UPDATE ON student
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ============ teacher ============
CREATE TABLE teacher (
  id            UUID PRIMARY KEY,
  school_id     UUID NOT NULL REFERENCES school(id),
  user_id       UUID NOT NULL REFERENCES app_user(id),
  first_name    TEXT NOT NULL,
  last_name     TEXT NOT NULL,
  employee_id   TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ,
  UNIQUE (school_id, employee_id)
);
CREATE INDEX ix_teacher_school ON teacher(school_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_teacher_user   ON teacher(user_id);
CREATE TRIGGER trg_teacher_updated BEFORE UPDATE ON teacher
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ============ klass (class) ============
CREATE TABLE klass (
  id            UUID PRIMARY KEY,
  school_id     UUID NOT NULL REFERENCES school(id),
  name          TEXT NOT NULL,
  academic_year TEXT NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ,
  UNIQUE (school_id, name, academic_year)
);
CREATE INDEX ix_klass_school_year ON klass(school_id, academic_year);
CREATE TRIGGER trg_klass_updated BEFORE UPDATE ON klass
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ============ student_class (join, temporal) ============
CREATE TABLE student_class (
  id           UUID PRIMARY KEY,
  student_id   UUID NOT NULL REFERENCES student(id),
  class_id     UUID NOT NULL REFERENCES klass(id),
  valid_from   DATE NOT NULL DEFAULT CURRENT_DATE,
  valid_to     DATE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- One active class per student.
CREATE UNIQUE INDEX uq_student_class_active
  ON student_class(student_id) WHERE valid_to IS NULL;
CREATE INDEX ix_student_class_class
  ON student_class(class_id) WHERE valid_to IS NULL;

-- ============ class_teacher (M:N) ============
CREATE TABLE class_teacher (
  class_id    UUID NOT NULL REFERENCES klass(id),
  teacher_id  UUID NOT NULL REFERENCES teacher(id),
  role        TEXT NOT NULL DEFAULT 'TEACHER'
              CHECK (role IN ('CLASS_TEACHER', 'SUBJECT_TEACHER', 'TEACHER')),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (class_id, teacher_id)
);
CREATE INDEX ix_class_teacher_teacher ON class_teacher(teacher_id);

-- ============ event ============
CREATE TABLE event (
  id          UUID PRIMARY KEY,
  school_id   UUID NOT NULL REFERENCES school(id),
  name        TEXT NOT NULL,
  description TEXT,
  event_date  DATE,
  is_default  BOOLEAN NOT NULL DEFAULT FALSE,
  created_by  UUID NOT NULL REFERENCES app_user(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at  TIMESTAMPTZ
);
-- Exactly one default event per school.
CREATE UNIQUE INDEX uq_event_default_per_school
  ON event(school_id) WHERE is_default = TRUE;
CREATE INDEX ix_event_school_date
  ON event(school_id, event_date DESC) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_event_updated BEFORE UPDATE ON event
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ============ photo (PARTITIONED BY HASH (event_id)) ============
CREATE TABLE photo (
  id              UUID NOT NULL,
  event_id        UUID NOT NULL REFERENCES event(id),
  school_id       UUID NOT NULL REFERENCES school(id),
  blob_key        TEXT NOT NULL,
  blob_bucket     TEXT NOT NULL,
  content_type    TEXT NOT NULL,
  size_bytes      BIGINT NOT NULL,
  width_px        INT,
  height_px       INT,
  taken_at        TIMESTAMPTZ,
  uploaded_by     UUID NOT NULL REFERENCES app_user(id),
  upload_status   TEXT NOT NULL DEFAULT 'PENDING'
                   CHECK (upload_status IN ('PENDING', 'UPLOADED', 'FAILED')),
  ml_status       TEXT NOT NULL DEFAULT 'PENDING'
                   CHECK (ml_status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED', 'SKIPPED')),
  ml_processed_at TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at      TIMESTAMPTZ,
  PRIMARY KEY (event_id, id)
) PARTITION BY HASH (event_id);

-- 16 hash partitions
CREATE TABLE photo_p00 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE photo_p01 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 1);
CREATE TABLE photo_p02 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 2);
CREATE TABLE photo_p03 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 3);
CREATE TABLE photo_p04 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 4);
CREATE TABLE photo_p05 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 5);
CREATE TABLE photo_p06 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 6);
CREATE TABLE photo_p07 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 7);
CREATE TABLE photo_p08 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 8);
CREATE TABLE photo_p09 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 9);
CREATE TABLE photo_p10 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 10);
CREATE TABLE photo_p11 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 11);
CREATE TABLE photo_p12 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 12);
CREATE TABLE photo_p13 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 13);
CREATE TABLE photo_p14 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 14);
CREATE TABLE photo_p15 PARTITION OF photo FOR VALUES WITH (MODULUS 16, REMAINDER 15);

-- Indexes on partitioned table; PG creates per-partition indexes automatically.
CREATE INDEX ix_photo_event_created  ON photo(event_id, created_at DESC);
CREATE INDEX ix_photo_school_created ON photo(school_id, created_at DESC);
CREATE INDEX ix_photo_uploaded_by    ON photo(uploaded_by);
CREATE INDEX ix_photo_ml_status_pending
  ON photo(ml_status) WHERE ml_status IN ('PENDING', 'PROCESSING');

CREATE TRIGGER trg_photo_updated BEFORE UPDATE ON photo
  FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ============ ml_run (provenance + idempotency) ============
CREATE TABLE ml_run (
  id            UUID PRIMARY KEY,
  event_id      UUID,
  triggered_by  UUID REFERENCES app_user(id),
  status        TEXT NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCESS', 'FAILED')),
  model_version TEXT NOT NULL,
  photo_count   INT,
  match_count   INT,
  started_at    TIMESTAMPTZ,
  finished_at   TIMESTAMPTZ,
  error         TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ml_run_status_created
  ON ml_run(status, created_at DESC);

-- ============ photo_student (ML output, M:N) ============
CREATE TABLE photo_student (
  photo_id     UUID NOT NULL,
  student_id   UUID NOT NULL REFERENCES student(id),
  event_id     UUID NOT NULL,
  confidence   REAL NOT NULL CHECK (confidence BETWEEN 0 AND 1),
  bbox         JSONB,
  ml_run_id    UUID NOT NULL REFERENCES ml_run(id),
  is_confirmed BOOLEAN,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (photo_id, student_id)
);
-- THE keystone index: serves "given (student, event) → photos."
CREATE INDEX ix_photo_student_student_event
  ON photo_student(student_id, event_id, photo_id);
CREATE INDEX ix_photo_student_event ON photo_student(event_id);
CREATE INDEX ix_photo_student_run   ON photo_student(ml_run_id);

-- ============ student_event (precompute) ============
CREATE TABLE student_event (
  student_id      UUID NOT NULL REFERENCES student(id),
  event_id        UUID NOT NULL REFERENCES event(id),
  first_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  photo_count     INT NOT NULL DEFAULT 0,
  last_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (student_id, event_id)
);
CREATE INDEX ix_student_event_event ON student_event(event_id);

-- ============ share (access grants) ============
CREATE TABLE share (
  id                UUID PRIMARY KEY,
  school_id         UUID NOT NULL REFERENCES school(id),
  scope             TEXT NOT NULL CHECK (scope IN ('STUDENT', 'CLASS', 'ALL')),
  target_student_id UUID REFERENCES student(id),
  target_class_id   UUID REFERENCES klass(id),
  resource_type     TEXT NOT NULL CHECK (resource_type IN ('EVENT', 'PHOTO', 'ALBUM')),
  resource_id       UUID NOT NULL,
  shared_by         UUID NOT NULL REFERENCES app_user(id),
  expires_at        TIMESTAMPTZ,
  revoked_at        TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT share_scope_target_consistent CHECK (
    (scope = 'STUDENT' AND target_student_id IS NOT NULL AND target_class_id IS NULL) OR
    (scope = 'CLASS'   AND target_class_id   IS NOT NULL AND target_student_id IS NULL) OR
    (scope = 'ALL'     AND target_student_id IS NULL    AND target_class_id IS NULL)
  )
);
CREATE INDEX ix_share_target_student
  ON share(target_student_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_share_target_class
  ON share(target_class_id)   WHERE revoked_at IS NULL;
CREATE INDEX ix_share_school_all
  ON share(school_id) WHERE scope = 'ALL' AND revoked_at IS NULL;
CREATE INDEX ix_share_resource
  ON share(resource_type, resource_id);

-- ============ notification (in-app inbox) ============
CREATE TABLE notification (
  id                UUID PRIMARY KEY,
  recipient_user_id UUID NOT NULL REFERENCES app_user(id),
  type              TEXT NOT NULL,
  payload           JSONB NOT NULL,
  read_at           TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notification_recipient
  ON notification(recipient_user_id, created_at DESC);
CREATE INDEX ix_notification_unread
  ON notification(recipient_user_id) WHERE read_at IS NULL;

-- ============ outbox (transactional outbox) ============
-- BIGSERIAL id because ordered processing matters and contention is bounded
-- by the poller batch size. Per ADR 0008, the only non-UUIDv7 PK in the system.
CREATE TABLE outbox (
  id             BIGSERIAL PRIMARY KEY,
  aggregate_type TEXT NOT NULL,
  aggregate_id   UUID NOT NULL,
  event_type     TEXT NOT NULL,
  payload        JSONB NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at   TIMESTAMPTZ,
  attempts       INT NOT NULL DEFAULT 0,
  last_error     TEXT
);
-- Worker scans this with FOR UPDATE SKIP LOCKED.
CREATE INDEX ix_outbox_unprocessed
  ON outbox(id) WHERE processed_at IS NULL;
