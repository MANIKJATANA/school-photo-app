package com.example.photoapp.repository.photo;

import com.example.photoapp.domain.photo.Photo;
import com.example.photoapp.domain.photo.PhotoId;
import com.example.photoapp.domain.photo.UploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, PhotoId>, JpaSpecificationExecutor<Photo> {

    /**
     * Lookup by composite PK with soft-delete filter. Partition-pruned (event_id is
     * the partition key) — single-partition hit.
     */
    @Query("""
            SELECT p FROM Photo p
             WHERE p.id.eventId = :eventId
               AND p.id.id = :photoId
               AND p.deletedAt IS NULL
            """)
    Optional<Photo> findActive(@Param("eventId") UUID eventId, @Param("photoId") UUID photoId);

    /**
     * Lookup by photo id alone (used by {@code GET /photos/{id}}). Scans all
     * partitions because the partition key is missing from the predicate;
     * fast in practice (sub-ms per partition × 16) but flag at scale.
     */
    @Query("""
            SELECT p FROM Photo p
             WHERE p.id.id = :photoId
               AND p.deletedAt IS NULL
            """)
    Optional<Photo> findActiveByIdAlone(@Param("photoId") UUID photoId);

    /**
     * Event-scoped listing (used by {@code GET /events/{id}/photos}).
     * Partition-pruned to the single partition matching the event.
     */
    @Query("""
            SELECT p FROM Photo p
             WHERE p.id.eventId = :eventId
               AND p.deletedAt IS NULL
             ORDER BY p.createdAt DESC, p.id.id DESC
            """)
    List<Photo> findActiveByEvent(@Param("eventId") UUID eventId);

    /**
     * For the stale-upload sweeper (Slice 6e): find PENDING uploads older than the
     * given cutoff so they can be marked FAILED or deleted.
     */
    @Query("""
            SELECT p FROM Photo p
             WHERE p.uploadStatus = :status
               AND p.createdAt < :before
               AND p.deletedAt IS NULL
            """)
    List<Photo> findByUploadStatusOlderThan(
            @Param("status") UploadStatus status,
            @Param("before") Instant before);
}
