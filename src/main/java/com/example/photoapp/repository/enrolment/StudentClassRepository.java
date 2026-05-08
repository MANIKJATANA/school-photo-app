package com.example.photoapp.repository.enrolment;

import com.example.photoapp.domain.enrolment.StudentClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentClassRepository extends JpaRepository<StudentClass, UUID> {

    Optional<StudentClass> findByStudentIdAndValidToIsNull(UUID studentId);

    List<StudentClass> findByClassIdAndValidToIsNull(UUID classId);
}
