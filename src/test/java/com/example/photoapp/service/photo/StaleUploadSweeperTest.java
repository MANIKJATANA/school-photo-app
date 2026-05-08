package com.example.photoapp.service.photo;

import com.example.photoapp.domain.photo.Photo;
import com.example.photoapp.domain.photo.UploadStatus;
import com.example.photoapp.repository.photo.PhotoRepository;
import com.example.photoapp.storage.blob.BlobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaleUploadSweeperTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-06-01T12:00:00Z"), ZoneOffset.UTC);
    private static final long TTL_MINUTES = 60;
    private static final int BATCH_SIZE = 500;

    private PhotoRepository photos;
    private BlobStore blobStore;
    private StaleUploadSweeper sweeper;

    @BeforeEach
    void setUp() {
        photos = mock(PhotoRepository.class);
        blobStore = mock(BlobStore.class);
        sweeper = new StaleUploadSweeper(photos, blobStore, CLOCK, TTL_MINUTES, BATCH_SIZE);
    }

    @Test
    void empty_result_is_a_noop() {
        when(photos.findByUploadStatusOlderThan(eq(UploadStatus.PENDING), any()))
                .thenReturn(List.of());

        int processed = sweeper.sweep();

        assertThat(processed).isEqualTo(0);
        verify(blobStore, never()).delete(any());
        verify(photos, never()).save(any());
    }

    @Test
    void marks_each_stale_row_as_failed_and_soft_deletes() {
        Photo p1 = pendingPhoto("k1");
        Photo p2 = pendingPhoto("k2");
        when(photos.findByUploadStatusOlderThan(eq(UploadStatus.PENDING), any()))
                .thenReturn(List.of(p1, p2));

        int processed = sweeper.sweep();

        assertThat(processed).isEqualTo(2);
        assertThat(p1.getUploadStatus()).isEqualTo(UploadStatus.FAILED);
        assertThat(p1.isDeleted()).isTrue();
        assertThat(p2.getUploadStatus()).isEqualTo(UploadStatus.FAILED);
        assertThat(p2.isDeleted()).isTrue();
        verify(blobStore).delete("k1");
        verify(blobStore).delete("k2");
        verify(photos, times(2)).save(any());
    }

    @Test
    void one_blob_delete_failure_does_not_stop_the_batch() {
        Photo p1 = pendingPhoto("k1");
        Photo p2 = pendingPhoto("k2");
        Photo p3 = pendingPhoto("k3");
        when(photos.findByUploadStatusOlderThan(eq(UploadStatus.PENDING), any()))
                .thenReturn(List.of(p1, p2, p3));
        // p2's blob delete blows up; sweeper should log + continue.
        doThrow(new RuntimeException("boom")).when(blobStore).delete("k2");

        int processed = sweeper.sweep();

        assertThat(processed).isEqualTo(2); // p1 and p3 succeeded
        assertThat(p1.getUploadStatus()).isEqualTo(UploadStatus.FAILED);
        assertThat(p2.getUploadStatus()).isEqualTo(UploadStatus.PENDING); // not advanced — sweeper retries next run
        assertThat(p3.getUploadStatus()).isEqualTo(UploadStatus.FAILED);
        verify(blobStore, atLeastOnce()).delete("k1");
        verify(blobStore, atLeastOnce()).delete("k2");
        verify(blobStore, atLeastOnce()).delete("k3");
    }

    @Test
    void caps_processing_at_batch_size_when_backlog_is_huge() {
        StaleUploadSweeper smallBatch = new StaleUploadSweeper(photos, blobStore, CLOCK, TTL_MINUTES, 2);
        when(photos.findByUploadStatusOlderThan(eq(UploadStatus.PENDING), any()))
                .thenReturn(List.of(pendingPhoto("a"), pendingPhoto("b"), pendingPhoto("c"), pendingPhoto("d")));

        int processed = smallBatch.sweep();

        assertThat(processed).isEqualTo(2);
        verify(photos, times(2)).save(any());
    }

    private static Photo pendingPhoto(String key) {
        Photo p = new Photo(UUID.randomUUID(), UUID.randomUUID(), key,
                "test-bucket", "image/jpeg", 1024L, UUID.randomUUID());
        p.assignIdForUpload(UUID.randomUUID());
        return p;
    }
}
