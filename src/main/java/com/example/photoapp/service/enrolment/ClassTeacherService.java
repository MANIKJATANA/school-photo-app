package com.example.photoapp.service.enrolment;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.domain.enrolment.ClassTeacher;
import com.example.photoapp.domain.enrolment.ClassTeacherId;
import com.example.photoapp.domain.enrolment.TeachingRole;
import com.example.photoapp.domain.klass.Klass;
import com.example.photoapp.domain.teacher.Teacher;
import com.example.photoapp.repository.enrolment.ClassTeacherRepository;
import com.example.photoapp.repository.klass.KlassRepository;
import com.example.photoapp.repository.teacher.TeacherRepository;
import com.example.photoapp.web.dto.ClassTeacherDtos.AssignmentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ClassTeacherService {

    private static final Logger log = LoggerFactory.getLogger(ClassTeacherService.class);

    private final ClassTeacherRepository assignments;
    private final TeacherRepository teachers;
    private final KlassRepository classes;

    public ClassTeacherService(ClassTeacherRepository assignments,
                                TeacherRepository teachers,
                                KlassRepository classes) {
        this.assignments = assignments;
        this.teachers = teachers;
        this.classes = classes;
    }

    @Transactional
    public AssignmentResponse assign(UUID schoolId, UUID classId, UUID teacherId,
                                      TeachingRole role, UUID actorUserId) {
        verifyTeacherInSchool(schoolId, teacherId);
        verifyClassInSchool(schoolId, classId);

        ClassTeacherId pk = new ClassTeacherId(classId, teacherId);
        if (assignments.existsById(pk)) {
            throw new Errors.Conflict("Teacher is already assigned to this class");
        }

        ClassTeacher saved = assignments.save(new ClassTeacher(classId, teacherId, role));
        log.info("teacher {} assigned to class {} as {} actor={}", teacherId, classId, role, actorUserId);
        return toResponse(saved);
    }

    @Transactional
    public AssignmentResponse updateRole(UUID schoolId, UUID classId, UUID teacherId,
                                          TeachingRole role, UUID actorUserId) {
        verifyTeacherInSchool(schoolId, teacherId);
        verifyClassInSchool(schoolId, classId);

        ClassTeacher existing = assignments.findById(new ClassTeacherId(classId, teacherId))
                .orElseThrow(() -> new Errors.NotFound(
                        "assignment for teacher " + teacherId + " in class " + classId));

        existing.setRole(role);
        ClassTeacher saved = assignments.save(existing);
        log.info("teacher {} role in class {} updated to {} actor={}", teacherId, classId, role, actorUserId);
        return toResponse(saved);
    }

    @Transactional
    public void remove(UUID schoolId, UUID classId, UUID teacherId, UUID actorUserId) {
        verifyTeacherInSchool(schoolId, teacherId);
        verifyClassInSchool(schoolId, classId);

        ClassTeacherId pk = new ClassTeacherId(classId, teacherId);
        if (!assignments.existsById(pk)) {
            throw new Errors.NotFound("assignment for teacher " + teacherId + " in class " + classId);
        }
        assignments.deleteById(pk);
        log.info("teacher {} removed from class {} actor={}", teacherId, classId, actorUserId);
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> listByClass(UUID schoolId, UUID classId) {
        verifyClassInSchool(schoolId, classId);
        return assignments.findByIdClassIdOrderByCreatedAtAsc(classId).stream()
                .map(ClassTeacherService::toResponse)
                .toList();
    }

    private void verifyTeacherInSchool(UUID schoolId, UUID teacherId) {
        Teacher t = teachers.findByIdAndDeletedAtIsNull(teacherId)
                .orElseThrow(() -> new Errors.NotFound("teacher", teacherId));
        // Cross-school access leaks nothing: same NotFound, not Forbidden.
        if (!t.getSchoolId().equals(schoolId)) {
            throw new Errors.NotFound("teacher", teacherId);
        }
    }

    private void verifyClassInSchool(UUID schoolId, UUID classId) {
        Klass k = classes.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new Errors.NotFound("class", classId));
        if (!k.getSchoolId().equals(schoolId)) {
            throw new Errors.NotFound("class", classId);
        }
    }

    private static AssignmentResponse toResponse(ClassTeacher ct) {
        return new AssignmentResponse(ct.getClassId(), ct.getTeacherId(), ct.getRole(), ct.getCreatedAt());
    }
}
