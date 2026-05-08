package com.example.photoapp.repository.enrolment;

import com.example.photoapp.domain.enrolment.ClassTeacher;
import com.example.photoapp.domain.enrolment.ClassTeacherId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClassTeacherRepository extends JpaRepository<ClassTeacher, ClassTeacherId> {

    List<ClassTeacher> findByIdClassIdOrderByCreatedAtAsc(UUID classId);

    List<ClassTeacher> findByIdTeacherId(UUID teacherId);
}
