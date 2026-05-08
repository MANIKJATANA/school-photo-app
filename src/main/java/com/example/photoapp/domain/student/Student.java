package com.example.photoapp.domain.student;

import com.example.photoapp.common.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A student in a school. {@code userId} points at the {@code app_user} the
 * student (or, per ADR 0006, the parent) logs in with. ML face-matching
 * status is tracked here so an admin can see at a glance whether reference
 * photos have been enrolled.
 */
@Entity
@Table(name = "student")
public class Student extends Auditable {

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "roll_number")
    private String rollNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "face_embedding_status", nullable = false)
    private FaceEmbeddingStatus faceEmbeddingStatus = FaceEmbeddingStatus.PENDING; // matches V1 default

    protected Student() {
        // JPA
    }

    public Student(UUID schoolId, String firstName, String lastName) {
        this.schoolId = schoolId;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public UUID getSchoolId()                          { return schoolId; }
    public void setSchoolId(UUID schoolId)             { this.schoolId = schoolId; }

    public UUID getUserId()                            { return userId; }
    public void setUserId(UUID userId)                 { this.userId = userId; }

    public String getFirstName()                       { return firstName; }
    public void setFirstName(String firstName)         { this.firstName = firstName; }

    public String getLastName()                        { return lastName; }
    public void setLastName(String lastName)           { this.lastName = lastName; }

    public String getRollNumber()                      { return rollNumber; }
    public void setRollNumber(String rollNumber)       { this.rollNumber = rollNumber; }

    public LocalDate getDateOfBirth()                  { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth)  { this.dateOfBirth = dateOfBirth; }

    public FaceEmbeddingStatus getFaceEmbeddingStatus()                     { return faceEmbeddingStatus; }
    public void setFaceEmbeddingStatus(FaceEmbeddingStatus faceEmbeddingStatus) { this.faceEmbeddingStatus = faceEmbeddingStatus; }
}
