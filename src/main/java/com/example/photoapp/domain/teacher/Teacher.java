package com.example.photoapp.domain.teacher;

import com.example.photoapp.common.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "teacher")
public class Teacher extends Auditable {

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "employee_id")
    private String employeeId;

    protected Teacher() {
        // JPA
    }

    public Teacher(UUID schoolId, UUID userId, String firstName, String lastName) {
        this.schoolId = schoolId;
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public UUID getSchoolId()                  { return schoolId; }
    public void setSchoolId(UUID schoolId)     { this.schoolId = schoolId; }

    public UUID getUserId()                    { return userId; }
    public void setUserId(UUID userId)         { this.userId = userId; }

    public String getFirstName()               { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName()                { return lastName; }
    public void setLastName(String lastName)   { this.lastName = lastName; }

    public String getEmployeeId()              { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
}
