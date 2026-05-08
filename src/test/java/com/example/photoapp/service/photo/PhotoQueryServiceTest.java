package com.example.photoapp.service.photo;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.pagination.Base64CursorCodec;
import com.example.photoapp.common.pagination.CursorCodec;
import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.domain.event.Event;
import com.example.photoapp.domain.photo.Photo;
import com.example.photoapp.repository.event.EventRepository;
import com.example.photoapp.repository.photo.PhotoRepository;
import com.example.photoapp.storage.blob.BlobStore;
import com.example.photoapp.storage.s3.S3Properties;
import com.example.photoapp.web.dto.PhotoDtos.PhotoListItem;
import com.example.photoapp.web.dto.PhotoDtos.PhotoUrlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhotoQueryServiceTest {

    private static final UUID SCHOOL_A = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID SCHOOL_B = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID EVENT    = UUID.fromString("01900000-0000-7000-8000-000000000010");
    private static final UUID PHOTO    = UUID.fromString("01900000-0000-7000-8000-000000000020");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-06-01T00:00:00Z"), ZoneOffset.UTC);

    private final S3Properties s3Props = new S3Properties(
            "test-bucket", "us-east-1", "http://localhost:4566", true,
            "test", "test", Duration.ofMinutes(10), Duration.ofMinutes(5));

    private PhotoRepository photos;
    private EventRepository events;
    private BlobStore blobStore;
    private CursorCodec cursorCodec;
    private PhotoQueryService service;

    @BeforeEach
    void setUp() {
        photos = mock(PhotoRepository.class);
        events = mock(EventRepository.class);
        blobStore = mock(BlobStore.class);
        cursorCodec = new Base64CursorCodec();

        when(events.findByIdAndDeletedAtIsNull(EVENT)).thenReturn(Optional.of(eventInSchool(EVENT, SCHOOL_A)));
        when(blobStore.presignGet(any(), any())).thenReturn(URI.create("https://s3.example/key?sig=abc"));

        service = new PhotoQueryService(photos, events, blobStore, cursorCodec, s3Props, CLOCK);
    }

    @Test
    void getPresignedUrl_returns_url_and_expiry() {
        Photo p = uploadedPhoto(SCHOOL_A, EVENT, PHOTO);
        when(photos.findActiveByIdAlone(PHOTO)).thenReturn(Optional.of(p));

        PhotoUrlResponse resp = service.getPresignedUrl(SCHOOL_A, PHOTO);

        assertThat(resp.url().toString()).startsWith("https://");
        assertThat(resp.expiresAt()).isEqualTo(CLOCK.instant().plus(s3Props.getUrlTtl()));
    }

    @Test
    void getPresignedUrl_missing_photo_throws_NotFound() {
        when(photos.findActiveByIdAlone(PHOTO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPresignedUrl(SCHOOL_A, PHOTO))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void getPresignedUrl_cross_school_throws_NotFound() {
        Photo p = uploadedPhoto(SCHOOL_B, EVENT, PHOTO);
        when(photos.findActiveByIdAlone(PHOTO)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.getPresignedUrl(SCHOOL_A, PHOTO))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void getPresignedUrl_pending_photo_throws_NotFound() {
        Photo p = pendingPhoto(SCHOOL_A, EVENT, PHOTO);
        when(photos.findActiveByIdAlone(PHOTO)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.getPresignedUrl(SCHOOL_A, PHOTO))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listByEvent_returns_items_with_presigned_urls() {
        Photo p1 = uploadedPhoto(SCHOOL_A, EVENT, UUID.randomUUID());
        Photo p2 = uploadedPhoto(SCHOOL_A, EVENT, UUID.randomUUID());
        when(photos.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p1, p2)));

        CursorPage<PhotoListItem> page = service.listByEvent(SCHOOL_A, EVENT, null, 50);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items()).allMatch(item ->
                item.getUrl().toString().startsWith("https://") && item.urlExpiresAt() != null);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listByEvent_emits_cursor_when_more_rows_exist() {
        Photo p1 = uploadedPhoto(SCHOOL_A, EVENT, UUID.randomUUID());
        Photo p2 = uploadedPhoto(SCHOOL_A, EVENT, UUID.randomUUID());
        Photo p3 = uploadedPhoto(SCHOOL_A, EVENT, UUID.randomUUID());
        when(photos.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p1, p2, p3)));

        CursorPage<PhotoListItem> page = service.listByEvent(SCHOOL_A, EVENT, null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNotBlank();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listByEvent_clamps_excessive_limit() {
        when(photos.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        CursorPage<PhotoListItem> page = service.listByEvent(SCHOOL_A, EVENT, null, 99999);

        assertThat(page.limit()).isEqualTo(PhotoQueryService.MAX_LIMIT);
    }

    @Test
    void listByEvent_rejects_zero_or_negative_limit() {
        assertThatThrownBy(() -> service.listByEvent(SCHOOL_A, EVENT, null, 0))
                .isInstanceOf(Errors.BadRequest.class);
    }

    @Test
    void listByEvent_cross_school_event_throws_NotFound() {
        when(events.findByIdAndDeletedAtIsNull(EVENT))
                .thenReturn(Optional.of(eventInSchool(EVENT, SCHOOL_B)));

        assertThatThrownBy(() -> service.listByEvent(SCHOOL_A, EVENT, null, 50))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listByEvent_empty_event_returns_empty_page() {
        when(photos.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        CursorPage<PhotoListItem> page = service.listByEvent(SCHOOL_A, EVENT, null, 50);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    // ============ helpers ============

    private static Event eventInSchool(UUID id, UUID schoolId) {
        Event e = new Event(schoolId, "Default", UUID.randomUUID());
        setField(e, "id", id);
        return e;
    }

    private static Photo uploadedPhoto(UUID schoolId, UUID eventId, UUID photoId) {
        Photo p = new Photo(eventId, schoolId, "blob/" + photoId + ".jpg",
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

    private static Photo pendingPhoto(UUID schoolId, UUID eventId, UUID photoId) {
        Photo p = new Photo(eventId, schoolId, "blob/" + photoId + ".jpg",
                "test-bucket", "image/jpeg", 1024, UUID.randomUUID());
        p.assignIdForUpload(photoId);
        return p;
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
