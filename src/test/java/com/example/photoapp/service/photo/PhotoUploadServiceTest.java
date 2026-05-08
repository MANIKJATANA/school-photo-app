package com.example.photoapp.service.photo;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.id.IdGenerator;
import com.example.photoapp.domain.event.Event;
import com.example.photoapp.domain.photo.Photo;
import com.example.photoapp.domain.photo.UploadStatus;
import com.example.photoapp.repository.event.EventRepository;
import com.example.photoapp.repository.photo.PhotoRepository;
import com.example.photoapp.storage.blob.BlobMetadata;
import com.example.photoapp.storage.blob.BlobStore;
import com.example.photoapp.storage.s3.S3Properties;
import com.example.photoapp.web.dto.PhotoDtos.InitiateUploadRequest;
import com.example.photoapp.web.dto.PhotoDtos.InitiateUploadResponse;
import com.example.photoapp.web.dto.PhotoDtos.PhotoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoUploadServiceTest {

    private static final UUID SCHOOL_A = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID SCHOOL_B = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID ADMIN    = UUID.fromString("01900000-0000-7000-8000-000000000010");
    private static final UUID EVENT    = UUID.fromString("01900000-0000-7000-8000-000000000020");
    private static final UUID PHOTO    = UUID.fromString("01900000-0000-7000-8000-000000000030");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-06-01T00:00:00Z"), ZoneOffset.UTC);

    private final S3Properties s3Props = new S3Properties(
            "test-bucket", "us-east-1", "http://localhost:4566", true,
            "test", "test", Duration.ofMinutes(10), Duration.ofMinutes(5));

    private PhotoRepository photos;
    private EventRepository events;
    private BlobStore blobStore;
    private IdGenerator idGenerator;
    private PhotoUploadService service;

    @BeforeEach
    void setUp() {
        photos = mock(PhotoRepository.class);
        events = mock(EventRepository.class);
        blobStore = mock(BlobStore.class);
        idGenerator = mock(IdGenerator.class);

        when(idGenerator.newId()).thenReturn(PHOTO);
        when(events.findByIdAndDeletedAtIsNull(EVENT)).thenReturn(Optional.of(eventInSchool(EVENT, SCHOOL_A)));
        when(blobStore.presignPut(any(), any(), any())).thenReturn(URI.create("https://s3.example/test-key?sig=abc"));
        when(photos.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new PhotoUploadService(photos, events, blobStore, s3Props, idGenerator, CLOCK,
                52_428_800L, Set.of("image/jpeg", "image/png", "image/heic", "image/webp"));
    }

    @Test
    void initiate_persists_pending_photo_and_returns_presigned_url() {
        InitiateUploadResponse resp = service.initiate(SCHOOL_A, ADMIN,
                new InitiateUploadRequest(EVENT, "image/jpeg", 1024L));

        assertThat(resp.photoId()).isEqualTo(PHOTO);
        assertThat(resp.eventId()).isEqualTo(EVENT);
        assertThat(resp.putUrl().toString()).startsWith("https://");
        assertThat(resp.expiresAt()).isEqualTo(CLOCK.instant().plus(s3Props.putUrlTtl()));

        ArgumentCaptor<Photo> captor = ArgumentCaptor.forClass(Photo.class);
        verify(photos).saveAndFlush(captor.capture());
        Photo saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(EVENT);
        assertThat(saved.getId()).isEqualTo(PHOTO);
        assertThat(saved.getSchoolId()).isEqualTo(SCHOOL_A);
        assertThat(saved.getUploadStatus()).isEqualTo(UploadStatus.PENDING);
        assertThat(saved.getBlobKey()).contains(SCHOOL_A.toString())
                .contains(EVENT.toString())
                .contains(PHOTO.toString())
                .endsWith(".jpg");
    }

    @Test
    void initiate_cross_school_event_throws_NotFound() {
        when(events.findByIdAndDeletedAtIsNull(EVENT))
                .thenReturn(Optional.of(eventInSchool(EVENT, SCHOOL_B)));

        assertThatThrownBy(() -> service.initiate(SCHOOL_A, ADMIN,
                new InitiateUploadRequest(EVENT, "image/jpeg", 1024L)))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void initiate_disallowed_content_type_throws_BadRequest() {
        assertThatThrownBy(() -> service.initiate(SCHOOL_A, ADMIN,
                new InitiateUploadRequest(EVENT, "application/zip", 1024L)))
                .isInstanceOf(Errors.BadRequest.class);
    }

    @Test
    void initiate_oversize_throws_BadRequest() {
        assertThatThrownBy(() -> service.initiate(SCHOOL_A, ADMIN,
                new InitiateUploadRequest(EVENT, "image/jpeg", 60_000_000L)))
                .isInstanceOf(Errors.BadRequest.class);
    }

    @Test
    void initiate_zero_or_negative_size_throws_BadRequest() {
        assertThatThrownBy(() -> service.initiate(SCHOOL_A, ADMIN,
                new InitiateUploadRequest(EVENT, "image/jpeg", 0L)))
                .isInstanceOf(Errors.BadRequest.class);
    }

    @Test
    void confirm_flips_to_uploaded_and_uses_blob_size_authoritatively() {
        Photo pending = pendingPhoto(SCHOOL_A, EVENT, PHOTO, 1024L); // client claimed 1024
        when(photos.findActiveByIdAlone(PHOTO)).thenReturn(Optional.of(pending));
        when(blobStore.head(any())).thenReturn(Optional.of(new BlobMetadata(2048L, "image/jpeg"))); // actual

        PhotoResponse resp = service.confirm(SCHOOL_A, ADMIN, PHOTO);

        assertThat(resp.uploadStatus()).isEqualTo(UploadStatus.UPLOADED);
        assertThat(resp.sizeBytes()).isEqualTo(2048L); // authoritative from blob, not the 1024 claim
    }

    @Test
    void confirm_when_blob_missing_throws_NotFound() {
        Photo pending = pendingPhoto(SCHOOL_A, EVENT, PHOTO, 1024L);
        when(photos.findActiveByIdAlone(PHOTO)).thenReturn(Optional.of(pending));
        when(blobStore.head(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(SCHOOL_A, ADMIN, PHOTO))
                .isInstanceOf(Errors.NotFound.class);
        verify(photos, never()).saveAndFlush(any());
    }

    @Test
    void confirm_cross_school_throws_NotFound() {
        Photo pending = pendingPhoto(SCHOOL_B, EVENT, PHOTO, 1024L);
        when(photos.findActiveByIdAlone(PHOTO)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.confirm(SCHOOL_A, ADMIN, PHOTO))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void confirm_idempotent_on_already_uploaded_row() {
        Photo uploaded = pendingPhoto(SCHOOL_A, EVENT, PHOTO, 1024L);
        uploaded.confirmUploadedWith(2048L);
        when(photos.findActiveByIdAlone(PHOTO)).thenReturn(Optional.of(uploaded));

        PhotoResponse resp = service.confirm(SCHOOL_A, ADMIN, PHOTO);

        assertThat(resp.uploadStatus()).isEqualTo(UploadStatus.UPLOADED);
        assertThat(resp.sizeBytes()).isEqualTo(2048L);
        verify(blobStore, never()).head(any());
        verify(photos, never()).saveAndFlush(any());
    }

    @Test
    void confirm_missing_photo_throws_NotFound() {
        when(photos.findActiveByIdAlone(PHOTO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(SCHOOL_A, ADMIN, PHOTO))
                .isInstanceOf(Errors.NotFound.class);
    }

    // ============ helpers ============

    private static Event eventInSchool(UUID id, UUID schoolId) {
        Event e = new Event(schoolId, "Default", UUID.randomUUID());
        setField(e, "id", id);
        return e;
    }

    private static Photo pendingPhoto(UUID schoolId, UUID eventId, UUID photoId, long size) {
        Photo p = new Photo(eventId, schoolId, "blob/key.jpg", "test-bucket", "image/jpeg", size, UUID.randomUUID());
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
