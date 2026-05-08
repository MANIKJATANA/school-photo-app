package com.example.photoapp.service.photo;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.pagination.CursorCodec;
import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.domain.event.Event;
import com.example.photoapp.domain.photo.Photo;
import com.example.photoapp.domain.photo.UploadStatus;
import com.example.photoapp.repository.event.EventRepository;
import com.example.photoapp.repository.photo.PhotoRepository;
import com.example.photoapp.storage.blob.BlobStore;
import com.example.photoapp.storage.s3.S3Properties;
import com.example.photoapp.web.dto.PhotoDtos.PhotoListItem;
import com.example.photoapp.web.dto.PhotoDtos.PhotoUrlResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PhotoQueryService {

    private static final Logger log = LoggerFactory.getLogger(PhotoQueryService.class);
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private final PhotoRepository photos;
    private final EventRepository events;
    private final BlobStore blobStore;
    private final CursorCodec cursorCodec;
    private final S3Properties s3Properties;
    private final Clock clock;

    public PhotoQueryService(PhotoRepository photos,
                              EventRepository events,
                              BlobStore blobStore,
                              CursorCodec cursorCodec,
                              S3Properties s3Properties,
                              Clock clock) {
        this.photos = photos;
        this.events = events;
        this.blobStore = blobStore;
        this.cursorCodec = cursorCodec;
        this.s3Properties = s3Properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PhotoUrlResponse getPresignedUrl(UUID schoolId, UUID photoId) {
        Photo photo = photos.findActiveByIdAlone(photoId)
                .orElseThrow(() -> new Errors.NotFound("photo", photoId));
        // Cross-school access leaks nothing: same NotFound, not Forbidden.
        if (!photo.getSchoolId().equals(schoolId)) {
            throw new Errors.NotFound("photo", photoId);
        }
        // Don't hand out URLs to PENDING / FAILED uploads — bytes may be missing or partial.
        if (photo.getUploadStatus() != UploadStatus.UPLOADED) {
            throw new Errors.NotFound("photo", photoId);
        }
        Duration ttl = s3Properties.getUrlTtl();
        URI url = blobStore.presignGet(photo.getBlobKey(), ttl);
        return new PhotoUrlResponse(url, clock.instant().plus(ttl));
    }

    @Transactional(readOnly = true)
    public CursorPage<PhotoListItem> listByEvent(UUID schoolId, UUID eventId,
                                                  String cursor, Integer requestedLimit) {
        verifyEventInSchool(schoolId, eventId);
        int limit = clampLimit(requestedLimit);
        CursorCodec.Cursor decoded = cursorCodec.decode(cursor);

        Specification<Photo> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("id").get("eventId"), eventId),
                cb.isNull(root.get("deletedAt")),
                cb.equal(root.get("uploadStatus"), UploadStatus.UPLOADED));
        if (decoded != null) {
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.lessThan(root.get("createdAt"), decoded.sortKey()),
                    cb.and(
                            cb.equal(root.get("createdAt"), decoded.sortKey()),
                            cb.lessThan(root.get("id").get("id"), decoded.id()))));
        }

        var page = photos.findAll(
                spec,
                PageRequest.of(0, limit + 1,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id.id"))));
        List<Photo> rows = page.getContent();
        boolean hasMore = rows.size() > limit;
        List<Photo> trimmed = hasMore ? rows.subList(0, limit) : rows;

        Duration ttl = s3Properties.getUrlTtl();
        Instant urlExpiresAt = clock.instant().plus(ttl);
        List<PhotoListItem> items = trimmed.stream()
                .map(p -> toItem(p, ttl, urlExpiresAt))
                .toList();

        String nextCursor = null;
        if (hasMore) {
            Photo last = trimmed.get(trimmed.size() - 1);
            nextCursor = cursorCodec.encode(new CursorCodec.Cursor(last.getCreatedAt(), last.getId()));
        }
        log.info("listByEvent event={} count={} hasMore={}", eventId, items.size(), hasMore);
        return CursorPage.of(items, nextCursor, limit);
    }

    private void verifyEventInSchool(UUID schoolId, UUID eventId) {
        Event e = events.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new Errors.NotFound("event", eventId));
        if (!e.getSchoolId().equals(schoolId)) {
            throw new Errors.NotFound("event", eventId);
        }
    }

    private static int clampLimit(Integer requested) {
        if (requested == null) return DEFAULT_LIMIT;
        if (requested < 1)     throw new Errors.BadRequest("limit must be >= 1");
        return Math.min(requested, MAX_LIMIT);
    }

    private PhotoListItem toItem(Photo p, Duration ttl, Instant urlExpiresAt) {
        URI url = blobStore.presignGet(p.getBlobKey(), ttl);
        return new PhotoListItem(
                p.getId(), p.getEventId(),
                p.getContentType(), p.getSizeBytes(),
                p.getWidthPx(), p.getHeightPx(), p.getTakenAt(),
                url, urlExpiresAt,
                p.getCreatedAt());
    }
}
