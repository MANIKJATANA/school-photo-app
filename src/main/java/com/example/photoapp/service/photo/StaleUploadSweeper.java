package com.example.photoapp.service.photo;

import com.example.photoapp.domain.photo.Photo;
import com.example.photoapp.domain.photo.UploadStatus;
import com.example.photoapp.repository.photo.PhotoRepository;
import com.example.photoapp.storage.blob.BlobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Periodically reaps {@code PENDING} photo rows whose upload was initiated
 * more than {@code photoapp.upload.pending-ttl-minutes} ago. For each row:
 * <ol>
 *   <li>Best-effort {@code BlobStore.delete(blobKey)} — idempotent, no-op
 *       if the client never PUT the bytes.</li>
 *   <li>Mark {@code uploadStatus = FAILED} and {@code deleted_at = now()} so
 *       the row exits every active read path. The row stays in the table
 *       for audit; a future retention sweep can hard-delete after a longer
 *       window.</li>
 * </ol>
 *
 * Failures of the per-row blob delete are logged at warn but do not stop
 * the batch — the next sweeper run will still see the row marked FAILED;
 * the stuck blob can be reaped manually if needed.
 */
@Component
public class StaleUploadSweeper {

    private static final Logger log = LoggerFactory.getLogger(StaleUploadSweeper.class);

    private final PhotoRepository photos;
    private final BlobStore blobStore;
    private final Clock clock;
    private final Duration pendingTtl;
    private final int batchSize;

    public StaleUploadSweeper(PhotoRepository photos,
                               BlobStore blobStore,
                               Clock clock,
                               @Value("${photoapp.upload.pending-ttl-minutes}") long pendingTtlMinutes,
                               @Value("${photoapp.upload.sweeper-batch-size}") int batchSize) {
        this.photos = photos;
        this.blobStore = blobStore;
        this.clock = clock;
        this.pendingTtl = Duration.ofMinutes(pendingTtlMinutes);
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${photoapp.upload.sweeper-interval-minutes}", timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void scheduledSweep() {
        sweep();
    }

    /** Exposed for tests to invoke without waiting for the schedule. */
    public int sweep() {
        Instant cutoff = clock.instant().minus(pendingTtl);
        List<Photo> stale = photos.findByUploadStatusOlderThan(UploadStatus.PENDING, cutoff);
        if (stale.isEmpty()) {
            return 0;
        }
        // Process at most batchSize per run so a backlog doesn't monopolise the worker thread.
        List<Photo> batch = stale.size() > batchSize ? stale.subList(0, batchSize) : stale;
        int processed = 0;
        for (Photo photo : batch) {
            try {
                processOne(photo);
                processed++;
            } catch (Exception e) {
                log.warn("sweeper failed on photo={}: {}", photo.getId(), e.getMessage());
            }
        }
        log.info("stale-upload sweep: found={} batch={} processed={}", stale.size(), batch.size(), processed);
        return processed;
    }

    @Transactional
    void processOne(Photo photo) {
        // Best-effort blob delete first — if it throws, the row stays PENDING and the next
        // sweep retries. If it succeeds (or the blob never existed), mark the row FAILED.
        try {
            blobStore.delete(photo.getBlobKey());
        } catch (RuntimeException e) {
            log.warn("blob delete failed for photo={} key={}: {}",
                    photo.getId(), photo.getBlobKey(), e.getMessage());
            throw e;
        }
        photo.markFailed(clock.instant());
        photos.save(photo);
    }
}
