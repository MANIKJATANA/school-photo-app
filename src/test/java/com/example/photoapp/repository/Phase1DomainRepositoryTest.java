package com.example.photoapp.repository;

import com.example.photoapp.TestcontainersConfiguration;
import com.example.photoapp.domain.klass.Klass;
import com.example.photoapp.domain.school.School;
import com.example.photoapp.domain.student.Student;
import com.example.photoapp.domain.teacher.Teacher;
import com.example.photoapp.domain.user.AppUser;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.repository.klass.KlassRepository;
import com.example.photoapp.repository.school.SchoolRepository;
import com.example.photoapp.repository.student.StudentRepository;
import com.example.photoapp.repository.teacher.TeacherRepository;
import com.example.photoapp.repository.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Combined repository slice test for the three Phase 1 / Slice 4a aggregates.
 * One file keeps the school + admin fixture setup DRY.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class Phase1DomainRepositoryTest {

    @Autowired SchoolRepository schools;
    @Autowired AppUserRepository users;
    @Autowired StudentRepository students;
    @Autowired TeacherRepository teachers;
    @Autowired KlassRepository  classes;

    // ============ student ============

    @Test
    void student_persists_and_finds_by_school_and_roll_number() {
        UUID schoolId = newSchool();
        Student s = new Student(schoolId, "Alice", "Liddell");
        s.setRollNumber("R-001");
        students.saveAndFlush(s);

        assertThat(students.findBySchoolIdAndRollNumberAndDeletedAtIsNull(schoolId, "R-001")).isPresent();
    }

    @Test
    void student_duplicate_roll_number_in_same_school_is_rejected() {
        UUID schoolId = newSchool();
        students.saveAndFlush(rollNumberStudent(schoolId, "R-100"));

        assertThatThrownBy(() -> students.saveAndFlush(rollNumberStudent(schoolId, "R-100")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void student_same_roll_number_across_schools_allowed() {
        UUID a = newSchool();
        UUID b = newSchool();
        students.saveAndFlush(rollNumberStudent(a, "R-S"));
        students.saveAndFlush(rollNumberStudent(b, "R-S"));

        assertThat(students.findBySchoolIdAndRollNumberAndDeletedAtIsNull(a, "R-S")).isPresent();
        assertThat(students.findBySchoolIdAndRollNumberAndDeletedAtIsNull(b, "R-S")).isPresent();
    }

    @Test
    void student_soft_deleted_is_excluded_from_default_finders() {
        UUID schoolId = newSchool();
        Student s = rollNumberStudent(schoolId, "R-X");
        students.saveAndFlush(s);
        s.softDelete(Instant.now());
        students.saveAndFlush(s);

        assertThat(students.findBySchoolIdAndRollNumberAndDeletedAtIsNull(schoolId, "R-X")).isEmpty();
        assertThat(students.findByIdAndDeletedAtIsNull(s.getId())).isEmpty();
        assertThat(students.findById(s.getId())).isPresent(); // soft-delete is repo-finder semantics
    }

    // ============ teacher ============

    @Test
    void teacher_persists_and_finds_by_school_and_employee_id() {
        UUID schoolId = newSchool();
        UUID userId = newAppUser(schoolId, Role.TEACHER);
        Teacher t = new Teacher(schoolId, userId, "Mary", "Poppins");
        t.setEmployeeId("E-001");
        teachers.saveAndFlush(t);

        assertThat(teachers.findBySchoolIdAndEmployeeIdAndDeletedAtIsNull(schoolId, "E-001")).isPresent();
    }

    @Test
    void teacher_duplicate_employee_id_in_same_school_is_rejected() {
        UUID schoolId = newSchool();
        teachers.saveAndFlush(employee(schoolId, "E-100"));

        assertThatThrownBy(() -> teachers.saveAndFlush(employee(schoolId, "E-100")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void teacher_same_employee_id_across_schools_allowed() {
        UUID a = newSchool();
        UUID b = newSchool();
        teachers.saveAndFlush(employee(a, "E-S"));
        teachers.saveAndFlush(employee(b, "E-S"));

        assertThat(teachers.findBySchoolIdAndEmployeeIdAndDeletedAtIsNull(a, "E-S")).isPresent();
        assertThat(teachers.findBySchoolIdAndEmployeeIdAndDeletedAtIsNull(b, "E-S")).isPresent();
    }

    @Test
    void teacher_soft_deleted_is_excluded_from_default_finders() {
        UUID schoolId = newSchool();
        Teacher t = employee(schoolId, "E-200");
        teachers.saveAndFlush(t);
        t.softDelete(Instant.now());
        teachers.saveAndFlush(t);

        assertThat(teachers.findBySchoolIdAndEmployeeIdAndDeletedAtIsNull(schoolId, "E-200")).isEmpty();
    }

    // ============ klass ============

    @Test
    void klass_persists_and_finds_by_school_year_name() {
        UUID schoolId = newSchool();
        Klass k = new Klass(schoolId, "Grade 5 - B", "2025-2026");
        classes.saveAndFlush(k);

        assertThat(classes.findBySchoolIdAndNameAndAcademicYearAndDeletedAtIsNull(
                schoolId, "Grade 5 - B", "2025-2026")).isPresent();
    }

    @Test
    void klass_duplicate_in_same_year_is_rejected() {
        UUID schoolId = newSchool();
        classes.saveAndFlush(new Klass(schoolId, "Grade 5 - A", "2025-2026"));

        assertThatThrownBy(() -> classes.saveAndFlush(new Klass(schoolId, "Grade 5 - A", "2025-2026")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void klass_same_name_different_year_allowed() {
        UUID schoolId = newSchool();
        classes.saveAndFlush(new Klass(schoolId, "Grade 5 - A", "2024-2025"));
        classes.saveAndFlush(new Klass(schoolId, "Grade 5 - A", "2025-2026"));

        assertThat(classes.findBySchoolIdAndNameAndAcademicYearAndDeletedAtIsNull(
                schoolId, "Grade 5 - A", "2024-2025")).isPresent();
        assertThat(classes.findBySchoolIdAndNameAndAcademicYearAndDeletedAtIsNull(
                schoolId, "Grade 5 - A", "2025-2026")).isPresent();
    }

    @Test
    void klass_soft_deleted_is_excluded_from_default_finders() {
        UUID schoolId = newSchool();
        Klass k = new Klass(schoolId, "Grade 5 - C", "2025-2026");
        classes.saveAndFlush(k);
        k.softDelete(Instant.now());
        classes.saveAndFlush(k);

        assertThat(classes.findBySchoolIdAndNameAndAcademicYearAndDeletedAtIsNull(
                schoolId, "Grade 5 - C", "2025-2026")).isEmpty();
        assertThat(classes.findByIdAndDeletedAtIsNull(k.getId())).isEmpty();
        assertThat(classes.findById(k.getId())).isPresent(); // soft-delete is repo-finder semantics
    }

    // ============ fixture helpers ============

    private UUID newSchool() {
        School s = new School("Test School " + UUID.randomUUID(), null, null);
        return schools.saveAndFlush(s).getId();
    }

    private UUID newAppUser(UUID schoolId, Role role) {
        AppUser u = new AppUser(schoolId, role.name().toLowerCase() + "-" + UUID.randomUUID() + "@x.test", "h", role);
        return users.saveAndFlush(u).getId();
    }

    private static Student rollNumberStudent(UUID schoolId, String roll) {
        Student s = new Student(schoolId, "First", "Last");
        s.setRollNumber(roll);
        return s;
    }

    private Teacher employee(UUID schoolId, String empId) {
        Teacher t = new Teacher(schoolId, newAppUser(schoolId, Role.TEACHER), "First", "Last");
        t.setEmployeeId(empId);
        return t;
    }
}
