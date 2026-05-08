package com.example.photoapp.domain.enrolment;

/**
 * The role a teacher plays within a specific class — distinct from the
 * user-level {@link com.example.photoapp.domain.user.Role} enum (ADMIN /
 * TEACHER / STUDENT). One teacher can be a CLASS_TEACHER in 5A and a
 * SUBJECT_TEACHER in 5B. Mirrors the V1 CHECK constraint on
 * {@code class_teacher.role}.
 */
public enum TeachingRole {
    CLASS_TEACHER,
    SUBJECT_TEACHER,
    TEACHER
}
