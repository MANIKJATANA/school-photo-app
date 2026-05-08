package com.example.photoapp.repository.photo;

import com.example.photoapp.domain.photo.Photo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Partition-aware / index-tuned read paths that don't fit the
 * {@code JpaSpecificationExecutor} model. Per ADR 0001 the PG-specific
 * implementation lives behind this interface; alt-DB ports re-implement.
 */
public interface PhotoQueryRepository {

    /**
     * The keystone query: photos in event E where student S appears, ordered by
     * created_at DESC, id DESC. Only UPLOADED, non-deleted photos; rejected
     * tags ({@code is_confirmed = FALSE}) are excluded.
     *
     * @param cursorCreatedAt   if non-null, returns rows strictly older than this
     * @param cursorId          tie-breaker; with cursorCreatedAt forms the cursor
     * @param limit             cap; caller passes limit+1 to detect "has more"
     */
    List<Photo> findPhotosForStudentInEvent(
            UUID studentId,
            UUID eventId,
            Instant cursorCreatedAt,
            UUID cursorId,
            int limit);
}
