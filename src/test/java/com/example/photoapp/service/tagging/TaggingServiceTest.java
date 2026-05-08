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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaggingServiceTest {

    private static final UUID SCHOOL_A = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID SCHOOL_B = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID ADMIN    = UUID.fromString("01900000-0000-7000-8000-000000000010");
    private static final UUID PHOTO    = UUID.fromString("01900000-0000-7000-8000-000000000020");
    private static final UUID EVENT    = UUID.fromString("01900000-0000-7000-8000-000000000030");
    private static final UUID STUDENT  = UUID.fromString("01900000-0000-7000-8000-000000000040");

    private PhotoStudentRepository tags;
    private PhotoRepository photos;
    private StudentRepository students;
    private StudentEventRefresher refresher;
    private TaggingService service;

    @BeforeEach
    void setUp() {
        tags = mock(PhotoStudentRepository.class);
        photos = mock(PhotoRepository.class);
        students = mock(StudentRepository.class);
        refresher = mock(StudentEventRefresher.class);

        when(photos.findActiveByIdAlone(PHOTO))
                .thenReturn(Optional.of(photoInSchoolEvent(SCHOOL_A, EVENT, PHOTO)));
        when(students.findByIdAndDeletedAtIsNull(STUDENT))
                .thenReturn(Optional.of(studentInSchool(SCHOOL_A, STUDENT)));
        when(tags.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new TaggingService(tags, photos, students, refresher);
    }

    @Test
    void addTag_first_time_persists_row_and_increments_precompute() {
        when(tags.findById(any())).thenReturn(Optional.empty());

        TagResponse resp = service.addTag(SCHOOL_A, ADMIN, PHOTO, STUDENT, 1.0f);

        assertThat(resp.studentId()).isEqualTo(STUDENT);
        assertThat(resp.isConfirmed()).isTrue();

        ArgumentCaptor<PhotoStudent> captor = ArgumentCaptor.forClass(PhotoStudent.class);
        verify(tags).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(EVENT);
        verify(refresher).apply(STUDENT, EVENT, +1);
    }

    @Test
    void addTag_idempotent_when_tag_already_exists() {
        PhotoStudent existing = new PhotoStudent(PHOTO, STUDENT, EVENT, 1.0f, TaggingService.MANUAL_RUN);
        existing.setIsConfirmed(Boolean.TRUE);
        when(tags.findById(any())).thenReturn(Optional.of(existing));

        service.addTag(SCHOOL_A, ADMIN, PHOTO, STUDENT, 1.0f);

        verify(tags, never()).save(any());
        verify(refresher, never()).apply(any(), any(), eq(1));
    }

    @Test
    void addTag_cross_school_photo_throws_NotFound() {
        when(photos.findActiveByIdAlone(PHOTO))
                .thenReturn(Optional.of(photoInSchoolEvent(SCHOOL_B, EVENT, PHOTO)));

        assertThatThrownBy(() -> service.addTag(SCHOOL_A, ADMIN, PHOTO, STUDENT, 1.0f))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void addTag_cross_school_student_throws_NotFound() {
        when(students.findByIdAndDeletedAtIsNull(STUDENT))
                .thenReturn(Optional.of(studentInSchool(SCHOOL_B, STUDENT)));

        assertThatThrownBy(() -> service.addTag(SCHOOL_A, ADMIN, PHOTO, STUDENT, 1.0f))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void removeTag_existing_deletes_row_and_decrements_precompute() {
        when(tags.existsById(new PhotoStudentId(PHOTO, STUDENT))).thenReturn(true);

        service.removeTag(SCHOOL_A, ADMIN, PHOTO, STUDENT);

        verify(tags).deleteById(new PhotoStudentId(PHOTO, STUDENT));
        verify(refresher).apply(STUDENT, EVENT, -1);
    }

    @Test
    void removeTag_missing_throws_NotFound() {
        when(tags.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> service.removeTag(SCHOOL_A, ADMIN, PHOTO, STUDENT))
                .isInstanceOf(Errors.NotFound.class);
    }

    @Test
    void confirmTag_flips_to_FALSE_decrements_precompute() {
        PhotoStudent existing = new PhotoStudent(PHOTO, STUDENT, EVENT, 0.9f, UUID.randomUUID());
        existing.setIsConfirmed(Boolean.TRUE);
        when(tags.findById(any())).thenReturn(Optional.of(existing));

        service.confirmTag(SCHOOL_A, ADMIN, PHOTO, STUDENT, false);

        assertThat(existing.getIsConfirmed()).isFalse();
        verify(refresher).apply(STUDENT, EVENT, -1);
    }

    @Test
    void confirmTag_flips_to_TRUE_increments_precompute() {
        PhotoStudent existing = new PhotoStudent(PHOTO, STUDENT, EVENT, 0.9f, UUID.randomUUID());
        existing.setIsConfirmed(Boolean.FALSE);
        when(tags.findById(any())).thenReturn(Optional.of(existing));

        service.confirmTag(SCHOOL_A, ADMIN, PHOTO, STUDENT, true);

        assertThat(existing.getIsConfirmed()).isTrue();
        verify(refresher).apply(STUDENT, EVENT, +1);
    }

    @Test
    void confirmTag_no_change_in_visibility_does_not_touch_precompute() {
        PhotoStudent existing = new PhotoStudent(PHOTO, STUDENT, EVENT, 0.9f, UUID.randomUUID());
        existing.setIsConfirmed(Boolean.TRUE);
        when(tags.findById(any())).thenReturn(Optional.of(existing));

        service.confirmTag(SCHOOL_A, ADMIN, PHOTO, STUDENT, true);

        verify(refresher, never()).apply(any(), any(), any(Integer.class));
    }

    // ============ helpers ============

    private static Photo photoInSchoolEvent(UUID schoolId, UUID eventId, UUID photoId) {
        Photo p = new Photo(eventId, schoolId, "blob/k.jpg", "test-bucket",
                "image/jpeg", 1024, UUID.randomUUID());
        p.assignIdForUpload(photoId);
        p.confirmUploadedWith(2048L);
        return p;
    }

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
}
