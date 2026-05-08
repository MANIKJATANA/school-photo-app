package com.example.photoapp.service.photo;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.id.IdGenerator;
import com.example.photoapp.domain.event.Event;
import com.example.photoapp.domain.photo.Photo;
import com.example.photoapp.domain.photo.UploadStatus;
import com.example.photoapp.repository.event.EventRepository;
import com.example.photoapp.repository.photo.PhotoRepository;
import com.example.photoapp.storage.blob.BlobKeyStrategy;
import com.example.photoapp.storage.blob.BlobMetadata;
import com.example.photoapp.storage.blob.BlobStore;
import com.example.photoapp.storage.s3.S3Properties;
import com.example.photoapp.web.dto.PhotoDtos.InitiateUploadRequest;
import com.example.photoapp.web.dto.PhotoDtos.InitiateUploadResponse;
import com.example.photoapp.web.dto.PhotoDtos.PhotoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Service
public class PhotoUploadService {

    private static final Logger log = LoggerFactory.getLogger(PhotoUploadService.class);

    private final PhotoRepository photos;
    private final EventRepository events;
    private final BlobStore blobStore;
    private final S3Properties s3Properties;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final long maxSizeBytes;
    private final Set<String> allowedContentTypes;

    public PhotoUploadService(
            PhotoRepository photos,
            EventRepository events,
            BlobStore blobStore,
            S3Properties s3Properties,
            IdGenerator idGenerator,
            Clock clock,
            @Value("${photoapp.upload.max-size-bytes}") long maxSizeBytes,
            @Value("#{'${photoapp.upload.allowed-content-types}'.split(',')}") Set<String> allowedContentTypes) {
        this.photos = photos;
        this.events = events;
        this.blobStore = blobStore;
        this.s3Properties = s3Properties;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.maxSizeBytes = maxSizeBytes;
        this.allowedContentTypes = Set.copyOf(allowedContentTypes);
    }

    @Transactional
    public InitiateUploadResponse initiate(UUID schoolId, UUID actorUserId, InitiateUploadRequest req) {
        validateContentType(req.contentType());
        validateSize(req.sizeBytes());
        Event event = loadEventInSchool(schoolId, req.eventId());

        String extension = extensionFor(req.contentType());
        UUID photoId = idGenerator.newId();
        String blobKey = BlobKeyStrategy.photoKey(schoolId, event.getId(), photoId, extension);

        // Photo's @PrePersist generates the embedded id via Ids.newId(), but we want the photoId
        // we just generated so the response's photoId matches the persisted row. The constructor
        // overrides the id field with our generated one.
        Photo photo = newPhoto(event.getId(), schoolId, photoId, blobKey,
                s3Properties.bucket(), req.contentType(), req.sizeBytes(), actorUserId);
        photos.saveAndFlush(photo);

        Duration ttl = s3Properties.putUrlTtl();
        URI putUrl = blobStore.presignPut(blobKey, req.contentType(), ttl);

        log.info("photo upload initiated photo={} event={} school={} actor={}",
                photoId, event.getId(), schoolId, actorUserId);
        return new InitiateUploadResponse(photoId, event.getId(), putUrl, clock.instant().plus(ttl));
    }

    @Transactional
    public PhotoResponse confirm(UUID schoolId, UUID actorUserId, UUID photoId) {
        Photo photo = loadPhotoForConfirm(schoolId, photoId);
        if (photo.getUploadStatus() == UploadStatus.UPLOADED) {
            // Idempotent: nothing to do.
            return toResponse(photo);
        }

        BlobMetadata meta = blobStore.head(photo.getBlobKey())
                .orElseThrow(() -> new Errors.NotFound("Blob not found at the expected key"));

        // Authoritative size from the blob, not the client claim.
        photo.confirmUploadedWith(meta.sizeBytes());
        photos.saveAndFlush(photo);

        log.info("photo upload confirmed photo={} school={} actor={} size={}",
                photoId, schoolId, actorUserId, meta.sizeBytes());
        return toResponse(photo);
    }

    private Event loadEventInSchool(UUID schoolId, UUID eventId) {
        Event e = events.findByIdAndDeletedAtIsNull(eventId)
                .orElseThrow(() -> new Errors.NotFound("event", eventId));
        // Cross-school access leaks nothing: same NotFound, not Forbidden.
        if (!e.getSchoolId().equals(schoolId)) {
            throw new Errors.NotFound("event", eventId);
        }
        return e;
    }

    private Photo loadPhotoForConfirm(UUID schoolId, UUID photoId) {
        Photo photo = photos.findActiveByIdAlone(photoId)
                .orElseThrow(() -> new Errors.NotFound("photo", photoId));
        if (!photo.getSchoolId().equals(schoolId)) {
            throw new Errors.NotFound("photo", photoId);
        }
        return photo;
    }

    private void validateContentType(String contentType) {
        if (!allowedContentTypes.contains(contentType)) {
            throw new Errors.BadRequest("Unsupported content type: " + contentType);
        }
    }

    private void validateSize(long sizeBytes) {
        if (sizeBytes <= 0 || sizeBytes > maxSizeBytes) {
            throw new Errors.BadRequest("size_bytes must be in [1, " + maxSizeBytes + "]");
        }
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png"  -> "png";
            case "image/heic" -> "heic";
            case "image/webp" -> "webp";
            default -> throw new Errors.BadRequest("Unsupported content type: " + contentType);
        };
    }

    private static Photo newPhoto(UUID eventId, UUID schoolId, UUID photoId, String blobKey,
                                   String blobBucket, String contentType, long sizeBytes, UUID uploadedBy) {
        Photo p = new Photo(eventId, schoolId, blobKey, blobBucket, contentType, sizeBytes, uploadedBy);
        p.assignIdForUpload(photoId);
        return p;
    }

    private static PhotoResponse toResponse(Photo p) {
        return new PhotoResponse(
                p.getId(), p.getEventId(), p.getSchoolId(),
                p.getContentType(), p.getSizeBytes(),
                p.getWidthPx(), p.getHeightPx(), p.getTakenAt(),
                p.getUploadStatus(), p.getMlStatus(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
