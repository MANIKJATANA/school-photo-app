package com.example.photoapp.security.jwt;

import java.time.Instant;

/**
 * Opaque envelope for an issued JWT and its expiry. {@link JwtIssuer} returns
 * this; the auth controller (Slice 2b-2) shapes it into an HTTP response.
 */
public record AppToken(String token, Instant expiresAt) {}
