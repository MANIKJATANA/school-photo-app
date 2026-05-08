package com.example.photoapp.service.teacher;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.pagination.CursorCodec;
import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.common.school.SchoolScopes;
import com.example.photoapp.domain.teacher.Teacher;
import com.example.photoapp.domain.user.AppUser;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.repository.teacher.TeacherRepository;
import com.example.photoapp.service.provisioning.UserProvisioning;
import com.example.photoapp.web.dto.TeacherDtos.CreateTeacherRequest;
import com.example.photoapp.web.dto.TeacherDtos.TeacherResponse;
import com.example.photoapp.web.dto.TeacherDtos.UpdateTeacherRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TeacherService {

    private static final Logger log = LoggerFactory.getLogger(TeacherService.class);
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private final TeacherRepository teachers;
    private final UserProvisioning userProvisioning;
    private final CursorCodec cursorCodec;
    private final Clock clock;

    public TeacherService(TeacherRepository teachers,
                          UserProvisioning userProvisioning,
                          CursorCodec cursorCodec,
                          Clock clock) {
        this.teachers = teachers;
        this.userProvisioning = userProvisioning;
        this.cursorCodec = cursorCodec;
        this.clock = clock;
    }

    @Transactional
    public TeacherResponse create(UUID schoolId, UUID actorUserId, CreateTeacherRequest req) {
        AppUser user = userProvisioning.provision(
                schoolId, req.email(), req.password(), Role.TEACHER, req.phone());

        Teacher t = new Teacher(schoolId, user.getId(), req.firstName(), req.lastName());
        t.setEmployeeId(req.employeeId());
        try {
            teachers.saveAndFlush(t);
        } catch (DataIntegrityViolationException e) {
            // V1 UNIQUE (school_id, employee_id) — surface as 409, not 500.
            throw new Errors.Conflict("A teacher with that employee_id already exists in this school");
        }

        log.info("teacher created id={} school={} actor={}", t.getId(), schoolId, actorUserId);
        return toResponse(t);
    }

    @Transactional(readOnly = true)
    public CursorPage<TeacherResponse> list(UUID schoolId, String cursor, Integer requestedLimit) {
        int limit = clampLimit(requestedLimit);
        CursorCodec.Cursor decoded = cursorCodec.decode(cursor);

        Specification<Teacher> spec = SchoolScopes.<Teacher>activeInSchool(schoolId);
        if (decoded != null) {
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.lessThan(root.get("createdAt"), decoded.sortKey()),
                    cb.and(
                            cb.equal(root.get("createdAt"), decoded.sortKey()),
                            cb.lessThan(root.get("id"), decoded.id()))));
        }

        var page = teachers.findAll(
                spec,
                PageRequest.of(0, limit + 1,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        List<Teacher> rows = page.getContent();
        boolean hasMore = rows.size() > limit;
        List<Teacher> trimmed = hasMore ? rows.subList(0, limit) : rows;

        String nextCursor = null;
        if (hasMore) {
            Teacher last = trimmed.get(trimmed.size() - 1);
            nextCursor = cursorCodec.encode(new CursorCodec.Cursor(last.getCreatedAt(), last.getId()));
        }
        return CursorPage.of(trimmed.stream().map(TeacherService::toResponse).toList(), nextCursor, limit);
    }

    @Transactional(readOnly = true)
    public TeacherResponse get(UUID schoolId, UUID id) {
        return toResponse(loadInSchool(schoolId, id));
    }

    @Transactional
    public TeacherResponse update(UUID schoolId, UUID id, UpdateTeacherRequest req, UUID actorUserId) {
        Teacher t = loadInSchool(schoolId, id);
        if (req.firstName() != null)  t.setFirstName(req.firstName());
        if (req.lastName() != null)   t.setLastName(req.lastName());
        if (req.employeeId() != null) t.setEmployeeId(req.employeeId());
        try {
            teachers.saveAndFlush(t);
        } catch (DataIntegrityViolationException e) {
            throw new Errors.Conflict("A teacher with that employee_id already exists in this school");
        }
        log.info("teacher updated id={} school={} actor={}", id, schoolId, actorUserId);
        return toResponse(t);
    }

    @Transactional
    public void softDelete(UUID schoolId, UUID id, UUID actorUserId) {
        Teacher t = loadInSchool(schoolId, id);
        t.softDelete(Instant.now(clock));
        teachers.save(t);
        log.info("teacher soft-deleted id={} school={} actor={}", id, schoolId, actorUserId);
    }

    private Teacher loadInSchool(UUID schoolId, UUID id) {
        Teacher t = teachers.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new Errors.NotFound("teacher", id));
        // Cross-school access leaks nothing: same NotFound, not Forbidden.
        if (!t.getSchoolId().equals(schoolId)) {
            throw new Errors.NotFound("teacher", id);
        }
        return t;
    }

    private static int clampLimit(Integer requested) {
        if (requested == null) return DEFAULT_LIMIT;
        if (requested < 1)     throw new Errors.BadRequest("limit must be >= 1");
        return Math.min(requested, MAX_LIMIT);
    }

    private static TeacherResponse toResponse(Teacher t) {
        return new TeacherResponse(
                t.getId(), t.getSchoolId(), t.getUserId(),
                t.getFirstName(), t.getLastName(), t.getEmployeeId(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
