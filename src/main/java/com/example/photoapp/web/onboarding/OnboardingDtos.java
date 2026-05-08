package com.example.photoapp.web.onboarding;

import com.example.photoapp.web.auth.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class OnboardingDtos {

    private OnboardingDtos() {}

    public record CreateSchoolRequest(@Valid SchoolPart school, @Valid AdminPart admin) {}

    public record SchoolPart(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500) String address,
            @Email @Size(max = 200) String contactEmail) {}

    public record AdminPart(
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(min = 8, max = 200) String password,
            @Size(max = 50) String phone) {}

    public record OnboardingResponse(
            UUID schoolId,
            UUID adminUserId,
            UUID defaultEventId,
            TokenResponse tokens) {}
}
