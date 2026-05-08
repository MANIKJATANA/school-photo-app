package com.example.photoapp.repository.tagging;

import com.example.photoapp.domain.tagging.StudentEvent;
import com.example.photoapp.domain.tagging.StudentEventId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentEventRepository extends JpaRepository<StudentEvent, StudentEventId> {

    Optional<StudentEvent> findByIdStudentIdAndIdEventId(UUID studentId, UUID eventId);

    List<StudentEvent> findByIdStudentIdOrderByLastUpdatedAtDesc(UUID studentId);
}
