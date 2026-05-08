package com.example.photoapp.domain.school;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level checks of the Auditable lifecycle hook on School. The richer
 * persistence-path assertions (UUIDv7 PK is real, deleted_at filter excludes
 * rows from the default finder, etc.) live in AppUserRepositoryTest which
 * runs against a real Postgres via Testcontainers.
 */
class SchoolEntityTest {

    @Test
    void onPersist_assigns_uuidv7_id_and_audit_timestamps() throws Exception {
        School s = new School("Test School", "1 St", "info@example.com");
        invokeOnPersist(s);

        assertThat(s.getId()).isNotNull();
        assertThat(s.getId().version()).isEqualTo(7);
        assertThat(s.getCreatedAt()).isNotNull();
        assertThat(s.getUpdatedAt()).isNotNull();
        assertThat(s.getDeletedAt()).isNull();
        assertThat(s.isDeleted()).isFalse();
    }

    @Test
    void softDelete_sets_deletedAt_and_flips_isDeleted() {
        School s = new School("Test School", null, null);
        Instant when = Instant.parse("2030-01-01T00:00:00Z");
        s.softDelete(when);

        assertThat(s.getDeletedAt()).isEqualTo(when);
        assertThat(s.isDeleted()).isTrue();
    }

    private static void invokeOnPersist(Object entity) throws Exception {
        Method m = entity.getClass().getSuperclass().getDeclaredMethod("onPersist");
        m.setAccessible(true);
        m.invoke(entity);
    }
}
