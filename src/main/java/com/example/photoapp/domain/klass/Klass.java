package com.example.photoapp.domain.klass;

import com.example.photoapp.common.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A class within a school for a given academic year. Named {@code Klass} (and
 * the table is {@code klass}) to dodge the Java {@code class} keyword while
 * keeping the column / API names readable.
 */
@Entity
@Table(name = "klass")
public class Klass extends Auditable {

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    protected Klass() {
        // JPA
    }

    public Klass(UUID schoolId, String name, String academicYear) {
        this.schoolId = schoolId;
        this.name = name;
        this.academicYear = academicYear;
    }

    public UUID getSchoolId()              { return schoolId; }
    public void setSchoolId(UUID schoolId) { this.schoolId = schoolId; }

    public String getName()                { return name; }
    public void setName(String name)       { this.name = name; }

    public String getAcademicYear()                    { return academicYear; }
    public void setAcademicYear(String academicYear)   { this.academicYear = academicYear; }
}
