package com.example.photoapp.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class KlassDtos {

    private KlassDtos() {}

    public record CreateClassRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 20) String academicYear) {}

    /** Update is partial: every field is optional; null means "leave unchanged". */
    public record UpdateClassRequest(
            @Size(max = 100) String name,
            @Size(max = 20) String academicYear) {}

    public record ClassResponse(
            UUID id,
            UUID schoolId,
            String name,
            String academicYear,
            Instant createdAt,
            Instant updatedAt) {}
}
