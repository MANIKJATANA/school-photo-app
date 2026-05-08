package com.example.photoapp.service.tagging;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.domain.photo.Photo;
import com.example.photoapp.domain.student.Student;
import com.example.photoapp.domain.tagging.PhotoStudent;
import com.example.photoapp.domain.tagging.PhotoStudentId;
import com.example.photoapp.repository.photo.PhotoRepository;
import com.example.photoapp.repository.student.StudentRepository;
import com.example.photoapp.repository.tagging.PhotoStudentRepository;
import com.example.photoapp.web.dto.TaggingDtos.TagResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Manual tag/untag/confirm operations on a photo. Every mutation goes through
 * {@link StudentEventRefresher} in the same tx so the precompute stays
 * consistent (ADR 0004).
 *
 * <p>For manual tags: ml_run_id uses the all-zero UUID sentinel
 * ({@link #MANUAL_RUN}); confidence is fixed at 1.0; is_confirmed is TRUE
 * (admin tagged it deliberately).
 */
@Service
public class TaggingService {

    private static final Logger log = LoggerFactory.getLogger(TaggingService.class);
    static final UUID MANUAL_RUN = new UUID(0L, 0L);

    private final PhotoStudentRepository tags;
    private final PhotoRepository photos;
    private final StudentRepository students;
    private final StudentEventRefresher refresher;

    public TaggingService(PhotoStudentRepository tags,
                          PhotoRepository photos,
                          StudentRepository students,
                          StudentEventRefresher refresher) {
        this.tags = tags;
        this.photos = photos;
        this.students = students;
        this.refresher = refresher;
    }

    @Transactional
    public TagResponse addTag(UUID schoolId, UUID actorUserId, UUID photoId, UUID studentId, Float confidence) {
        Photo photo = loadPhotoInSchool(schoolId, photoId);
        verifyStudentInSchool(schoolId, studentId);

        PhotoStudentId pk = new PhotoStudentId(photo.getId(), studentId);
        Optional<PhotoStudent> existing = tags.findById(pk);
        if (existing.isPresent()) {
            // Idempotent: tag already present. Return as-is, no count change.
            return toResponse(existing.get());
        }

        PhotoStudent fresh = new PhotoStudent(
                photo.getId(), studentId, photo.getEventId(),
                confidence == null ? 1.0f : confidence,
                MANUAL_RUN);
        fresh.setIsConfirmed(Boolean.TRUE);
        tags.save(fresh);
        refresher.apply(studentId, photo.getEventId(), +1);

        log.info("manual tag added photo={} student={} event={} actor={}",
                photoId, studentId, photo.getEventId(), actorUserId);
        return toResponse(fresh);
    }

    @Transactional
    public void removeTag(UUID schoolId, UUID actorUserId, UUID photoId, UUID studentId) {
        Photo photo = loadPhotoInSchool(schoolId, photoId);
        PhotoStudentId pk = new PhotoStudentId(photo.getId(), studentId);
        if (!tags.existsById(pk)) {
            throw new Errors.NotFound("tag for student " + studentId + " on photo " + photoId);
        }
        tags.deleteById(pk);
        refresher.apply(studentId, photo.getEventId(), -1);
        log.info("manual tag removed photo={} student={} event={} actor={}",
                photoId, studentId, photo.getEventId(), actorUserId);
    }

    @Transactional
    public TagResponse confirmTag(UUID schoolId, UUID actorUserId, UUID photoId, UUID studentId, boolean confirmed) {
        Photo photo = loadPhotoInSchool(schoolId, photoId);
        PhotoStudentId pk = new PhotoStudentId(photo.getId(), studentId);
        PhotoStudent ps = tags.findById(pk).orElseThrow(
                () -> new Errors.NotFound("tag for student " + studentId + " on photo " + photoId));

        Boolean wasIncluded = ps.getIsConfirmed() == null ? Boolean.TRUE : ps.getIsConfirmed();
        Boolean isIncluded = confirmed ? Boolean.TRUE : Boolean.FALSE;
        ps.setIsConfirmed(isIncluded);
        tags.save(ps);

        // If the row's visibility flipped, adjust the precompute count.
        if (wasIncluded && !isIncluded) {
            refresher.apply(studentId, photo.getEventId(), -1);
        } else if (!wasIncluded && isIncluded) {
            refresher.apply(studentId, photo.getEventId(), +1);
        }
        log.info("tag confirmed={} photo={} student={} actor={}",
                confirmed, photoId, studentId, actorUserId);
        return toResponse(ps);
    }

    private Photo loadPhotoInSchool(UUID schoolId, UUID photoId) {
        Photo photo = photos.findActiveByIdAlone(photoId)
                .orElseThrow(() -> new Errors.NotFound("photo", photoId));
        // Cross-school access leaks nothing: same NotFound, not Forbidden.
        if (!photo.getSchoolId().equals(schoolId)) {
            throw new Errors.NotFound("photo", photoId);
        }
        return photo;
    }

    private void verifyStudentInSchool(UUID schoolId, UUID studentId) {
        Student s = students.findByIdAndDeletedAtIsNull(studentId)
                .orElseThrow(() -> new Errors.NotFound("student", studentId));
        if (!s.getSchoolId().equals(schoolId)) {
            throw new Errors.NotFound("student", studentId);
        }
    }

    private static TagResponse toResponse(PhotoStudent ps) {
        return new TagResponse(ps.getPhotoId(), ps.getStudentId(), ps.getEventId(),
                ps.getConfidence(), ps.getIsConfirmed(), ps.getCreatedAt());
    }
}
