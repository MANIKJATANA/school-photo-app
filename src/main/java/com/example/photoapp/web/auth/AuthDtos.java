package com.example.photoapp.web.auth;

import com.example.photoapp.domain.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotNull UUID schoolId,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 1, max = 200) String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record TokenResponse(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            MeResponse user) {}

    public record MeResponse(UUID userId, UUID schoolId, Role role) {}
}
