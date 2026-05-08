package com.example.photoapp.repository.teacher;

import com.example.photoapp.domain.teacher.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TeacherRepository extends JpaRepository<Teacher, UUID>, JpaSpecificationExecutor<Teacher> {

    Optional<Teacher> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Teacher> findBySchoolIdAndEmployeeIdAndDeletedAtIsNull(UUID schoolId, String employeeId);

    Optional<Teacher> findByUserIdAndDeletedAtIsNull(UUID userId);
}
