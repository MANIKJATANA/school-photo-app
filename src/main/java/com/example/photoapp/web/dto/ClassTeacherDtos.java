package com.example.photoapp.web.dto;

import com.example.photoapp.domain.enrolment.TeachingRole;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class ClassTeacherDtos {

    private ClassTeacherDtos() {}

    public record AssignTeacherRequest(
            @NotNull UUID teacherId,
            @NotNull TeachingRole role) {}

    public record UpdateAssignmentRequest(@NotNull TeachingRole role) {}

    public record AssignmentResponse(
            UUID classId,
            UUID teacherId,
            TeachingRole role,
            Instant createdAt) {}
}
