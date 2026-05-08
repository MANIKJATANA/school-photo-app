package com.example.photoapp.service.enrolment;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.domain.enrolment.ClassTeacher;
import com.example.photoapp.domain.enrolment.ClassTeacherId;
import com.example.photoapp.domain.enrolment.TeachingRole;
import com.example.photoapp.domain.klass.Klass;
import com.example.photoapp.domain.teacher.Teacher;
import com.example.photoapp.repository.enrolment.ClassTeacherRepository;
import com.example.photoapp.repository.klass.KlassRepository;
import com.example.photoapp.repository.teacher.TeacherRepository;
import com.example.photoapp.web.dto.ClassTeacherDtos.AssignmentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
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

class ClassTeacherServiceTest {

    private static final UUID SCHOOL_A = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID SCHOOL_B = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final UUID TEACHER  = UUID.fromString("01900000-0000-7000-8000-000000000010");
    private static final UUID CLASS    = UUID.fromString("01900000-0000-7000-8000-000000000020");

    private ClassTeacherRepository repo;
    private TeacherRepository teachers;
    private KlassRepository classes;
    private ClassTeacherService service;

    @BeforeEach
    void setUp() {
        repo = mock(ClassTeacherRepository.class);
        teachers = mock(TeacherRepository.class);
        classes = mock(KlassRepository.class);

        when(teachers.findByIdAndDeletedAtIsNull(TEACHER))
                .thenReturn(Optional.of(teacher(TEACHER, SCHOOL_A)));
        when(classes.findByIdAndDeletedAtIsNull(CLASS))
                .thenReturn(Optional.of(klass(CLASS, SCHOOL_A)));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new ClassTeacherService(repo, teachers, classes);
    }

    @Test
    void assign_first_time_persists_row() {
        when(repo.existsById(new ClassTeacherId(CLASS, TEACHER))).thenReturn(false);

        AssignmentResponse resp = service.assign(SCHOOL_A, CLASS, TEACHER,
                TeachingRole.CLASS_TEACHER, ADMIN_ID);

        assertThat(resp.classId()).isEqualTo(CLASS);
        assertThat(resp.teacherId()).isEqualTo(TEACHER);
        assertThat(resp.role()).isEqualTo(TeachingRole.CLASS_TEACHER);

        ArgumentCaptor<ClassTeacher> captor = ArgumentCaptor.forClass(ClassTeacher.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getClassId()).isEqualTo(CLASS);
        assertThat(captor.getValue().getTeacherId()).isEqualTo(TEACHER);
    }

    @Test
    void assign_when_already_assigned_throws_conflict() {
        when(repo.existsById(new ClassTeacherId(CLASS, TEACHER))).thenReturn(true);

        assertThatThrownBy(() -> service.assign(SCHOOL_A, CLASS, TEACHER,
                TeachingRole.SUBJECT_TEACHER, ADMIN_ID))
                .isInstanceOf(Errors.Conflict.class);
        verify(repo, never()).save(any());
    }

    @Test
    void assign_cross_school_teacher_throws_not_found() {
        when(teachers.findByIdAndDeletedAtIsNull(TEACHER))
                .thenReturn(Optional.of(teacher(TEACHER, SCHOOL_B)));

        assertThatThrownBy(() -> service.assign(SCHOOL_A, CLASS, TEACHER,
                TeachingRole.TEACHER, ADMIN_ID))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void assign_cross_school_class_throws_not_found() {
        when(classes.findByIdAndDeletedAtIsNull(CLASS))
                .thenReturn(Optional.of(klass(CLASS, SCHOOL_B)));

        assertThatThrownBy(() -> service.assign(SCHOOL_A, CLASS, TEACHER,
                TeachingRole.TEACHER, ADMIN_ID))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void update_role_on_existing_assignment_mutates_role() {
        ClassTeacher existing = new ClassTeacher(CLASS, TEACHER, TeachingRole.TEACHER);
        when(repo.findById(new ClassTeacherId(CLASS, TEACHER))).thenReturn(Optional.of(existing));

        AssignmentResponse resp = service.updateRole(SCHOOL_A, CLASS, TEACHER,
                TeachingRole.CLASS_TEACHER, ADMIN_ID);

        assertThat(resp.role()).isEqualTo(TeachingRole.CLASS_TEACHER);
        assertThat(existing.getRole()).isEqualTo(TeachingRole.CLASS_TEACHER);
    }

    @Test
    void update_role_when_no_assignment_throws_not_found() {
        when(repo.findById(new ClassTeacherId(CLASS, TEACHER))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRole(SCHOOL_A, CLASS, TEACHER,
                TeachingRole.SUBJECT_TEACHER, ADMIN_ID))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void remove_existing_assignment_deletes_row() {
        ClassTeacherId pk = new ClassTeacherId(CLASS, TEACHER);
        when(repo.existsById(pk)).thenReturn(true);

        service.remove(SCHOOL_A, CLASS, TEACHER, ADMIN_ID);

        verify(repo).deleteById(pk);
    }

    @Test
    void remove_when_no_assignment_throws_not_found() {
        when(repo.existsById(new ClassTeacherId(CLASS, TEACHER))).thenReturn(false);

        assertThatThrownBy(() -> service.remove(SCHOOL_A, CLASS, TEACHER, ADMIN_ID))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void list_by_class_returns_only_rows_for_class() {
        ClassTeacher a = new ClassTeacher(CLASS, UUID.randomUUID(), TeachingRole.CLASS_TEACHER);
        ClassTeacher b = new ClassTeacher(CLASS, UUID.randomUUID(), TeachingRole.SUBJECT_TEACHER);
        when(repo.findByIdClassIdOrderByCreatedAtAsc(CLASS)).thenReturn(List.of(a, b));

        List<AssignmentResponse> page = service.listByClass(SCHOOL_A, CLASS);

        assertThat(page).hasSize(2);
        assertThat(page).allMatch(r -> r.classId().equals(CLASS));
    }

    private static Teacher teacher(UUID id, UUID schoolId) {
        Teacher t = new Teacher(schoolId, UUID.randomUUID(), "First", "Last");
        setField(t, "id", id);
        return t;
    }

    private static Klass klass(UUID id, UUID schoolId) {
        Klass k = new Klass(schoolId, "Grade 5", "2025-2026");
        setField(k, "id", id);
        return k;
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
