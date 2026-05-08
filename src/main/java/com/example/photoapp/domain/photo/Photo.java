package com.example.photoapp.domain.photo;

import com.example.photoapp.common.id.Ids;
import com.example.photoapp.common.time.Clocks;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A photo uploaded against an event. The underlying {@code photo} table is
 * partitioned {@code BY HASH (event_id)} into 16 partitions (V1, ADR 0003);
 * the partition layout is invisible to JPA.
 *
 * <p>Does NOT extend {@link com.example.photoapp.common.audit.Auditable}:
 * Auditable assumes a single-UUID PK, but Photo's PK is the composite
 * {@link PhotoId} {@code (event_id, id)}. Audit timestamps are managed
 * directly via {@link #onPersist}/{@link #onUpdate}.
 */
@Entity
@Table(name = "photo")
public class Photo {

    @EmbeddedId
    private PhotoId id;

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "blob_key", nullable = false)
    private String blobKey;

    @Column(name = "blob_bucket", nullable = false)
    private String blobBucket;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "width_px")
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    @Column(name = "taken_at")
    private Instant takenAt;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false)
    private UploadStatus uploadStatus = UploadStatus.PENDING; // matches V1 default

    @Enumerated(EnumType.STRING)
    @Column(name = "ml_status", nullable = false)
    private MlStatus mlStatus = MlStatus.PENDING; // matches V1 default

    @Column(name = "ml_processed_at")
    private Instant mlProcessedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Photo() {
        // JPA
    }

    public Photo(UUID eventId, UUID schoolId, String blobKey, String blobBucket,
                 String contentType, long sizeBytes, UUID uploadedBy) {
        this.id = new PhotoId(eventId, Ids.newId());
        this.schoolId = schoolId;
        this.blobKey = blobKey;
        this.blobBucket = blobBucket;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
    }

    @PrePersist
    void onPersist() {
        Instant now = Clocks.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Clocks.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    public void softDelete(Instant when) { this.deletedAt = when; }

    public PhotoId getPk()       { return id; }
    public UUID    getId()       { return id.id(); }
    public UUID    getEventId()  { return id.eventId(); }

    public UUID    getSchoolId()                   { return schoolId; }
    public String  getBlobKey()                    { return blobKey; }
    public String  getBlobBucket()                 { return blobBucket; }
    public String  getContentType()                { return contentType; }
    public long    getSizeBytes()                  { return sizeBytes; }

    public Integer getWidthPx()                    { return widthPx; }
    public void    setWidthPx(Integer widthPx)     { this.widthPx = widthPx; }

    public Integer getHeightPx()                   { return heightPx; }
    public void    setHeightPx(Integer heightPx)   { this.heightPx = heightPx; }

    public Instant getTakenAt()                    { return takenAt; }
    public void    setTakenAt(Instant takenAt)     { this.takenAt = takenAt; }

    public UUID    getUploadedBy()                 { return uploadedBy; }

    public UploadStatus getUploadStatus()                      { return uploadStatus; }
    public void         setUploadStatus(UploadStatus s)        { this.uploadStatus = s; }

    public MlStatus getMlStatus()                              { return mlStatus; }
    public void     setMlStatus(MlStatus s)                    { this.mlStatus = s; }

    public Instant  getMlProcessedAt()                         { return mlProcessedAt; }
    public void     setMlProcessedAt(Instant t)                { this.mlProcessedAt = t; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
