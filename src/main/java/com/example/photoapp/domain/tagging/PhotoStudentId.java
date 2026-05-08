package com.example.photoapp.domain.tagging;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@link PhotoStudent}: {@code (photo_id, student_id)}.
 * Order chosen to match V1's index leftmost-prefix pattern; the keystone
 * read query has its own {@code (student_id, event_id, photo_id)} index.
 */
@Embeddable
public record PhotoStudentId(
        @Column(name = "photo_id",   nullable = false, updatable = false) UUID photoId,
        @Column(name = "student_id", nullable = false, updatable = false) UUID studentId) implements Serializable {
}
