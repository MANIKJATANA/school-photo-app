package com.example.photoapp.web.dto;

import com.example.photoapp.domain.student.FaceEmbeddingStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class StudentDtos {

    private StudentDtos() {}

    public record CreateStudentRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(min = 8, max = 200) String password,
            @Size(max = 50) String rollNumber,
            LocalDate dateOfBirth,
            @Size(max = 50) String phone) {}

    /** Update is partial: every field is optional; null means "leave unchanged". */
    public record UpdateStudentRequest(
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 50) String rollNumber,
            LocalDate dateOfBirth) {}

    public record StudentResponse(
            UUID id,
            UUID schoolId,
            UUID userId,
            String firstName,
            String lastName,
            String rollNumber,
            LocalDate dateOfBirth,
            FaceEmbeddingStatus faceEmbeddingStatus,
            Instant createdAt,
            Instant updatedAt) {}
}
