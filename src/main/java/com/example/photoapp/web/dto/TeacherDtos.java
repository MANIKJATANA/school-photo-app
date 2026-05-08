package com.example.photoapp.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class TeacherDtos {

    private TeacherDtos() {}

    public record CreateTeacherRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(min = 8, max = 200) String password,
            @Size(max = 50) String employeeId,
            @Size(max = 50) String phone) {}

    /** Update is partial: every field is optional; null means "leave unchanged". */
    public record UpdateTeacherRequest(
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 50) String employeeId) {}

    public record TeacherResponse(
            UUID id,
            UUID schoolId,
            UUID userId,
            String firstName,
            String lastName,
            String employeeId,
            Instant createdAt,
            Instant updatedAt) {}
}
