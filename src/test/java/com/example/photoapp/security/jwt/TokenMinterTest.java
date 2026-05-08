package com.example.photoapp.security.jwt;

import com.example.photoapp.domain.user.Role;
import com.example.photoapp.security.Principal;
import com.example.photoapp.web.auth.AuthDtos.TokenResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenMinterTest {

    private static final UUID USER_ID  = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID SCHOOL   = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final Instant T0    = Instant.parse("2030-01-01T00:00:00Z");

    private final JwtProperties props = new JwtProperties(
            "test-secret-must-be-at-least-32-bytes-long-for-hs256-ok",
            "photoapp-test",
            Duration.ofMinutes(15),
            Duration.ofDays(14));
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
    private final JwtIssuer issuer = new JwtIssuer(props, clock);
    private final JwtVerifier verifier = new JwtVerifier(props, clock);

    private final TokenMinter minter = new TokenMinter(issuer);

    @Test
    void mintPair_returns_parsable_tokens_and_user_payload() {
        Principal p = new Principal(USER_ID, SCHOOL, Role.ADMIN);

        TokenResponse resp = minter.mintPair(p);

        assertThat(resp.accessToken()).isNotBlank();
        assertThat(resp.refreshToken()).isNotBlank();
        assertThat(resp.user().userId()).isEqualTo(USER_ID);
        assertThat(resp.user().schoolId()).isEqualTo(SCHOOL);
        assertThat(resp.user().role()).isEqualTo(Role.ADMIN);
        assertThat(resp.accessTokenExpiresAt()).isEqualTo(T0.plus(props.accessTtl()));
        assertThat(resp.refreshTokenExpiresAt()).isEqualTo(T0.plus(props.refreshTtl()));

        // Tokens parse cleanly through the same key.
        assertThat(verifier.verifyAccess(resp.accessToken()).userId()).isEqualTo(USER_ID);
        assertThat(verifier.verifyRefresh(resp.refreshToken()).userId()).isEqualTo(USER_ID);
    }
}
