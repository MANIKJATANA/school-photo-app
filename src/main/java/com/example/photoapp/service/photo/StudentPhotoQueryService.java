package com.example.photoapp.service.photo;

import com.example.photoapp.common.error.Errors;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StudentPhotoQueryService {

    private static final Logger log = LoggerFactory.getLogger(StudentPhotoQueryService.class);
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private final PhotoQueryRepository photoQueries;
    private final StudentEventRepository studentEvents;
    private final StudentRepository students;
    private final BlobStore blobStore;
    private final CursorCodec cursorCodec;
    private final S3Properties s3Properties;
    private final Clock clock;

    public StudentPhotoQueryService(PhotoQueryRepository photoQueries,
                                     StudentEventRepository studentEvents,
                                     StudentRepository students,
                                     BlobStore blobStore,
                                     CursorCodec cursorCodec,
                                     S3Properties s3Properties,
                                     Clock clock) {
        this.photoQueries = photoQueries;
        this.studentEvents = studentEvents;
        this.students = students;
        this.blobStore = blobStore;
        this.cursorCodec = cursorCodec;
        this.s3Properties = s3Properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StudentEventResponse> listEventsForStudent(UUID schoolId, UUID studentId) {
        verifyStudentInSchool(schoolId, studentId);
        return studentEvents.findByIdStudentIdOrderByLastUpdatedAtDesc(studentId).stream()
                .map(StudentPhotoQueryService::toEventResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CursorPage<PhotoListItem> listPhotosForStudentInEvent(UUID schoolId, UUID studentId,
                                                                  UUID eventId,
                                                                  String cursor, Integer requestedLimit) {
        verifyStudentInSchool(schoolId, studentId);
        int limit = clampLimit(requestedLimit);
        CursorCodec.Cursor decoded = cursorCodec.decode(cursor);

        List<Photo> rows = photoQueries.findPhotosForStudentInEvent(
                studentId, eventId,
                decoded == null ? null : decoded.sortKey(),
                decoded == null ? null : decoded.id(),
                limit + 1);

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
        log.info("listPhotosForStudentInEvent student={} event={} count={} hasMore={}",
                studentId, eventId, items.size(), hasMore);
        return CursorPage.of(items, nextCursor, limit);
    }

    private void verifyStudentInSchool(UUID schoolId, UUID studentId) {
        Student s = students.findByIdAndDeletedAtIsNull(studentId)
                .orElseThrow(() -> new Errors.NotFound("student", studentId));
        if (!s.getSchoolId().equals(schoolId)) {
            throw new Errors.NotFound("student", studentId);
        }
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

    private static StudentEventResponse toEventResponse(StudentEvent se) {
        return new StudentEventResponse(se.getStudentId(), se.getEventId(),
                se.getPhotoCount(), se.getFirstSeenAt(), se.getLastUpdatedAt());
    }

    private static int clampLimit(Integer requested) {
        if (requested == null) return DEFAULT_LIMIT;
        if (requested < 1)     throw new Errors.BadRequest("limit must be >= 1");
        return Math.min(requested, MAX_LIMIT);
    }
}
