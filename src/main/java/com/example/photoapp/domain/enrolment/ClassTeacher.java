package com.example.photoapp.domain.enrolment;

import com.example.photoapp.common.time.Clocks;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * M:N join between {@code klass} and {@code teacher} carrying a
 * class-scoped {@link TeachingRole}. Composite PK {@code (class_id,
 * teacher_id)} enforces "at most one assignment per (class, teacher)" — re-
 * assigning the same teacher with a different role is an UPDATE, not an
 * INSERT. No temporal model and no soft-delete: removing an assignment
 * deletes the row.
 *
 * Does NOT extend {@link com.example.photoapp.common.audit.Auditable}: V1's
 * {@code class_teacher} table has only {class_id, teacher_id, role,
 * created_at}.
 */
@Entity
@Table(name = "class_teacher")
public class ClassTeacher {

    @EmbeddedId
    private ClassTeacherId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private TeachingRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClassTeacher() {
        // JPA
    }

    public ClassTeacher(UUID classId, UUID teacherId, TeachingRole role) {
        this.id = new ClassTeacherId(classId, teacherId);
        this.role = role;
    }

    @PrePersist
    void onPersist() {
        if (createdAt == null) createdAt = Clocks.now();
    }

    public ClassTeacherId getId() { return id; }
    public UUID getClassId()      { return id.classId(); }
    public UUID getTeacherId()    { return id.teacherId(); }

    public TeachingRole getRole()                  { return role; }
    public void setRole(TeachingRole role)         { this.role = role; }

    public Instant getCreatedAt() { return createdAt; }
}
