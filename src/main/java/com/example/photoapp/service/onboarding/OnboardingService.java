package com.example.photoapp.service.onboarding;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.domain.event.Event;
import com.example.photoapp.domain.school.School;
import com.example.photoapp.domain.user.AppUser;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.repository.event.EventRepository;
import com.example.photoapp.repository.school.SchoolRepository;
import com.example.photoapp.security.Principal;
import com.example.photoapp.security.jwt.AppToken;
import com.example.photoapp.security.jwt.JwtIssuer;
import com.example.photoapp.service.provisioning.UserProvisioning;
import com.example.photoapp.web.auth.AuthDtos.MeResponse;
import com.example.photoapp.web.auth.AuthDtos.TokenResponse;
import com.example.photoapp.web.onboarding.OnboardingDtos.CreateSchoolRequest;
import com.example.photoapp.web.onboarding.OnboardingDtos.OnboardingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Bootstraps a new school: school row, admin {@link AppUser}, and the
 * per-school default {@link Event} in one transaction. The endpoint sits
 * outside the JWT wall and is gated by a pre-shared key — a stop-gap until a
 * real super-admin invitation flow lands in a later slice.
 */
@Service
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);
    private static final String DEFAULT_EVENT_NAME = "Uncategorised";

    private final SchoolRepository schools;
    private final UserProvisioning userProvisioning;
    private final EventRepository events;
    private final JwtIssuer issuer;
    private final byte[] expectedKeyBytes;

    public OnboardingService(SchoolRepository schools,
                             UserProvisioning userProvisioning,
                             EventRepository events,
                             JwtIssuer issuer,
                             @Value("${photoapp.onboarding.key}") String onboardingKey) {
        this.schools = schools;
        this.userProvisioning = userProvisioning;
        this.events = events;
        this.issuer = issuer;
        this.expectedKeyBytes = onboardingKey.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(rollbackFor = Exception.class)
    public OnboardingResponse bootstrapSchool(String onboardingKey, CreateSchoolRequest req) {
        verifyKey(onboardingKey);

        School school = new School(req.school().name(), req.school().address(), req.school().contactEmail());
        schools.save(school);

        AppUser admin = userProvisioning.provision(
                school.getId(),
                req.admin().email(),
                req.admin().password(),
                Role.ADMIN,
                req.admin().phone());

        Event defaultEvent = new Event(school.getId(), DEFAULT_EVENT_NAME, admin.getId());
        defaultEvent.setDefault(true);
        defaultEvent.setDescription("Photos not yet associated with a specific event");
        events.save(defaultEvent);

        log.info("onboarded school={} admin={} default_event={}",
                school.getId(), admin.getId(), defaultEvent.getId());

        Principal principal = new Principal(admin.getId(), school.getId(), admin.getRole());
        TokenResponse tokens = mintTokens(principal);

        return new OnboardingResponse(school.getId(), admin.getId(), defaultEvent.getId(), tokens);
    }

    private void verifyKey(String provided) {
        if (provided == null) {
            log.warn("onboarding rejected: missing key");
            throw new Errors.Forbidden("Missing onboarding key");
        }
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedKeyBytes, providedBytes)) {
            log.warn("onboarding rejected: invalid key");
            throw new Errors.Forbidden("Invalid onboarding key");
        }
    }

    private TokenResponse mintTokens(Principal principal) {
        AppToken access = issuer.issueAccess(principal);
        AppToken refresh = issuer.issueRefresh(principal);
        MeResponse me = new MeResponse(principal.userId(), principal.schoolId(), principal.role());
        return new TokenResponse(
                access.token(), access.expiresAt(),
                refresh.token(), refresh.expiresAt(),
                me);
    }
}
