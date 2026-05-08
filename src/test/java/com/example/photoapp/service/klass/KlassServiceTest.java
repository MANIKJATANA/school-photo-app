package com.example.photoapp.service.klass;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.pagination.Base64CursorCodec;
import com.example.photoapp.common.pagination.CursorCodec;
import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.domain.klass.Klass;
import com.example.photoapp.repository.klass.KlassRepository;
import com.example.photoapp.web.dto.KlassDtos.ClassResponse;
import com.example.photoapp.web.dto.KlassDtos.CreateClassRequest;
import com.example.photoapp.web.dto.KlassDtos.UpdateClassRequest;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KlassServiceTest {

    private static final UUID SCHOOL_A = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID SCHOOL_B = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-06-01T00:00:00Z"), ZoneOffset.UTC);

    private KlassRepository repo;
    private CursorCodec cursorCodec;
    private KlassService service;

    @BeforeEach
    void setUp() {
        repo = mock(KlassRepository.class);
        cursorCodec = new Base64CursorCodec();

        when(repo.save(any())).thenAnswer(inv -> {
            Klass k = inv.getArgument(0);
            if (k.getId() == null) setId(k, UUID.randomUUID());
            return k;
        });
        when(repo.saveAndFlush(any())).thenAnswer(inv -> {
            Klass k = inv.getArgument(0);
            if (k.getId() == null) setId(k, UUID.randomUUID());
            return k;
        });

        service = new KlassService(repo, cursorCodec, CLOCK);
    }

    @Test
    void create_persists_and_returns_response() {
        ClassResponse resp = service.create(SCHOOL_A, ADMIN_ID, validRequest());

        assertThat(resp.id()).isNotNull();
        assertThat(resp.schoolId()).isEqualTo(SCHOOL_A);
        assertThat(resp.name()).isEqualTo("Grade 5 - A");
        assertThat(resp.academicYear()).isEqualTo("2025-2026");

        ArgumentCaptor<Klass> captor = ArgumentCaptor.forClass(Klass.class);
        verify(repo).saveAndFlush(captor.capture());
        Klass saved = captor.getValue();
        assertThat(saved.getSchoolId()).isEqualTo(SCHOOL_A);
        assertThat(saved.getName()).isEqualTo("Grade 5 - A");
    }

    @Test
    void create_translates_duplicate_name_year_to_conflict() {
        doThrow(new DataIntegrityViolationException("uq_klass_school_name_year"))
                .when(repo).saveAndFlush(any());

        assertThatThrownBy(() -> service.create(SCHOOL_A, ADMIN_ID, validRequest()))
                .isInstanceOf(Errors.Conflict.class)
                .hasMessageContaining("name and year");
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_applies_school_scope_and_returns_first_page() {
        Klass k1 = klass(SCHOOL_A, "Grade 5 - A", "2025-2026", Instant.parse("2030-05-01T00:00:00Z"));
        Klass k2 = klass(SCHOOL_A, "Grade 5 - B", "2025-2026", Instant.parse("2030-04-01T00:00:00Z"));
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(k1, k2)));

        CursorPage<ClassResponse> page = service.list(SCHOOL_A, null, 50);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_returns_next_cursor_when_more_rows_exist() {
        Klass k1 = klass(SCHOOL_A, "A", "2025", Instant.parse("2030-05-03T00:00:00Z"));
        Klass k2 = klass(SCHOOL_A, "B", "2025", Instant.parse("2030-05-02T00:00:00Z"));
        Klass k3 = klass(SCHOOL_A, "C", "2025", Instant.parse("2030-05-01T00:00:00Z"));
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(k1, k2, k3)));

        CursorPage<ClassResponse> page = service.list(SCHOOL_A, null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNotBlank();
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_clamps_excessive_limit_to_max() {
        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        CursorPage<ClassResponse> page = service.list(SCHOOL_A, null, 99999);

        assertThat(page.limit()).isEqualTo(KlassService.MAX_LIMIT);
    }

    @Test
    void list_rejects_zero_or_negative_limit() {
        assertThatThrownBy(() -> service.list(SCHOOL_A, null, 0))
                .isInstanceOf(Errors.BadRequest.class);
    }

    @Test
    void get_returns_class_when_in_school() {
        UUID id = UUID.randomUUID();
        Klass k = klass(SCHOOL_A, "Grade 5", "2025-2026", Instant.now());
        setId(k, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(k));

        assertThat(service.get(SCHOOL_A, id).id()).isEqualTo(id);
    }

    @Test
    void get_throws_NotFound_when_class_belongs_to_different_school() {
        UUID id = UUID.randomUUID();
        Klass k = klass(SCHOOL_B, "Grade 5", "2025-2026", Instant.now());
        setId(k, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(k));

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
        Klass k = klass(SCHOOL_A, "Grade 5 - A", "2024-2025", Instant.now());
        setId(k, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(k));

        UpdateClassRequest update = new UpdateClassRequest("Grade 5 - A (renamed)", null);
        ClassResponse resp = service.update(SCHOOL_A, id, update, ADMIN_ID);

        assertThat(resp.name()).isEqualTo("Grade 5 - A (renamed)");
        assertThat(resp.academicYear()).isEqualTo("2024-2025"); // unchanged
    }

    @Test
    void update_translates_duplicate_to_conflict() {
        UUID id = UUID.randomUUID();
        Klass k = klass(SCHOOL_A, "Original", "2025-2026", Instant.now());
        setId(k, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(k));
        doThrow(new DataIntegrityViolationException("uq_klass_school_name_year"))
                .when(repo).saveAndFlush(any());

        assertThatThrownBy(() -> service.update(SCHOOL_A, id,
                new UpdateClassRequest("Already-Taken", null), ADMIN_ID))
                .isInstanceOf(Errors.Conflict.class);
    }

    @Test
    void softDelete_sets_deletedAt() {
        UUID id = UUID.randomUUID();
        Klass k = klass(SCHOOL_A, "X", "2025", Instant.now());
        setId(k, id);
        when(repo.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(k));

        service.softDelete(SCHOOL_A, id, ADMIN_ID);

        assertThat(k.getDeletedAt()).isEqualTo(CLOCK.instant());
    }

    private static Klass klass(UUID schoolId, String name, String year, Instant createdAt) {
        Klass k = new Klass(schoolId, name, year);
        setId(k, UUID.randomUUID());
        try {
            Field f = k.getClass().getSuperclass().getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(k, createdAt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return k;
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

    private static CreateClassRequest validRequest() {
        return new CreateClassRequest("Grade 5 - A", "2025-2026");
    }
}
