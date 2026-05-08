package com.example.photoapp.service.enrolment;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.domain.enrolment.StudentClass;
import com.example.photoapp.domain.klass.Klass;
import com.example.photoapp.domain.student.Student;
import com.example.photoapp.repository.enrolment.StudentClassRepository;
import com.example.photoapp.repository.klass.KlassRepository;
import com.example.photoapp.repository.student.StudentRepository;
import com.example.photoapp.web.dto.EnrolmentDtos.EnrolmentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StudentEnrolmentService {

    private static final Logger log = LoggerFactory.getLogger(StudentEnrolmentService.class);

    private final StudentClassRepository enrolments;
    private final StudentRepository students;
    private final KlassRepository classes;
    private final Clock clock;

    public StudentEnrolmentService(StudentClassRepository enrolments,
                                    StudentRepository students,
                                    KlassRepository classes,
                                    Clock clock) {
        this.enrolments = enrolments;
        this.students = students;
        this.classes = classes;
        this.clock = clock;
    }

    @Transactional
    public EnrolmentResponse enrol(UUID schoolId, UUID classId, UUID studentId, UUID actorUserId) {
        verifyStudentInSchool(schoolId, studentId);
        verifyClassInSchool(schoolId, classId);

        Optional<StudentClass> existing = enrolments.findByStudentIdAndValidToIsNull(studentId);
        if (existing.isPresent()) {
            StudentClass active = existing.get();
            if (active.getClassId().equals(classId)) {
                // Idempotent: already enrolled here.
                return toResponse(active);
            }
            // Transfer: end the prior assignment in the same tx.
            active.end(today());
            enrolments.save(active);
            log.info("student {} transferred from class {} to {} actor={}",
                    studentId, active.getClassId(), classId, actorUserId);
        } else {
            log.info("student {} enrolled in class {} actor={}", studentId, classId, actorUserId);
        }

        StudentClass fresh = new StudentClass(studentId, classId, today());
        try {
            enrolments.saveAndFlush(fresh);
        } catch (DataIntegrityViolationException e) {
            // V1 uq_student_class_active — defence-in-depth if a concurrent enrolment beat us.
            throw new Errors.Conflict("Student already has an active class assignment");
        }
        return toResponse(fresh);
    }

    @Transactional
    public void unenrol(UUID schoolId, UUID classId, UUID studentId, UUID actorUserId) {
        verifyStudentInSchool(schoolId, studentId);
        verifyClassInSchool(schoolId, classId);

        StudentClass active = enrolments.findByStudentIdAndValidToIsNull(studentId)
                .filter(sc -> sc.getClassId().equals(classId))
                .orElseThrow(() -> new Errors.NotFound(
                        "active enrolment for student " + studentId + " in class " + classId));

        active.end(today());
        enrolments.save(active);
        log.info("student {} unenrolled from class {} actor={}", studentId, classId, actorUserId);
    }

    @Transactional(readOnly = true)
    public List<EnrolmentResponse> listActiveByClass(UUID schoolId, UUID classId) {
        verifyClassInSchool(schoolId, classId);
        return enrolments.findByClassIdAndValidToIsNull(classId).stream()
                .map(StudentEnrolmentService::toResponse)
                .toList();
    }

    private void verifyStudentInSchool(UUID schoolId, UUID studentId) {
        Student s = students.findByIdAndDeletedAtIsNull(studentId)
                .orElseThrow(() -> new Errors.NotFound("student", studentId));
        // Cross-school access leaks nothing: same NotFound, not Forbidden.
        if (!s.getSchoolId().equals(schoolId)) {
            throw new Errors.NotFound("student", studentId);
        }
    }

    private void verifyClassInSchool(UUID schoolId, UUID classId) {
        Klass k = classes.findByIdAndDeletedAtIsNull(classId)
                .orElseThrow(() -> new Errors.NotFound("class", classId));
        if (!k.getSchoolId().equals(schoolId)) {
            throw new Errors.NotFound("class", classId);
        }
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), clock.getZone());
    }

    private static EnrolmentResponse toResponse(StudentClass sc) {
        return new EnrolmentResponse(sc.getId(), sc.getStudentId(), sc.getClassId(),
                sc.getValidFrom(), sc.getValidTo());
    }
}
