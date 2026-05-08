package com.example.photoapp.domain.tagging;

import com.example.photoapp.common.time.Clocks;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * "Student S appears in photo P." Output of either ML face-matching or
 * manual tagging. {@code event_id} is denormalised from {@code photo} so the
 * keystone read query
 * {@code WHERE student_id=? AND event_id=?} prunes by index and partition
 * without an extra join.
 *
 * <p>Does NOT extend {@link com.example.photoapp.common.audit.Auditable}:
 * V1 has only {@code created_at}, no {@code updated_at} / {@code deleted_at}.
 * Untag is a hard delete (the row's existence IS the tag).
 *
 * <p>{@code is_confirmed} is nullable in V1: NULL = auto (ML wrote it),
 * TRUE = a human confirmed, FALSE = a human rejected (we keep the row for
 * provenance, but reads filter rejected out).
 */
@Entity
@Table(name = "photo_student")
public class PhotoStudent {

    @EmbeddedId
    private PhotoStudentId id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "confidence", nullable = false)
    private float confidence;

    /** {@code bbox} stored as JSONB; service code treats it as opaque (ADR 0001). */
    @Column(name = "bbox", columnDefinition = "jsonb")
    private String bboxJson;

    @Column(name = "ml_run_id", nullable = false)
    private UUID mlRunId;

    @Column(name = "is_confirmed")
    private Boolean isConfirmed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PhotoStudent() {
        // JPA
    }

    public PhotoStudent(UUID photoId, UUID studentId, UUID eventId,
                         float confidence, UUID mlRunId) {
        this.id = new PhotoStudentId(photoId, studentId);
        this.eventId = eventId;
        this.confidence = confidence;
        this.mlRunId = mlRunId;
    }

    @PrePersist
    void onPersist() {
        if (createdAt == null) createdAt = Clocks.now();
    }

    public PhotoStudentId getId()  { return id; }
    public UUID getPhotoId()       { return id.photoId(); }
    public UUID getStudentId()     { return id.studentId(); }
    public UUID getEventId()       { return eventId; }

    public float getConfidence()                  { return confidence; }
    public void setConfidence(float confidence)   { this.confidence = confidence; }

    public String getBboxJson()                   { return bboxJson; }
    public void setBboxJson(String bboxJson)      { this.bboxJson = bboxJson; }

    public UUID getMlRunId()                      { return mlRunId; }
    public void setMlRunId(UUID mlRunId)          { this.mlRunId = mlRunId; }

    public Boolean getIsConfirmed()               { return isConfirmed; }
    public void setIsConfirmed(Boolean confirmed) { this.isConfirmed = confirmed; }

    public Instant getCreatedAt()                 { return createdAt; }
}
