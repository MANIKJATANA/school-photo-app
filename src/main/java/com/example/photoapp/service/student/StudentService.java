package com.example.photoapp.service.student;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.common.pagination.CursorPaginator;
import com.example.photoapp.common.school.SchoolScopes;
import com.example.photoapp.domain.student.Student;
import com.example.photoapp.domain.user.AppUser;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.repository.student.StudentRepository;
import com.example.photoapp.service.provisioning.UserProvisioning;
import com.example.photoapp.web.dto.StudentDtos.CreateStudentRequest;
import com.example.photoapp.web.dto.StudentDtos.StudentResponse;
import com.example.photoapp.web.dto.StudentDtos.UpdateStudentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class StudentService {

    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    private final StudentRepository students;
    private final UserProvisioning userProvisioning;
    private final CursorPaginator paginator;
    private final Clock clock;

    public StudentService(StudentRepository students,
                          UserProvisioning userProvisioning,
                          CursorPaginator paginator,
                          Clock clock) {
        this.students = students;
        this.userProvisioning = userProvisioning;
        this.paginator = paginator;
        this.clock = clock;
    }

    @Transactional
    public StudentResponse create(UUID schoolId, UUID actorUserId, CreateStudentRequest req) {
        AppUser user = userProvisioning.provision(
                schoolId, req.email(), req.password(), Role.STUDENT, req.phone());

        Student s = new Student(schoolId, req.firstName(), req.lastName());
        s.setUserId(user.getId());
        s.setRollNumber(req.rollNumber());
        s.setDateOfBirth(req.dateOfBirth());
        try {
            students.saveAndFlush(s);
        } catch (DataIntegrityViolationException e) {
            // V1 UNIQUE (school_id, roll_number) — surface as 409, not 500.
            throw new Errors.Conflict("A student with that roll_number already exists in this school");
        }

        log.info("student created id={} school={} actor={}", s.getId(), schoolId, actorUserId);
        return toResponse(s);
    }

    @Transactional(readOnly = true)
    public CursorPage<StudentResponse> list(UUID schoolId, String cursor, Integer requestedLimit) {
        return paginator.paginate(
                students,
                SchoolScopes.activeInSchool(schoolId),
                cursor, requestedLimit,
                STUDENT_KEYS,
                StudentService::toResponse);
    }

    @Transactional(readOnly = true)
    public StudentResponse get(UUID schoolId, UUID id) {
        return toResponse(loadInSchool(schoolId, id));
    }

    @Transactional
    public StudentResponse update(UUID schoolId, UUID id, UpdateStudentRequest req, UUID actorUserId) {
        Student s = loadInSchool(schoolId, id);
        if (req.firstName() != null)   s.setFirstName(req.firstName());
        if (req.lastName() != null)    s.setLastName(req.lastName());
        if (req.rollNumber() != null)  s.setRollNumber(req.rollNumber());
        if (req.dateOfBirth() != null) s.setDateOfBirth(req.dateOfBirth());
        try {
            students.saveAndFlush(s);
        } catch (DataIntegrityViolationException e) {
            throw new Errors.Conflict("A student with that roll_number already exists in this school");
        }
        log.info("student updated id={} school={} actor={}", id, schoolId, actorUserId);
        return toResponse(s);
    }

    @Transactional
    public void softDelete(UUID schoolId, UUID id, UUID actorUserId) {
        Student s = loadInSchool(schoolId, id);
        s.softDelete(Instant.now(clock));
        students.save(s);
        log.info("student soft-deleted id={} school={} actor={}", id, schoolId, actorUserId);
    }

    private Student loadInSchool(UUID schoolId, UUID id) {
        Student s = students.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new Errors.NotFound("student", id));
        // Cross-school access leaks nothing: same NotFound, not Forbidden.
        if (!s.getSchoolId().equals(schoolId)) {
            throw new Errors.NotFound("student", id);
        }
        return s;
    }

    private static final CursorPaginator.RowKeys<Student> STUDENT_KEYS = new CursorPaginator.RowKeys<>() {
        @Override public Instant createdAt(Student s) { return s.getCreatedAt(); }
        @Override public UUID id(Student s)            { return s.getId(); }
    };

    private static StudentResponse toResponse(Student s) {
        return new StudentResponse(
                s.getId(), s.getSchoolId(), s.getUserId(),
                s.getFirstName(), s.getLastName(), s.getRollNumber(), s.getDateOfBirth(),
                s.getFaceEmbeddingStatus(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
