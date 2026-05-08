package com.example.photoapp.security;

import com.example.photoapp.domain.user.Role;

import java.util.UUID;

/**
 * Resolved viewer identity for the current request. Populated by the JWT
 * filter (Slice 2b) from a validated access token. Services and controllers
 * consume this rather than threading {@code (userId, schoolId, role)}
 * individually.
 */
public record Principal(UUID userId, UUID schoolId, Role role) {

    public boolean isAdmin()   { return role == Role.ADMIN; }
    public boolean isTeacher() { return role == Role.TEACHER; }
    public boolean isStudent() { return role == Role.STUDENT; }
}
