package com.example.photoapp.domain.tagging;

import com.example.photoapp.common.time.Clocks;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Precompute: "student S has photos in event E with count N." Maintained in
 * the same transaction as {@link PhotoStudent} writes (ADR 0004) so the
 * home-screen query is one indexed lookup instead of a {@code DISTINCT
 * event_id} aggregate over photo_student.
 *
 * <p>Does NOT extend {@link com.example.photoapp.common.audit.Auditable}: V1
 * has {@code first_seen_at}, {@code photo_count}, {@code last_updated_at};
 * no {@code created_at}/{@code deleted_at} of its own.
 */
@Entity
@Table(name = "student_event")
public class StudentEvent {

    @EmbeddedId
    private StudentEventId id;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "photo_count", nullable = false)
    private int photoCount;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    protected StudentEvent() {
        // JPA
    }

    public StudentEvent(UUID studentId, UUID eventId, int photoCount, Instant when) {
        this.id = new StudentEventId(studentId, eventId);
        this.photoCount = photoCount;
        this.firstSeenAt = when;
        this.lastUpdatedAt = when;
    }

    @PrePersist
    void onPersist() {
        if (firstSeenAt == null)   firstSeenAt = Clocks.now();
        if (lastUpdatedAt == null) lastUpdatedAt = firstSeenAt;
    }

    @PreUpdate
    void onUpdate() {
        lastUpdatedAt = Clocks.now();
    }

    public StudentEventId getId()      { return id; }
    public UUID getStudentId()         { return id.studentId(); }
    public UUID getEventId()           { return id.eventId(); }

    public Instant getFirstSeenAt()    { return firstSeenAt; }
    public Instant getLastUpdatedAt()  { return lastUpdatedAt; }

    public int getPhotoCount()                 { return photoCount; }
    public void setPhotoCount(int photoCount)  { this.photoCount = photoCount; }

    public void touch(Instant when) { this.lastUpdatedAt = when; }
}
