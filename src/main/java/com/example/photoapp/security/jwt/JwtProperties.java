package com.example.photoapp.security.jwt;

import java.time.Duration;

/**
 * Configuration for {@link JwtIssuer} / {@link JwtVerifier}. Holds the signing
 * secret, the issuer claim, and TTLs for access and refresh tokens. Wiring
 * from {@code application.properties} happens in Slice 2b-2's SecurityConfig.
 *
 * @param secret      HMAC secret. Must be at least 32 bytes (256 bits) — JJWT
 *                    enforces this for HS256 and rejects weaker keys.
 * @param issuer      "iss" claim value, e.g. "photoapp".
 * @param accessTtl   how long access tokens stay valid.
 * @param refreshTtl  how long refresh tokens stay valid.
 */
public record JwtProperties(String secret, String issuer, Duration accessTtl, Duration refreshTtl) {}
