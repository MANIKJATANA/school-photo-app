package com.example.photoapp.common.audit;

import com.example.photoapp.common.id.Ids;
import com.example.photoapp.common.time.Clocks;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Instant;
import java.util.UUID;

/**
 * Base for every domain entity. Provides:
 * <ul>
 *   <li>UUIDv7 primary key, generated via {@link Ids#newId()} on persist if unset.</li>
 *   <li>{@code created_at} / {@code updated_at} maintained by JPA lifecycle hooks
 *       as defence-in-depth alongside the SQL trigger from V1__baseline.sql.</li>
 *   <li>Soft-delete support via {@code deleted_at}; never hard-delete entities
 *       (per ADR 0008).</li>
 * </ul>
 *
 * Subclasses must remain free of Spring imports per the {@code domain/}
 * package contract.
 */
@MappedSuperclass
public abstract class Auditable {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    protected UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    protected Instant updatedAt;

    @Column(name = "deleted_at")
    protected Instant deletedAt;

    @PrePersist
    void onPersist() {
        if (id == null) {
            id = Ids.newId();
        }
        Instant now = Clocks.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Clocks.now();
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public boolean isDeleted() { return deletedAt != null; }

    public void softDelete(Instant when) {
        this.deletedAt = when;
    }
}
