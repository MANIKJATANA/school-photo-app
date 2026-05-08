package com.example.photoapp.web.dto;

import com.example.photoapp.domain.photo.MlStatus;
import com.example.photoapp.domain.photo.UploadStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public final class PhotoDtos {

    private PhotoDtos() {}

    public record InitiateUploadRequest(
            @NotNull UUID eventId,
            @NotBlank String contentType,
            @Positive long sizeBytes) {}

    public record InitiateUploadResponse(
            UUID photoId,
            UUID eventId,
            URI putUrl,
            Instant expiresAt) {}

    public record PhotoResponse(
            UUID id,
            UUID eventId,
            UUID schoolId,
            String contentType,
            long sizeBytes,
            Integer widthPx,
            Integer heightPx,
            Instant takenAt,
            UploadStatus uploadStatus,
            MlStatus mlStatus,
            Instant createdAt,
            Instant updatedAt) {}

    public record PhotoUrlResponse(URI url, Instant expiresAt) {}

    public record PhotoListItem(
            UUID photoId,
            UUID eventId,
            String contentType,
            long sizeBytes,
            Integer widthPx,
            Integer heightPx,
            Instant takenAt,
            URI getUrl,
            Instant urlExpiresAt,
            Instant createdAt) {}
}
