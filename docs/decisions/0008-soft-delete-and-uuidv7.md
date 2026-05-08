# ADR 0008: Soft delete via `deleted_at`; UUIDv7 for primary keys

- **Status**: Accepted
- **Date**: 2026-05-08
- **Deciders**: Architect-designer

## Context

Two cross-cutting choices:

**Deletion**: Hard deletes break ML provenance. If `student` rows can be hard-deleted, the FK from `photo_student` orphans. Recovery from accidental deletion becomes restore-from-backup, not flip-a-flag. Audit trails reference deleted rows.

**Primary keys**: Choices are sequential `BIGSERIAL`, UUIDv4, or UUIDv7. Sequential keys leak ordering and rate, and cause contention on a single sequence. UUIDv4 is random — bad for B-tree locality on high-volume tables. UUIDv7 is sortable by time embedded in the high bits — keeps B-trees happy and is partition-friendly.

## Decision

**Soft delete** via `deleted_at TIMESTAMPTZ` on every domain entity. Hard deletes are forbidden except for the outbox prune (where rows are processed, ordered, and bounded). Repositories filter `WHERE deleted_at IS NULL` by default; admin-only paths can override.

**UUIDv7** primary keys, generated app-side via `com.github.f4b6a3:uuid-creator`. The DB does not generate them. Exception: `outbox.id` is `BIGSERIAL` because ordered processing matters and contention is bounded by the poller batch size.

## Consequences

### Positive

- Soft delete preserves ML provenance and audit trails.
- Soft delete makes "undelete" a config flip rather than a restore.
- UUIDv7 keys are partition-friendly (the `photo` PK `(event_id, id)` indexes time-ordered within each partition).
- UUIDv7 avoids sequence contention across pods.
- UUIDs in URLs leak no sequence info to clients.

### Negative

- Every read path needs the `deleted_at IS NULL` filter. Easy to forget; the reviewer enforces.
- UUIDv7 is 16 bytes vs. 8 for `BIGSERIAL` — index size doubles. Acceptable: index-only scans on UUIDv7 are still fast and we save the sequence-contention cost.
- Tests need to assert soft-delete semantics — "delete" doesn't actually remove the row.

### Neutral

- We use `deleted_at` not `is_deleted` so we have the *when* for free.

## Alternatives considered

- **Hard delete** — rejected: breaks FKs from ML matches; loses audit trail.
- **`BIGSERIAL` PKs** — rejected: contention, ordering leak, cross-DB-portability concerns.
- **UUIDv4 PKs** — rejected: B-tree locality is worse, no time embedding.

## References

- Plan file, "Schema" section ("All entities use UUIDv7 PKs and soft-delete via `deleted_at`").
