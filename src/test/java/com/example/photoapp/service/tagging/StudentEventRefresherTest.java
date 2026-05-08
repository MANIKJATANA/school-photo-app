package com.example.photoapp.service.tagging;

import com.example.photoapp.domain.tagging.StudentEvent;
import com.example.photoapp.repository.tagging.StudentEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentEventRefresherTest {

    private static final UUID STUDENT = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID EVENT   = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-06-01T12:00:00Z"), ZoneOffset.UTC);

    private StudentEventRepository repo;
    private StudentEventRefresher refresher;

    @BeforeEach
    void setUp() {
        repo = mock(StudentEventRepository.class);
        refresher = new StudentEventRefresher(repo, CLOCK);
    }

    @Test
    void positive_delta_on_missing_row_inserts_with_count_equal_to_delta() {
        when(repo.findByIdStudentIdAndIdEventId(STUDENT, EVENT)).thenReturn(Optional.empty());

        refresher.apply(STUDENT, EVENT, 3);

        ArgumentCaptor<StudentEvent> captor = ArgumentCaptor.forClass(StudentEvent.class);
        verify(repo).save(captor.capture());
        StudentEvent saved = captor.getValue();
        assertThat(saved.getStudentId()).isEqualTo(STUDENT);
        assertThat(saved.getEventId()).isEqualTo(EVENT);
        assertThat(saved.getPhotoCount()).isEqualTo(3);
        assertThat(saved.getFirstSeenAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void positive_delta_on_existing_row_updates_count() {
        StudentEvent existing = new StudentEvent(STUDENT, EVENT, 2, Instant.parse("2030-05-01T00:00:00Z"));
        when(repo.findByIdStudentIdAndIdEventId(STUDENT, EVENT)).thenReturn(Optional.of(existing));

        refresher.apply(STUDENT, EVENT, 1);

        assertThat(existing.getPhotoCount()).isEqualTo(3);
        assertThat(existing.getLastUpdatedAt()).isEqualTo(CLOCK.instant());
        verify(repo).save(existing);
    }

    @Test
    void negative_delta_on_existing_row_decrements() {
        StudentEvent existing = new StudentEvent(STUDENT, EVENT, 2, Instant.parse("2030-05-01T00:00:00Z"));
        when(repo.findByIdStudentIdAndIdEventId(STUDENT, EVENT)).thenReturn(Optional.of(existing));

        refresher.apply(STUDENT, EVENT, -1);

        assertThat(existing.getPhotoCount()).isEqualTo(1);
    }

    @Test
    void negative_delta_below_zero_clamps_to_zero() {
        StudentEvent existing = new StudentEvent(STUDENT, EVENT, 1, Instant.parse("2030-05-01T00:00:00Z"));
        when(repo.findByIdStudentIdAndIdEventId(STUDENT, EVENT)).thenReturn(Optional.of(existing));

        refresher.apply(STUDENT, EVENT, -5);

        assertThat(existing.getPhotoCount()).isEqualTo(0);
    }

    @Test
    void negative_delta_on_missing_row_is_noop() {
        when(repo.findByIdStudentIdAndIdEventId(STUDENT, EVENT)).thenReturn(Optional.empty());

        refresher.apply(STUDENT, EVENT, -1);

        verify(repo, never()).save(any());
    }

    @Test
    void zero_delta_is_noop() {
        refresher.apply(STUDENT, EVENT, 0);

        verify(repo, never()).findByIdStudentIdAndIdEventId(any(), any());
        verify(repo, never()).save(any());
    }

    @Test
    void apply_method_carries_propagation_MANDATORY() throws NoSuchMethodException {
        // Regression guard: a future refactor that downgrades to REQUIRED would silently
        // start a new tx and break the same-tx invariant. Lock the propagation level
        // at the type-system level via reflection.
        Method m = StudentEventRefresher.class.getDeclaredMethod("apply", UUID.class, UUID.class, int.class);
        Transactional ann = m.getAnnotation(Transactional.class);
        assertThat(ann).isNotNull();
        assertThat(ann.propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
