package com.example.photoapp.domain.tagging;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@link StudentEvent}: {@code (student_id, event_id)}.
 */
@Embeddable
public record StudentEventId(
        @Column(name = "student_id", nullable = false, updatable = false) UUID studentId,
        @Column(name = "event_id",   nullable = false, updatable = false) UUID eventId) implements Serializable {
}
