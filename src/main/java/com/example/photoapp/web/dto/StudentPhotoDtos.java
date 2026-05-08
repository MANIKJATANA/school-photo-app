package com.example.photoapp.web.dto;

import java.time.Instant;
import java.util.UUID;

public final class StudentPhotoDtos {

    private StudentPhotoDtos() {}

    /** One row from the {@code student_event} precompute (ADR 0004). */
    public record StudentEventResponse(
            UUID studentId,
            UUID eventId,
            int photoCount,
            Instant firstSeenAt,
            Instant lastUpdatedAt) {}
}
