package com.example.photoapp.repository.tagging;

import com.example.photoapp.domain.tagging.PhotoStudent;
import com.example.photoapp.domain.tagging.PhotoStudentId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PhotoStudentRepository extends JpaRepository<PhotoStudent, PhotoStudentId> {

    List<PhotoStudent> findByIdPhotoId(UUID photoId);

    List<PhotoStudent> findByIdStudentIdAndEventId(UUID studentId, UUID eventId);
}
