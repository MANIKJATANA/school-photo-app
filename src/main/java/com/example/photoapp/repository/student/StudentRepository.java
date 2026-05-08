package com.example.photoapp.repository.student;

import com.example.photoapp.domain.student.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID>, JpaSpecificationExecutor<Student> {

    Optional<Student> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Student> findBySchoolIdAndRollNumberAndDeletedAtIsNull(UUID schoolId, String rollNumber);

    Optional<Student> findByUserIdAndDeletedAtIsNull(UUID userId);
}
