package com.example.photoapp.web.onboarding;

import com.example.photoapp.service.onboarding.OnboardingService;
import com.example.photoapp.web.onboarding.OnboardingDtos.CreateSchoolRequest;
import com.example.photoapp.web.onboarding.OnboardingDtos.OnboardingResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    static final String KEY_HEADER = "X-Onboarding-Key";

    private final OnboardingService onboarding;

    public OnboardingController(OnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    @PostMapping
    public OnboardingResponse create(
            @RequestHeader(value = KEY_HEADER, required = false) String key,
            @Valid @RequestBody CreateSchoolRequest req) {
        return onboarding.bootstrapSchool(key, req);
    }
}
