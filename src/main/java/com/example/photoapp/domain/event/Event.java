package com.example.photoapp.domain.event;

import com.example.photoapp.common.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Photo grouping. Each school has at least one event flagged
 * {@code is_default = true} which acts as the sentinel for uncategorised
 * uploads (see ADR 0005). The V1 partial unique index
 * {@code uq_event_default_per_school} enforces the "exactly one default per
 * school" invariant at the DB layer.
 */
@Entity
@Table(name = "event")
public class Event extends Auditable {

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected Event() {
        // JPA
    }

    public Event(UUID schoolId, String name, UUID createdBy) {
        this.schoolId = schoolId;
        this.name = name;
        this.createdBy = createdBy;
    }

    public UUID getSchoolId()       { return schoolId; }
    public void setSchoolId(UUID schoolId) { this.schoolId = schoolId; }

    public String getName()         { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription()  { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public boolean isDefault()      { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public UUID getCreatedBy()      { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
