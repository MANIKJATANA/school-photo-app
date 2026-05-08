package com.example.photoapp.service.teacher;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.pagination.Base64CursorCodec;
import com.example.photoapp.common.pagination.CursorCodec;
import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.domain.teacher.Teacher;
import com.example.photoapp.domain.user.AppUser;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.repository.teacher.TeacherRepository;
import com.example.photoapp.service.provisioning.UserProvisioning;
import com.example.photoapp.web.dto.TeacherDtos.CreateTeacherRequest;
import com.example.photoapp.web.dto.TeacherDtos.TeacherResponse;
import com.example.photoapp.web.dto.TeacherDtos.UpdateTeacherRequest;
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

class TeacherServiceTest {

    private static final UUID SCHOOL_A = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID SCHOOL_B = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final UUID NEW_USER_ID = UUID.fromString("01900000-0000-7000-8000-000000000004");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-06-01T00:00:00Z"), ZoneOffset.UTC);

    private TeacherRepository repo;
    private UserProvisioning provisioning;
    private CursorCodec cursorCodec;
    private TeacherService service;

    @BeforeEach
    void setUp() {
        repo = mock(TeacherRepository.class);
        provisioning = mock(UserProvisioning.class);
        cursorCodec = new Base64CursorCodec();

        AppUser provisioned = new AppUser(SCHOOL_A, "x@y.test", "h", Role.TEACHER);
        setId(provisioned, NEW_USER_ID);
        when(provisioning.provision(any(), any(), any(), any(), any())).thenReturn(provisioned);

        when(repo.save(any())).thenAnswer(inv -> {
            Teacher t = inv.getArgument(0);
            if (t.getId() == null) setId(t, UUID.randomUUID());
            return t;
        });
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            Teacher t = inv.getArgument(0);
            if (t.getId() == null) setId(t, UUID.randomUUID());
            return t;
        });

        service = new TeacherService(repo, provisioning, cursorCodec, CLOCK);
    }

    @Test
    void create_provisions_user_and_persists_teacher_linked_to_user_id() {
        TeacherResponse resp = service.create(SCHOOL_A, ADMIN_ID, validRequest());

        assertThat(resp.id()).isNotNull();
        assertThat(resp.userId()).isEqualTo(NEW_USER_ID);
        assertThat(resp.firstName()).isEqualTo("Mary");

        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(repo).saveAndFlush(captor.capture());
        Teacher saved = captor.getValue();
        assertThat(saved.getSchoolId()).isEqualTo(SCHOOL_A);
        assertThat(saved.getUserId()).isEqualTo(NEW_USER_ID);
        assertThat(saved.getEmployeeId()).isEqualTo("E-001");
    }

    @Test
    void create_calls_provisioning_with_TEACHER_role() {
        service.create(SCHOOL_A, ADMIN_ID, validRequest());

        verify(provisioning).provision(SCHOOL_A, "mary@example.com", "passw0rd-strong", Role.TEACHER, "555-0100");
    }

    @Test
    void create_translates_duplicate_employee_id_to_conflict() {
        doThrow(new DataIntegrityViolationException("uq_teacher_school_employee_id"))
                .when(repo).saveAndFlush(any());

        assertThatThrownBy(() -> service.create(SCHOOL_A, ADMIN_ID, validRequest()))
                .isInstanceOf(Errors.Conflict.class)
                .hasMessageContaining("employee_id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_applies_school_scope_and_returns_first_page() {
        Teacher t1 = teacher(SCHOOL_A, "Mary", "Poppins", Instant.parse("2030-05-01T00:00:00Z"));
        Teacher t2 = teacher(SCHOOL_A, "John", "Smith", Instant.parse("2030-04-01T00:00:00Z"));
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(t1, t2)));

        CursorPage<TeacherResponse> page = service.list(SCHOOL_A, null, 50);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_returns_next_cursor_when_more_rows_exist() {
        Teacher t1 = teacher(SCHOOL_A, "A", "A", Instant.parse("2030-05-03T00:00:00Z"));
        Teacher t2 = teacher(SCHOOL_A, "B", "B", Instant.parse("2030-05-02T00:00:00Z"));
        Teacher t3 = teacher(SCHOOL_A, "C", "C", Instant.parse("2030-05-01T00:00:00Z"));
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(t1, t2, t3)));

        CursorPage<TeacherResponse> page = service.list(SCHOOL_A, null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNotBlank();
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_clamps_excessive_limit_to_max() {
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        CursorPage<TeacherResponse> page = service.list(SCHOOL_A, null, 99999);

        assertThat(page.limit()).isEqualTo(TeacherService.MAX_LIMIT);
    }

    @Test
    void list_rejects_zero_or_negative_limit() {
        assertThatThrownBy(() -> service.list(SCHOOL_A, null, 0))
                .isInstanceOf(Errors.BadRequest.class);
    }

    @Test
    void get_returns_teacher_when_in_school() {
        UUID id = UUID.randomUUID();
        Teacher t = teacher(SCHOOL_A, "F", "L", Instant.now());
        setId(t, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(t));

        assertThat(service.get(SCHOOL_A, id).id()).isEqualTo(id);
    }

    @Test
    void get_throws_NotFound_when_teacher_belongs_to_different_school() {
        UUID id = UUID.randomUUID();
        Teacher t = teacher(SCHOOL_B, "F", "L", Instant.now());
        setId(t, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(t));

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
        Teacher t = teacher(SCHOOL_A, "Old", "Name", Instant.now());
        t.setEmployeeId("E-INIT");
        setId(t, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(t));

        UpdateTeacherRequest update = new UpdateTeacherRequest("New", null, null);
        TeacherResponse resp = service.update(SCHOOL_A, id, update, ADMIN_ID);

        assertThat(resp.firstName()).isEqualTo("New");
        assertThat(resp.lastName()).isEqualTo("Name");          // unchanged
        assertThat(resp.employeeId()).isEqualTo("E-INIT");      // unchanged
    }

    @Test
    void update_translates_duplicate_employee_id_to_conflict() {
        UUID id = UUID.randomUUID();
        Teacher t = teacher(SCHOOL_A, "F", "L", Instant.now());
        setId(t, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(t));
        doThrow(new DataIntegrityViolationException("uq_teacher_school_employee_id"))
                .when(repo).saveAndFlush(any());

        assertThatThrownBy(() -> service.update(SCHOOL_A, id,
                new UpdateTeacherRequest(null, null, "E-DUP"), ADMIN_ID))
                .isInstanceOf(Errors.Conflict.class)
                .hasMessageContaining("employee_id");
    }

    @Test
    void softDelete_sets_deletedAt_and_does_not_touch_app_user() {
        UUID id = UUID.randomUUID();
        Teacher t = teacher(SCHOOL_A, "F", "L", Instant.now());
        setId(t, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(t));

        service.softDelete(SCHOOL_A, id, ADMIN_ID);

        assertThat(t.getDeletedAt()).isEqualTo(CLOCK.instant());
        verify(provisioning, never()).provision(any(), any(), any(), any(), any());
    }

    private static Teacher teacher(UUID schoolId, String first, String last, Instant createdAt) {
        Teacher t = new Teacher(schoolId, UUID.randomUUID(), first, last);
        setId(t, UUID.randomUUID());
        try {
            Field f = t.getClass().getSuperclass().getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(t, createdAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return t;
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

    private static CreateTeacherRequest validRequest() {
        return new CreateTeacherRequest("Mary", "Poppins", "mary@example.com",
                "passw0rd-strong", "E-001", "555-0100");
    }
}
