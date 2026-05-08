package com.example.photoapp.service.klass;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.pagination.CursorCodec;
import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.common.school.SchoolScopes;
import com.example.photoapp.domain.klass.Klass;
import com.example.photoapp.repository.klass.KlassRepository;
import com.example.photoapp.web.dto.KlassDtos.ClassResponse;
import com.example.photoapp.web.dto.KlassDtos.CreateClassRequest;
import com.example.photoapp.web.dto.KlassDtos.UpdateClassRequest;
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
public class KlassService {

    private static final Logger log = LoggerFactory.getLogger(KlassService.class);
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    private final KlassRepository classes;
    private final CursorCodec cursorCodec;
    private final Clock clock;

    public KlassService(KlassRepository classes, CursorCodec cursorCodec, Clock clock) {
        this.classes = classes;
        this.cursorCodec = cursorCodec;
        this.clock = clock;
    }

    @Transactional
    public ClassResponse create(UUID schoolId, UUID actorUserId, CreateClassRequest req) {
        Klass k = new Klass(schoolId, req.name(), req.academicYear());
        try {
            classes.saveAndFlush(k);
        } catch (DataIntegrityViolationException e) {
            // V1 UNIQUE (school_id, name, academic_year)
            throw new Errors.Conflict("A class with that name and year already exists in this school");
        }

        log.info("class created id={} school={} actor={}", k.getId(), schoolId, actorUserId);
        return toResponse(k);
    }

    @Transactional(readOnly = true)
    public CursorPage<ClassResponse> list(UUID schoolId, String cursor, Integer requestedLimit) {
        int limit = clampLimit(requestedLimit);
        CursorCodec.Cursor decoded = cursorCodec.decode(cursor);

        Specification<Klass> spec = SchoolScopes.<Klass>activeInSchool(schoolId);
        if (decoded != null) {
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.lessThan(root.get("createdAt"), decoded.sortKey()),
                    cb.and(
                            cb.equal(root.get("createdAt"), decoded.sortKey()),
                            cb.lessThan(root.get("id"), decoded.id()))));
        }

        var page = classes.findAll(
                spec,
                PageRequest.of(0, limit + 1,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        List<Klass> rows = page.getContent();
        boolean hasMore = rows.size() > limit;
        List<Klass> trimmed = hasMore ? rows.subList(0, limit) : rows;

        String nextCursor = null;
        if (hasMore) {
            Klass last = trimmed.get(trimmed.size() - 1);
            nextCursor = cursorCodec.encode(new CursorCodec.Cursor(last.getCreatedAt(), last.getId()));
        }
        return CursorPage.of(trimmed.stream().map(KlassService::toResponse).toList(), nextCursor, limit);
    }

    @Transactional(readOnly = true)
    public ClassResponse get(UUID schoolId, UUID id) {
        return toResponse(loadInSchool(schoolId, id));
    }

    @Transactional
    public ClassResponse update(UUID schoolId, UUID id, UpdateClassRequest req, UUID actorUserId) {
        Klass k = loadInSchool(schoolId, id);
        if (req.name() != null)         k.setName(req.name());
        if (req.academicYear() != null) k.setAcademicYear(req.academicYear());
        try {
            classes.saveAndFlush(k);
        } catch (DataIntegrityViolationException e) {
            throw new Errors.Conflict("A class with that name and year already exists in this school");
        }
        log.info("class updated id={} school={} actor={}", id, schoolId, actorUserId);
        return toResponse(k);
    }

    @Transactional
    public void softDelete(UUID schoolId, UUID id, UUID actorUserId) {
        Klass k = loadInSchool(schoolId, id);
        k.softDelete(Instant.now(clock));
        classes.save(k);
        log.info("class soft-deleted id={} school={} actor={}", id, schoolId, actorUserId);
    }

    private Klass loadInSchool(UUID schoolId, UUID id) {
        Klass k = classes.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new Errors.NotFound("class", id));
        // Cross-school access leaks nothing: same NotFound, not Forbidden.
        if (!k.getSchoolId().equals(schoolId)) {
            throw new Errors.NotFound("class", id);
        }
        return k;
    }

    private static int clampLimit(Integer requested) {
        if (requested == null) return DEFAULT_LIMIT;
        if (requested < 1)     throw new Errors.BadRequest("limit must be >= 1");
        return Math.min(requested, MAX_LIMIT);
    }

    private static ClassResponse toResponse(Klass k) {
        return new ClassResponse(
                k.getId(), k.getSchoolId(), k.getName(), k.getAcademicYear(),
                k.getCreatedAt(), k.getUpdatedAt());
    }
}
