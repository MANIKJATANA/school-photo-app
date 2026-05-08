package com.example.photoapp.service.student;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.pagination.Base64CursorCodec;
import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.common.pagination.CursorPaginator;
import com.example.photoapp.domain.student.FaceEmbeddingStatus;
import com.example.photoapp.domain.student.Student;
import com.example.photoapp.domain.user.AppUser;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.repository.student.StudentRepository;
import com.example.photoapp.service.provisioning.UserProvisioning;
import com.example.photoapp.web.dto.StudentDtos.CreateStudentRequest;
import com.example.photoapp.web.dto.StudentDtos.StudentResponse;
import com.example.photoapp.web.dto.StudentDtos.UpdateStudentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentServiceTest {

    private static final UUID SCHOOL_A = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID SCHOOL_B = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final UUID NEW_USER_ID = UUID.fromString("01900000-0000-7000-8000-000000000004");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-06-01T00:00:00Z"), ZoneOffset.UTC);

    private StudentRepository repo;
    private UserProvisioning provisioning;
    private CursorPaginator paginator;
    private StudentService service;

    @BeforeEach
    void setUp() {
        repo = mock(StudentRepository.class);
        provisioning = mock(UserProvisioning.class);
        paginator = new CursorPaginator(new Base64CursorCodec());

        AppUser provisioned = new AppUser(SCHOOL_A, "x@y.test", "h", Role.STUDENT);
        setId(provisioned, NEW_USER_ID);
        when(provisioning.provision(any(), any(), any(), any(), any())).thenReturn(provisioned);

        // Default save / saveAndFlush behaviour: assign an id like JPA's @PrePersist would.
        when(repo.save(any())).thenAnswer(inv -> {
            Student s = inv.getArgument(0);
            if (s.getId() == null) setId(s, UUID.randomUUID());
            return s;
        });
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            Student s = inv.getArgument(0);
            if (s.getId() == null) setId(s, UUID.randomUUID());
            return s;
        });

        service = new StudentService(repo, provisioning, paginator, CLOCK);
    }

    @Test
    void create_provisions_user_and_persists_student_linked_to_user_id() {
        StudentResponse resp = service.create(SCHOOL_A, ADMIN_ID, validRequest());

        assertThat(resp.id()).isNotNull();
        assertThat(resp.userId()).isEqualTo(NEW_USER_ID);
        assertThat(resp.firstName()).isEqualTo("Alice");
        assertThat(resp.faceEmbeddingStatus()).isEqualTo(FaceEmbeddingStatus.PENDING);

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(repo).saveAndFlush(captor.capture());
        Student saved = captor.getValue();
        assertThat(saved.getSchoolId()).isEqualTo(SCHOOL_A);
        assertThat(saved.getUserId()).isEqualTo(NEW_USER_ID);
        assertThat(saved.getRollNumber()).isEqualTo("R-001");
    }

    @Test
    void create_translates_duplicate_roll_number_to_conflict() {
        doThrow(new DataIntegrityViolationException("uq_student_school_roll_number"))
                .when(repo).saveAndFlush(any());

        assertThatThrownBy(() -> service.create(SCHOOL_A, ADMIN_ID, validRequest()))
                .isInstanceOf(Errors.Conflict.class)
                .hasMessageContaining("roll_number");
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_applies_school_scope_and_returns_first_page() {
        Student s1 = student(SCHOOL_A, "Alice", "Liddell", Instant.parse("2030-05-01T00:00:00Z"));
        Student s2 = student(SCHOOL_A, "Bob", "Builder", Instant.parse("2030-04-01T00:00:00Z"));
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(s1, s2)));

        CursorPage<StudentResponse> page = service.list(SCHOOL_A, null, 50);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNull();
        assertThat(page.limit()).isEqualTo(50);
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_returns_next_cursor_when_more_rows_exist() {
        Student s1 = student(SCHOOL_A, "A", "A", Instant.parse("2030-05-03T00:00:00Z"));
        Student s2 = student(SCHOOL_A, "B", "B", Instant.parse("2030-05-02T00:00:00Z"));
        Student s3 = student(SCHOOL_A, "C", "C", Instant.parse("2030-05-01T00:00:00Z"));
        // Service requested limit+1 (3) — repo returns 3 to signal "there's more".
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(s1, s2, s3)));

        CursorPage<StudentResponse> page = service.list(SCHOOL_A, null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNotBlank();
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_clamps_excessive_limit_to_max() {
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        CursorPage<StudentResponse> page = service.list(SCHOOL_A, null, 99999);

        assertThat(page.limit()).isEqualTo(CursorPaginator.MAX_LIMIT);
    }

    @Test
    void list_rejects_zero_or_negative_limit() {
        assertThatThrownBy(() -> service.list(SCHOOL_A, null, 0))
                .isInstanceOf(Errors.BadRequest.class);
    }

    @Test
    void get_returns_student_when_in_school() {
        UUID id = UUID.randomUUID();
        Student s = student(SCHOOL_A, "F", "L", Instant.now());
        setId(s, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(s));

        assertThat(service.get(SCHOOL_A, id).id()).isEqualTo(id);
    }

    @Test
    void get_throws_NotFound_when_student_belongs_to_different_school() {
        UUID id = UUID.randomUUID();
        Student s = student(SCHOOL_B, "F", "L", Instant.now());
        setId(s, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.get(SCHOOL_A, id))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void get_throws_NotFound_when_missing() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(SCHOOL_A, id))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void update_only_mutates_non_null_fields() {
        UUID id = UUID.randomUUID();
        Student s = student(SCHOOL_A, "Old", "Name", Instant.now());
        s.setRollNumber("ROLL-1");
        s.setDateOfBirth(LocalDate.parse("2010-01-01"));
        setId(s, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(s));

        UpdateStudentRequest update = new UpdateStudentRequest("New", null, null, null);
        StudentResponse resp = service.update(SCHOOL_A, id, update, ADMIN_ID);

        assertThat(resp.firstName()).isEqualTo("New");
        assertThat(resp.lastName()).isEqualTo("Name");                  // unchanged
        assertThat(resp.rollNumber()).isEqualTo("ROLL-1");              // unchanged
        assertThat(resp.dateOfBirth()).isEqualTo(LocalDate.parse("2010-01-01")); // unchanged
    }

    @Test
    void update_translates_duplicate_roll_number_to_conflict() {
        UUID id = UUID.randomUUID();
        Student s = student(SCHOOL_A, "F", "L", Instant.now());
        setId(s, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(s));
        doThrow(new DataIntegrityViolationException("uq_student_school_roll_number"))
                .when(repo).saveAndFlush(any());

        assertThatThrownBy(() -> service.update(SCHOOL_A, id,
                new UpdateStudentRequest(null, null, "DUP-ROLL", null), ADMIN_ID))
                .isInstanceOf(Errors.Conflict.class)
                .hasMessageContaining("roll_number");
    }

    @Test
    void softDelete_sets_deletedAt_and_does_not_touch_app_user() {
        UUID id = UUID.randomUUID();
        Student s = student(SCHOOL_A, "F", "L", Instant.now());
        setId(s, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(s));

        service.softDelete(SCHOOL_A, id, ADMIN_ID);

        assertThat(s.getDeletedAt()).isEqualTo(CLOCK.instant());
        verify(provisioning, never()).provision(any(), any(), any(), any(), any());
    }

    private static Student student(UUID schoolId, String first, String last, Instant createdAt) {
        Student s = new Student(schoolId, first, last);
        setId(s, UUID.randomUUID());
        try {
            Field f = s.getClass().getSuperclass().getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(s, createdAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return s;
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field f = entity.getClass().getSuperclass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static CreateStudentRequest validRequest() {
        return new CreateStudentRequest("Alice", "Liddell", "alice@example.com",
                "passw0rd-strong", "R-001", LocalDate.parse("2010-01-15"), "555-0100");
    }
}
