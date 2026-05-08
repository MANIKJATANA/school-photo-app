package com.example.photoapp.service.photo;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.pagination.Base64CursorCodec;
import com.example.photoapp.common.pagination.CursorCodec;
import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.domain.photo.Photo;
import com.example.photoapp.domain.student.Student;
import com.example.photoapp.domain.tagging.StudentEvent;
import com.example.photoapp.repository.photo.PhotoQueryRepository;
import com.example.photoapp.repository.student.StudentRepository;
import com.example.photoapp.repository.tagging.StudentEventRepository;
import com.example.photoapp.storage.blob.BlobStore;
import com.example.photoapp.storage.s3.S3Properties;
import com.example.photoapp.web.dto.PhotoDtos.PhotoListItem;
import com.example.photoapp.web.dto.StudentPhotoDtos.StudentEventResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentPhotoQueryServiceTest {

    private static final UUID SCHOOL_A = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID SCHOOL_B = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID STUDENT  = UUID.fromString("01900000-0000-7000-8000-000000000010");
    private static final UUID EVENT    = UUID.fromString("01900000-0000-7000-8000-000000000020");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-06-01T00:00:00Z"), ZoneOffset.UTC);

    private final S3Properties s3Props = new S3Properties(
            "test-bucket", "us-east-1", "http://localhost:4566", true,
            "test", "test", Duration.ofMinutes(10), Duration.ofMinutes(5));

    private PhotoQueryRepository photoQueries;
    private StudentEventRepository studentEvents;
    private StudentRepository students;
    private BlobStore blobStore;
    private CursorCodec cursorCodec;
    private StudentPhotoQueryService service;

    @BeforeEach
    void setUp() {
        photoQueries = mock(PhotoQueryRepository.class);
        studentEvents = mock(StudentEventRepository.class);
        students = mock(StudentRepository.class);
        blobStore = mock(BlobStore.class);
        cursorCodec = new Base64CursorCodec();

        when(students.findByIdAndDeletedAtIsNull(STUDENT))
                .thenReturn(Optional.of(studentInSchool(SCHOOL_A, STUDENT)));
        when(blobStore.presignGet(any(), any())).thenReturn(URI.create("https://s3.example/key?sig=abc"));

        service = new StudentPhotoQueryService(photoQueries, studentEvents, students,
                blobStore, cursorCodec, s3Props, CLOCK);
    }

    @Test
    void list_events_returns_precompute_rows() {
        StudentEvent se = new StudentEvent(STUDENT, EVENT, 5, Instant.parse("2030-05-01T00:00:00Z"));
        when(studentEvents.findByIdStudentIdOrderByLastUpdatedAtDesc(STUDENT)).thenReturn(List.of(se));

        List<StudentEventResponse> resp = service.listEventsForStudent(SCHOOL_A, STUDENT);

        assertThat(resp).hasSize(1);
        assertThat(resp.get(0).eventId()).isEqualTo(EVENT);
        assertThat(resp.get(0).photoCount()).isEqualTo(5);
    }

    @Test
    void list_events_cross_school_throws_NotFound() {
        when(students.findByIdAndDeletedAtIsNull(STUDENT))
                .thenReturn(Optional.of(studentInSchool(SCHOOL_B, STUDENT)));

        assertThatThrownBy(() -> service.listEventsForStudent(SCHOOL_A, STUDENT))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void list_photos_calls_keystone_query_and_presigns_each_item() {
        Photo p1 = uploadedPhoto(EVENT, UUID.randomUUID());
        Photo p2 = uploadedPhoto(EVENT, UUID.randomUUID());
        when(photoQueries.findPhotosForStudentInEvent(eq(STUDENT), eq(EVENT), any(), any(), any(Integer.class)))
                .thenReturn(List.of(p1, p2));

        CursorPage<PhotoListItem> page = service.listPhotosForStudentInEvent(
                SCHOOL_A, STUDENT, EVENT, null, 50);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items()).allMatch(i -> i.getUrl().toString().startsWith("https://"));
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void list_photos_emits_cursor_when_more_rows() {
        Photo p1 = uploadedPhoto(EVENT, UUID.randomUUID());
        Photo p2 = uploadedPhoto(EVENT, UUID.randomUUID());
        Photo p3 = uploadedPhoto(EVENT, UUID.randomUUID());
        when(photoQueries.findPhotosForStudentInEvent(eq(STUDENT), eq(EVENT), any(), any(), any(Integer.class)))
                .thenReturn(List.of(p1, p2, p3));

        CursorPage<PhotoListItem> page = service.listPhotosForStudentInEvent(
                SCHOOL_A, STUDENT, EVENT, null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNotBlank();
    }

    @Test
    void list_photos_cross_school_throws_NotFound() {
        when(students.findByIdAndDeletedAtIsNull(STUDENT))
                .thenReturn(Optional.of(studentInSchool(SCHOOL_B, STUDENT)));

        assertThatThrownBy(() -> service.listPhotosForStudentInEvent(
                SCHOOL_A, STUDENT, EVENT, null, 50))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void list_photos_rejects_negative_limit() {
        assertThatThrownBy(() -> service.listPhotosForStudentInEvent(
                SCHOOL_A, STUDENT, EVENT, null, 0))
                .isInstanceOf(Errors.BadRequest.class);
    }

    @Test
    void list_photos_clamps_excessive_limit() {
        when(photoQueries.findPhotosForStudentInEvent(any(), any(), any(), any(), any(Integer.class)))
                .thenReturn(List.of());

        CursorPage<PhotoListItem> page = service.listPhotosForStudentInEvent(
                SCHOOL_A, STUDENT, EVENT, null, 99999);

        assertThat(page.limit()).isEqualTo(StudentPhotoQueryService.MAX_LIMIT);
    }

    // ============ helpers ============

    private static Student studentInSchool(UUID schoolId, UUID id) {
        Student s = new Student(schoolId, "F", "L");
        try {
            Field f = s.getClass().getSuperclass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(s, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return s;
    }

    private static Photo uploadedPhoto(UUID eventId, UUID photoId) {
        Photo p = new Photo(eventId, UUID.randomUUID(), "blob/" + photoId + ".jpg",
                "test-bucket", "image/jpeg", 1024, UUID.randomUUID());
        p.assignIdForUpload(photoId);
        p.confirmUploadedWith(2048L);
        try {
            Field f = p.getClass().getDeclaredField("createdAt");
            f.setAccessible(true);
            f.set(p, Instant.parse("2030-05-01T00:00:00Z"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return p;
    }
}
