package com.example.photoapp.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public final class EnrolmentDtos {

    private EnrolmentDtos() {}

    public record EnrolStudentRequest(@NotNull UUID studentId) {}

    public record EnrolmentResponse(
            UUID id,
            UUID studentId,
            UUID classId,
            LocalDate validFrom,
            LocalDate validTo) {}
}
