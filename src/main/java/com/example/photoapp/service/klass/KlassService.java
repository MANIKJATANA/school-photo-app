package com.example.photoapp.service.klass;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.common.pagination.CursorPaginator;
import com.example.photoapp.common.school.SchoolScopes;
import com.example.photoapp.domain.klass.Klass;
import com.example.photoapp.repository.klass.KlassRepository;
import com.example.photoapp.web.dto.KlassDtos.ClassResponse;
import com.example.photoapp.web.dto.KlassDtos.CreateClassRequest;
import com.example.photoapp.web.dto.KlassDtos.UpdateClassRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class KlassService {

    private static final Logger log = LoggerFactory.getLogger(KlassService.class);

    private final KlassRepository classes;
    private final CursorPaginator paginator;
    private final Clock clock;

    public KlassService(KlassRepository classes, CursorPaginator paginator, Clock clock) {
        this.classes = classes;
        this.paginator = paginator;
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
        return paginator.paginate(
                classes,
                SchoolScopes.activeInSchool(schoolId),
                cursor, requestedLimit,
                KLASS_KEYS,
                KlassService::toResponse);
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

    private static final CursorPaginator.RowKeys<Klass> KLASS_KEYS = new CursorPaginator.RowKeys<>() {
        @Override public Instant createdAt(Klass k) { return k.getCreatedAt(); }
        @Override public UUID id(Klass k)            { return k.getId(); }
    };

    private static ClassResponse toResponse(Klass k) {
        return new ClassResponse(
                k.getId(), k.getSchoolId(), k.getName(), k.getAcademicYear(),
                k.getCreatedAt(), k.getUpdatedAt());
    }
}
