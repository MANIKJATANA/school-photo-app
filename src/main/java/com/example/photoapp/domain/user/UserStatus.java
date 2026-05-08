package com.example.photoapp.domain.user;

/**
 * Lifecycle status on {@link AppUser}. Mirrors the CHECK constraint in
 * V1__baseline.sql (app_user.status).
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    PENDING
}
