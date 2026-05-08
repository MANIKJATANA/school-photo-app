package com.example.photoapp.service.enrolment;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.domain.enrolment.StudentClass;
import com.example.photoapp.domain.klass.Klass;
import com.example.photoapp.domain.student.Student;
import com.example.photoapp.repository.enrolment.StudentClassRepository;
import com.example.photoapp.repository.klass.KlassRepository;
import com.example.photoapp.repository.student.StudentRepository;
import com.example.photoapp.web.dto.EnrolmentDtos.EnrolmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentEnrolmentServiceTest {

    private static final UUID SCHOOL_A = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID SCHOOL_B = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final UUID STUDENT  = UUID.fromString("01900000-0000-7000-8000-000000000010");
    private static final UUID CLASS_A  = UUID.fromString("01900000-0000-7000-8000-000000000020");
    private static final UUID CLASS_B  = UUID.fromString("01900000-0000-7000-8000-000000000021");
    private static final LocalDate TODAY = LocalDate.parse("2030-06-01");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-06-01T12:00:00Z"), ZoneOffset.UTC);

    private StudentClassRepository enrolments;
    private StudentRepository students;
    private KlassRepository classes;
    private StudentEnrolmentService service;

    @BeforeEach
    void setUp() {
        enrolments = mock(StudentClassRepository.class);
        students = mock(StudentRepository.class);
        classes = mock(KlassRepository.class);

        when(students.findByIdAndDeletedAtIsNull(STUDENT))
                .thenReturn(Optional.of(student(STUDENT, SCHOOL_A)));
        when(classes.findByIdAndDeletedAtIsNull(CLASS_A))
                .thenReturn(Optional.of(klass(CLASS_A, SCHOOL_A)));
        when(classes.findByIdAndDeletedAtIsNull(CLASS_B))
                .thenReturn(Optional.of(klass(CLASS_B, SCHOOL_A)));

        when(enrolments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(enrolments.saveAndFlush(any())).thenAnswer(inv -> {
            StudentClass sc = inv.getArgument(0);
            // Mimic @PrePersist's id assignment.
            if (sc.getId() == null) setField(sc, "id", UUID.randomUUID());
            return sc;
        });

        service = new StudentEnrolmentService(enrolments, students, classes, CLOCK);
    }

    @Test
    void enrol_first_time_creates_active_row() {
        when(enrolments.findByStudentIdAndValidToIsNull(STUDENT)).thenReturn(Optional.empty());

        EnrolmentResponse resp = service.enrol(SCHOOL_A, CLASS_A, STUDENT, ADMIN_ID);

        assertThat(resp.studentId()).isEqualTo(STUDENT);
        assertThat(resp.classId()).isEqualTo(CLASS_A);
        assertThat(resp.validTo()).isNull();
        assertThat(resp.validFrom()).isEqualTo(TODAY);
    }

    @Test
    void enrol_same_class_twice_is_idempotent() {
        StudentClass active = activeRow(STUDENT, CLASS_A);
        when(enrolments.findByStudentIdAndValidToIsNull(STUDENT)).thenReturn(Optional.of(active));

        EnrolmentResponse resp = service.enrol(SCHOOL_A, CLASS_A, STUDENT, ADMIN_ID);

        assertThat(resp.id()).isEqualTo(active.getId());
        // No new row inserted.
        verify(enrolments, never()).saveAndFlush(any());
    }

    @Test
    void enrol_into_different_class_ends_prior_and_creates_new() {
        StudentClass active = activeRow(STUDENT, CLASS_A);
        when(enrolments.findByStudentIdAndValidToIsNull(STUDENT)).thenReturn(Optional.of(active));

        EnrolmentResponse resp = service.enrol(SCHOOL_A, CLASS_B, STUDENT, ADMIN_ID);

        assertThat(active.getValidTo()).isEqualTo(TODAY);          // prior ended
        assertThat(resp.classId()).isEqualTo(CLASS_B);             // new one is current
        assertThat(resp.validTo()).isNull();

        ArgumentCaptor<StudentClass> savedAndFlushed = ArgumentCaptor.forClass(StudentClass.class);
        verify(enrolments).saveAndFlush(savedAndFlushed.capture());
        assertThat(savedAndFlushed.getValue().getClassId()).isEqualTo(CLASS_B);
    }

    @Test
    void enrol_cross_school_student_throws_not_found() {
        when(students.findByIdAndDeletedAtIsNull(STUDENT))
                .thenReturn(Optional.of(student(STUDENT, SCHOOL_B)));

        assertThatThrownBy(() -> service.enrol(SCHOOL_A, CLASS_A, STUDENT, ADMIN_ID))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void enrol_cross_school_class_throws_not_found() {
        when(classes.findByIdAndDeletedAtIsNull(CLASS_A))
                .thenReturn(Optional.of(klass(CLASS_A, SCHOOL_B)));

        assertThatThrownBy(() -> service.enrol(SCHOOL_A, CLASS_A, STUDENT, ADMIN_ID))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void unenrol_active_assignment_sets_valid_to() {
        StudentClass active = activeRow(STUDENT, CLASS_A);
        when(enrolments.findByStudentIdAndValidToIsNull(STUDENT)).thenReturn(Optional.of(active));

        service.unenrol(SCHOOL_A, CLASS_A, STUDENT, ADMIN_ID);

        assertThat(active.getValidTo()).isEqualTo(TODAY);
    }

    @Test
    void unenrol_when_no_active_throws_not_found() {
        when(enrolments.findByStudentIdAndValidToIsNull(STUDENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unenrol(SCHOOL_A, CLASS_A, STUDENT, ADMIN_ID))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void unenrol_active_row_for_different_class_throws_not_found() {
        // Student is active in CLASS_A but caller is unenrolling them from CLASS_B.
        StudentClass active = activeRow(STUDENT, CLASS_A);
        when(enrolments.findByStudentIdAndValidToIsNull(STUDENT)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.unenrol(SCHOOL_A, CLASS_B, STUDENT, ADMIN_ID))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void list_active_by_class_returns_only_active_rows() {
        StudentClass a = activeRow(UUID.randomUUID(), CLASS_A);
        StudentClass b = activeRow(UUID.randomUUID(), CLASS_A);
        when(enrolments.findByClassIdAndValidToIsNull(CLASS_A)).thenReturn(List.of(a, b));

        List<EnrolmentResponse> page = service.listActiveByClass(SCHOOL_A, CLASS_A);

        assertThat(page).hasSize(2);
        assertThat(page).allMatch(r -> r.classId().equals(CLASS_A) && r.validTo() == null);
    }

    // ============ helpers ============

    private static Student student(UUID id, UUID schoolId) {
        Student s = new Student(schoolId, "First", "Last");
        setField(s, "id", id);
        return s;
    }

    private static Klass klass(UUID id, UUID schoolId) {
        Klass k = new Klass(schoolId, "Grade 5", "2025-2026");
        setField(k, "id", id);
        return k;
    }

    private static StudentClass activeRow(UUID studentId, UUID classId) {
        StudentClass sc = new StudentClass(studentId, classId, TODAY);
        setField(sc, "id", UUID.randomUUID());
        return sc;
    }

    private static void setField(Object entity, String name, Object value) {
        try {
            Class<?> c = entity.getClass();
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    f.set(entity, value);
                    return;
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
            throw new IllegalArgumentException("no field " + name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
