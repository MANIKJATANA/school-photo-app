package com.example.photoapp.service.onboarding;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.domain.event.Event;
import com.example.photoapp.domain.school.School;
import com.example.photoapp.domain.user.AppUser;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.repository.event.EventRepository;
import com.example.photoapp.repository.school.SchoolRepository;
import com.example.photoapp.repository.user.AppUserRepository;
import com.example.photoapp.security.jwt.JwtIssuer;
import com.example.photoapp.security.jwt.JwtProperties;
import com.example.photoapp.security.jwt.JwtVerifier;
import com.example.photoapp.web.onboarding.OnboardingDtos.AdminPart;
import com.example.photoapp.web.onboarding.OnboardingDtos.CreateSchoolRequest;
import com.example.photoapp.web.onboarding.OnboardingDtos.OnboardingResponse;
import com.example.photoapp.web.onboarding.OnboardingDtos.SchoolPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnboardingServiceTest {

    private static final String VALID_KEY = "test-onboarding-key";
    private static final String PLAIN_PASSWORD = "correct-horse-battery-staple";

    private final JwtProperties props = new JwtProperties(
            "test-secret-must-be-at-least-32-bytes-long-for-hs256-ok",
            "photoapp-test",
            Duration.ofMinutes(15),
            Duration.ofDays(14));
    private final Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final JwtIssuer issuer = new JwtIssuer(props, clock);
    private final JwtVerifier verifier = new JwtVerifier(props, clock);

    private SchoolRepository schools;
    private AppUserRepository users;
    private EventRepository events;
    private OnboardingService onboarding;

    @BeforeEach
    void setUp() {
        schools = mock(SchoolRepository.class);
        users = mock(AppUserRepository.class);
        events = mock(EventRepository.class);

        // Stub save() to assign an id (mimicking what JPA's @PrePersist does).
        when(schools.save(any())).thenAnswer(inv -> {
            School s = inv.getArgument(0);
            setId(s, UUID.randomUUID());
            return s;
        });
        when(users.saveAndFlush(any())).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            setId(u, UUID.randomUUID());
            return u;
        });
        when(events.save(any())).thenAnswer(inv -> {
            Event e = inv.getArgument(0);
            setId(e, UUID.randomUUID());
            return e;
        });

        onboarding = new OnboardingService(schools, users, events, encoder, issuer, VALID_KEY);
    }

    @Test
    void happy_path_returns_ids_and_tokens() {
        OnboardingResponse resp = onboarding.bootstrapSchool(VALID_KEY, validRequest());

        assertThat(resp.schoolId()).isNotNull();
        assertThat(resp.adminUserId()).isNotNull();
        assertThat(resp.defaultEventId()).isNotNull();
        assertThat(resp.tokens().accessToken()).isNotBlank();
        assertThat(resp.tokens().refreshToken()).isNotBlank();
        // Tokens parse cleanly under our JwtVerifier with the right claims.
        assertThat(verifier.verifyAccess(resp.tokens().accessToken()).userId()).isEqualTo(resp.adminUserId());
        assertThat(verifier.verifyAccess(resp.tokens().accessToken()).role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void wrong_key_throws_forbidden() {
        assertThatThrownBy(() -> onboarding.bootstrapSchool("wrong-key", validRequest()))
                .isInstanceOf(Errors.Forbidden.class);
    }

    @Test
    void null_key_throws_forbidden() {
        assertThatThrownBy(() -> onboarding.bootstrapSchool(null, validRequest()))
                .isInstanceOf(Errors.Forbidden.class);
    }

    @Test
    void empty_key_throws_forbidden() {
        assertThatThrownBy(() -> onboarding.bootstrapSchool("", validRequest()))
                .isInstanceOf(Errors.Forbidden.class);
    }

    @Test
    void duplicate_email_at_user_save_throws_conflict() {
        doThrow(new DataIntegrityViolationException("uq violation"))
                .when(users).saveAndFlush(any());

        assertThatThrownBy(() -> onboarding.bootstrapSchool(VALID_KEY, validRequest()))
                .isInstanceOf(Errors.Conflict.class);
    }

    @Test
    void admin_password_is_hashed_not_stored_plaintext() {
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        onboarding.bootstrapSchool(VALID_KEY, validRequest());

        verify(users).saveAndFlush(captor.capture());
        AppUser saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isNotEqualTo(PLAIN_PASSWORD);
        assertThat(encoder.matches(PLAIN_PASSWORD, saved.getPasswordHash())).isTrue();
    }

    @Test
    void default_event_is_created_with_is_default_true_and_admin_as_creator() {
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        onboarding.bootstrapSchool(VALID_KEY, validRequest());

        verify(events).save(captor.capture());
        Event saved = captor.getValue();
        assertThat(saved.isDefault()).isTrue();
        assertThat(saved.getCreatedBy()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Uncategorised");
    }

    private CreateSchoolRequest validRequest() {
        return new CreateSchoolRequest(
                new SchoolPart("Test School", "1 Main St", "info@example.com"),
                new AdminPart("admin@example.com", PLAIN_PASSWORD, "555-0100"));
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field f = entity.getClass().getSuperclass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
