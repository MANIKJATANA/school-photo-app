package com.example.photoapp.common.school;

import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

/**
 * Reusable JPA Specification fragments for the {@code school_id =
 * :schoolId AND deleted_at IS NULL} filter that applies to every read path.
 * Service-layer code never builds this predicate inline — always go through
 * one of these helpers so the rule is grep-friendly and the reviewer can
 * verify it on every diff.
 *
 * Entities expose the FK as a plain {@code schoolId} attribute (never as a
 * managed {@code @ManyToOne School} association — domain entities stay free
 * of cross-aggregate references for portability and lazy-loading sanity).
 *
 * The request-scoped holder bean ({@code SchoolContext}) lands in Slice 2
 * once the JWT filter exists. This class is purely static helpers — usable
 * from any context that already has a {@code schoolId}.
 */
public final class SchoolScopes {

    private SchoolScopes() {}

    /** {@code school_id = :schoolId}. */
    public static <T> Specification<T> bySchool(UUID schoolId) {
        return (root, q, cb) -> cb.equal(root.get("schoolId"), schoolId);
    }

    /** {@code deleted_at IS NULL} — exclude soft-deleted rows. */
    public static <T> Specification<T> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedAt"));
    }

    /** Composed: school-scoped AND not soft-deleted. The default Phase 1 read filter. */
    public static <T> Specification<T> activeInSchool(UUID schoolId) {
        return SchoolScopes.<T>bySchool(schoolId).and(notDeleted());
    }
}
