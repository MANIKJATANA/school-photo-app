package com.example.photoapp.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class TaggingDtos {

    private TaggingDtos() {}

    /** Confidence is required: ML callbacks set 0..1, manual tags use 1.0. */
    public record AddTagRequest(
            @NotNull UUID studentId,
            @DecimalMin("0.0") @DecimalMax("1.0") Float confidence) {}

    public record TagResponse(
            UUID photoId,
            UUID studentId,
            UUID eventId,
            float confidence,
            Boolean isConfirmed,
            Instant createdAt) {}
}
