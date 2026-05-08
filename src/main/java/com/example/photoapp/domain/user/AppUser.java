package com.example.photoapp.domain.user;

import com.example.photoapp.common.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.util.Locale;
import java.util.UUID;

/**
 * Login identity. There is no separate PARENT role (ADR 0006) — parents share
 * the student's credentials. Email is stored lowercased so the V1 partial
 * unique index {@code (school_id, lower(email))} does its job.
 */
@Entity
@Table(name = "app_user")
public class AppUser extends Auditable {

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.ACTIVE; // must match V1__baseline.sql app_user.status DEFAULT 'ACTIVE'

    protected AppUser() {
        // JPA
    }

    public AppUser(UUID schoolId, String email, String passwordHash, Role role) {
        this.schoolId = schoolId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    @PrePersist
    @PreUpdate
    void normaliseEmail() {
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT).trim();
        }
    }

    public UUID getSchoolId() { return schoolId; }
    public void setSchoolId(UUID schoolId) { this.schoolId = schoolId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }
}
