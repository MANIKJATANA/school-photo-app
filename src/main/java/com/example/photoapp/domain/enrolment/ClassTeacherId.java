package com.example.photoapp.domain.enrolment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@link ClassTeacher}. Implemented as a record
 * {@code @Embeddable} — Hibernate 6+ supports this; equals/hashCode come for
 * free from the record contract.
 */
@Embeddable
public record ClassTeacherId(
        @Column(name = "class_id",   nullable = false) UUID classId,
        @Column(name = "teacher_id", nullable = false) UUID teacherId) implements Serializable {
}
