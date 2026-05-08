package com.example.photoapp.security.jwt;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.security.Principal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtIssuerVerifierTest {

    private static final Instant T0 = Instant.parse("2030-01-01T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID SCHOOL_ID = UUID.fromString("01900000-0000-7000-8000-000000000002");

    private final JwtProperties props = new JwtProperties(
            "test-secret-must-be-at-least-32-bytes-long-for-hs256-ok",
            "photoapp-test",
            Duration.ofMinutes(15),
            Duration.ofDays(14));

    @Test
    void access_token_round_trip_preserves_principal() {
        JwtIssuer issuer = new JwtIssuer(props, Clock.fixed(T0, ZoneOffset.UTC));
        JwtVerifier verifier = new JwtVerifier(props, Clock.fixed(T0.plusSeconds(60), ZoneOffset.UTC));

        AppToken token = issuer.issueAccess(new Principal(USER_ID, SCHOOL_ID, Role.ADMIN));
        Principal got = verifier.verifyAccess(token.token());

        assertThat(got.userId()).isEqualTo(USER_ID);
        assertThat(got.schoolId()).isEqualTo(SCHOOL_ID);
        assertThat(got.role()).isEqualTo(Role.ADMIN);
        assertThat(token.expiresAt()).isEqualTo(T0.plus(props.accessTtl()));
    }

    @Test
    void refresh_token_round_trip_uses_refresh_ttl() {
        JwtIssuer issuer = new JwtIssuer(props, Clock.fixed(T0, ZoneOffset.UTC));
        JwtVerifier verifier = new JwtVerifier(props, Clock.fixed(T0.plusSeconds(60), ZoneOffset.UTC));

        AppToken token = issuer.issueRefresh(new Principal(USER_ID, SCHOOL_ID, Role.STUDENT));
        Principal got = verifier.verifyRefresh(token.token());

        assertThat(got.role()).isEqualTo(Role.STUDENT);
        assertThat(token.expiresAt()).isEqualTo(T0.plus(props.refreshTtl()));
    }

    @Test
    void access_token_verified_as_refresh_is_rejected() {
        JwtIssuer issuer = new JwtIssuer(props, Clock.fixed(T0, ZoneOffset.UTC));
        JwtVerifier verifier = new JwtVerifier(props, Clock.fixed(T0.plusSeconds(60), ZoneOffset.UTC));

        AppToken access = issuer.issueAccess(new Principal(USER_ID, SCHOOL_ID, Role.TEACHER));

        assertThatThrownBy(() -> verifier.verifyRefresh(access.token()))
                .isInstanceOf(Errors.Unauthorized.class);
    }

    @Test
    void refresh_token_verified_as_access_is_rejected() {
        JwtIssuer issuer = new JwtIssuer(props, Clock.fixed(T0, ZoneOffset.UTC));
        JwtVerifier verifier = new JwtVerifier(props, Clock.fixed(T0.plusSeconds(60), ZoneOffset.UTC));

        AppToken refresh = issuer.issueRefresh(new Principal(USER_ID, SCHOOL_ID, Role.TEACHER));

        assertThatThrownBy(() -> verifier.verifyAccess(refresh.token()))
                .isInstanceOf(Errors.Unauthorized.class);
    }

    @Test
    void tampered_token_is_rejected() {
        JwtIssuer issuer = new JwtIssuer(props, Clock.fixed(T0, ZoneOffset.UTC));
        JwtVerifier verifier = new JwtVerifier(props, Clock.fixed(T0.plusSeconds(60), ZoneOffset.UTC));

        AppToken token = issuer.issueAccess(new Principal(USER_ID, SCHOOL_ID, Role.ADMIN));
        // Flip the last char of the signature segment.
        String tampered = flipLastChar(token.token());

        assertThatThrownBy(() -> verifier.verifyAccess(tampered))
                .isInstanceOf(Errors.Unauthorized.class);
    }

    @Test
    void expired_token_is_rejected() {
        JwtIssuer issuer = new JwtIssuer(props, Clock.fixed(T0, ZoneOffset.UTC));
        // Verifier clock is well past the access TTL.
        JwtVerifier verifier = new JwtVerifier(props,
                Clock.fixed(T0.plus(props.accessTtl()).plusSeconds(1), ZoneOffset.UTC));

        AppToken token = issuer.issueAccess(new Principal(USER_ID, SCHOOL_ID, Role.ADMIN));

        assertThatThrownBy(() -> verifier.verifyAccess(token.token()))
                .isInstanceOf(Errors.Unauthorized.class);
    }

    @Test
    void token_from_different_issuer_config_is_rejected() {
        JwtProperties otherIssuerProps = new JwtProperties(
                props.secret(), "different-issuer", props.accessTtl(), props.refreshTtl());
        JwtIssuer otherIssuer = new JwtIssuer(otherIssuerProps, Clock.fixed(T0, ZoneOffset.UTC));
        JwtVerifier ourVerifier = new JwtVerifier(props, Clock.fixed(T0.plusSeconds(60), ZoneOffset.UTC));

        AppToken token = otherIssuer.issueAccess(new Principal(USER_ID, SCHOOL_ID, Role.ADMIN));

        assertThatThrownBy(() -> ourVerifier.verifyAccess(token.token()))
                .isInstanceOf(Errors.Unauthorized.class);
    }

    @Test
    void token_signed_with_different_secret_is_rejected() {
        JwtProperties otherSecretProps = new JwtProperties(
                "different-secret-also-at-least-32-bytes-long-okay-good",
                props.issuer(), props.accessTtl(), props.refreshTtl());
        JwtIssuer otherIssuer = new JwtIssuer(otherSecretProps, Clock.fixed(T0, ZoneOffset.UTC));
        JwtVerifier ourVerifier = new JwtVerifier(props, Clock.fixed(T0.plusSeconds(60), ZoneOffset.UTC));

        AppToken token = otherIssuer.issueAccess(new Principal(USER_ID, SCHOOL_ID, Role.ADMIN));

        assertThatThrownBy(() -> ourVerifier.verifyAccess(token.token()))
                .isInstanceOf(Errors.Unauthorized.class);
    }

    private static String flipLastChar(String token) {
        char last = token.charAt(token.length() - 1);
        char swapped = last == 'A' ? 'B' : 'A';
        return token.substring(0, token.length() - 1) + swapped;
    }
}
