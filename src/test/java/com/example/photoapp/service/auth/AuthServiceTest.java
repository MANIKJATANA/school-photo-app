package com.example.photoapp.service.auth;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.domain.user.AppUser;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.domain.user.UserStatus;
import com.example.photoapp.repository.user.AppUserRepository;
import com.example.photoapp.security.jwt.JwtIssuer;
import com.example.photoapp.security.jwt.JwtProperties;
import com.example.photoapp.security.jwt.JwtVerifier;
import com.example.photoapp.web.auth.AuthDtos.LoginRequest;
import com.example.photoapp.web.auth.AuthDtos.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final Instant T0 = Instant.parse("2030-01-01T00:00:00Z");
    private static final UUID SCHOOL_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final String EMAIL = "user@example.com";
    private static final String PLAIN_PASSWORD = "correct horse battery staple";

    private final JwtProperties props = new JwtProperties(
            "test-secret-must-be-at-least-32-bytes-long-for-hs256-ok",
            "photoapp-test",
            Duration.ofMinutes(15),
            Duration.ofDays(14));
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost for fast tests
    private final JwtIssuer issuer = new JwtIssuer(props, clock);
    private final JwtVerifier verifier = new JwtVerifier(props, clock);

    private AppUserRepository repo;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        repo = mock(AppUserRepository.class);
        auth = new AuthService(repo, encoder, verifier, new com.example.photoapp.security.jwt.TokenMinter(issuer));
    }

    @Test
    void login_with_correct_password_issues_tokens_and_user_payload() {
        AppUser user = activeUser();
        when(repo.findActiveBySchoolAndEmail(SCHOOL_ID, EMAIL)).thenReturn(Optional.of(user));

        TokenResponse resp = auth.login(new LoginRequest(SCHOOL_ID, EMAIL, PLAIN_PASSWORD));

        assertThat(resp.accessToken()).isNotBlank();
        assertThat(resp.refreshToken()).isNotBlank();
        assertThat(resp.user().userId()).isEqualTo(USER_ID);
        assertThat(resp.user().role()).isEqualTo(Role.ADMIN);
        // Tokens parse cleanly under the same key.
        assertThat(verifier.verifyAccess(resp.accessToken()).userId()).isEqualTo(USER_ID);
        assertThat(verifier.verifyRefresh(resp.refreshToken()).userId()).isEqualTo(USER_ID);
    }

    @Test
    void login_with_wrong_password_throws_unauthorized() {
        AppUser user = activeUser();
        when(repo.findActiveBySchoolAndEmail(SCHOOL_ID, EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> auth.login(new LoginRequest(SCHOOL_ID, EMAIL, "wrong-password")))
                .isInstanceOf(Errors.Unauthorized.class);
    }

    @Test
    void login_with_unknown_email_throws_same_unauthorized_no_enumeration() {
        when(repo.findActiveBySchoolAndEmail(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.login(new LoginRequest(SCHOOL_ID, "unknown@example.com", PLAIN_PASSWORD)))
                .isInstanceOf(Errors.Unauthorized.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void dummy_hash_used_for_unknown_users_is_well_formed_bcrypt() throws Exception {
        // Regression: a malformed BCrypt literal causes BCryptPasswordEncoder.matches to
        // short-circuit without running BCrypt, breaking the timing-uniformity defence.
        Field f = AuthService.class.getDeclaredField("dummyHash");
        f.setAccessible(true);
        String hash = (String) f.get(auth);
        assertThat(hash).matches("^\\$2[aby]\\$\\d\\d\\$[./0-9A-Za-z]{53}$");
    }

    @Test
    void login_when_user_is_suspended_throws_unauthorized() {
        AppUser user = activeUser();
        user.setStatus(UserStatus.SUSPENDED);
        when(repo.findActiveBySchoolAndEmail(SCHOOL_ID, EMAIL)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> auth.login(new LoginRequest(SCHOOL_ID, EMAIL, PLAIN_PASSWORD)))
                .isInstanceOf(Errors.Unauthorized.class);
    }

    @Test
    void refresh_with_valid_refresh_token_issues_new_pair() {
        AppUser user = activeUser();
        when(repo.findActiveBySchoolAndEmail(SCHOOL_ID, EMAIL)).thenReturn(Optional.of(user));
        when(repo.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user));

        TokenResponse first = auth.login(new LoginRequest(SCHOOL_ID, EMAIL, PLAIN_PASSWORD));
        TokenResponse second = auth.refresh(first.refreshToken());

        assertThat(second.accessToken()).isNotBlank();
        assertThat(second.refreshToken()).isNotBlank();
        assertThat(verifier.verifyAccess(second.accessToken()).userId()).isEqualTo(USER_ID);
    }

    @Test
    void refresh_with_access_token_throws_unauthorized() {
        AppUser user = activeUser();
        when(repo.findActiveBySchoolAndEmail(SCHOOL_ID, EMAIL)).thenReturn(Optional.of(user));

        TokenResponse first = auth.login(new LoginRequest(SCHOOL_ID, EMAIL, PLAIN_PASSWORD));

        assertThatThrownBy(() -> auth.refresh(first.accessToken()))
                .isInstanceOf(Errors.Unauthorized.class);
    }

    @Test
    void refresh_after_user_soft_deleted_throws_unauthorized() {
        AppUser user = activeUser();
        when(repo.findActiveBySchoolAndEmail(SCHOOL_ID, EMAIL)).thenReturn(Optional.of(user));
        when(repo.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.empty());

        TokenResponse first = auth.login(new LoginRequest(SCHOOL_ID, EMAIL, PLAIN_PASSWORD));

        assertThatThrownBy(() -> auth.refresh(first.refreshToken()))
                .isInstanceOf(Errors.Unauthorized.class);
    }

    @Test
    void refresh_after_user_suspended_throws_unauthorized() {
        AppUser user = activeUser();
        when(repo.findActiveBySchoolAndEmail(SCHOOL_ID, EMAIL)).thenReturn(Optional.of(user));
        TokenResponse first = auth.login(new LoginRequest(SCHOOL_ID, EMAIL, PLAIN_PASSWORD));

        AppUser suspended = activeUser();
        suspended.setStatus(UserStatus.SUSPENDED);
        when(repo.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> auth.refresh(first.refreshToken()))
                .isInstanceOf(Errors.Unauthorized.class);
    }

    private AppUser activeUser() {
        AppUser u = new AppUser(SCHOOL_ID, EMAIL, encoder.encode(PLAIN_PASSWORD), Role.ADMIN);
        // Set the auditable id directly via reflection — bypass JPA hooks for a unit test.
        try {
            Field idField = u.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(u, USER_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }
}
