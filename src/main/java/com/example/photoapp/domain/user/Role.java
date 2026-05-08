package com.example.photoapp.domain.user;

/**
 * Authorisation role on {@link AppUser}. Mirrors the CHECK constraint in
 * V1__baseline.sql (app_user.role).
 */
public enum Role {
    ADMIN,
    TEACHER,
    STUDENT
}
