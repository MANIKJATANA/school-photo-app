package com.example.photoapp.domain.enrolment;

import com.example.photoapp.common.id.Ids;
import com.example.photoapp.common.time.Clocks;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Temporal join row between {@code student} and {@code klass}. The V1 partial
 * unique index {@code uq_student_class_active ON (student_id) WHERE valid_to
 * IS NULL} enforces "at most one active class per student"; the service layer
 * upholds the same invariant explicitly so an alt-DB port without partial-
 * unique support stays correct.
 *
 * Does NOT extend {@link com.example.photoapp.common.audit.Auditable}: V1's
 * {@code student_class} table has only {id, student_id, class_id, valid_from,
 * valid_to, created_at} — no updated_at, no deleted_at. The temporal model
 * (valid_to flipping from null to a date) replaces soft-delete.
 */
@Entity
@Table(name = "student_class")
public class StudentClass {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudentClass() {
        // JPA
    }

    public StudentClass(UUID studentId, UUID classId, LocalDate validFrom) {
        this.studentId = studentId;
        this.classId = classId;
        this.validFrom = validFrom;
    }

    @PrePersist
    void onPersist() {
        if (id == null)        id = Ids.newId();
        if (createdAt == null) createdAt = Clocks.now();
        if (validFrom == null) validFrom = LocalDate.ofInstant(Clocks.now(), ZoneOffset.UTC);
    }

    public boolean isActive() { return validTo == null; }

    public void end(LocalDate when) { this.validTo = when; }

    public UUID getId()                { return id; }
    public UUID getStudentId()         { return studentId; }
    public UUID getClassId()           { return classId; }
    public LocalDate getValidFrom()    { return validFrom; }
    public LocalDate getValidTo()      { return validTo; }
    public Instant getCreatedAt()      { return createdAt; }
}
