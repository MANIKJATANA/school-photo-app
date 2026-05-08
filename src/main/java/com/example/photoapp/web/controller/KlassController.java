package com.example.photoapp.web.controller;

import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.klass.KlassService;
import com.example.photoapp.web.dto.KlassDtos.ClassResponse;
import com.example.photoapp.web.dto.KlassDtos.CreateClassRequest;
import com.example.photoapp.web.dto.KlassDtos.UpdateClassRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * URL says {@code /classes} (the user word); the underlying entity / package is
 * {@code Klass} (the Java keyword dodge). The discrepancy is contained inside
 * the codebase.
 */
@RestController
@RequestMapping("/api/v1/classes")
@PreAuthorize("hasRole('ADMIN')")
public class KlassController {

    private final KlassService classes;
    private final SchoolContext schoolContext;

    public KlassController(KlassService classes, SchoolContext schoolContext) {
        this.classes = classes;
        this.schoolContext = schoolContext;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClassResponse create(@Valid @RequestBody CreateClassRequest req) {
        Principal me = schoolContext.requirePrincipal();
        return classes.create(me.schoolId(), me.userId(), req);
    }

    @GetMapping
    public CursorPage<ClassResponse> list(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit",  required = false) Integer limit) {
        return classes.list(schoolContext.requireSchoolId(), cursor, limit);
    }

    @GetMapping("/{id}")
    public ClassResponse get(@PathVariable UUID id) {
        return classes.get(schoolContext.requireSchoolId(), id);
    }

    @PatchMapping("/{id}")
    public ClassResponse update(@PathVariable UUID id,
                                 @Valid @RequestBody UpdateClassRequest req) {
        Principal me = schoolContext.requirePrincipal();
        return classes.update(me.schoolId(), id, req, me.userId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        Principal me = schoolContext.requirePrincipal();
        classes.softDelete(me.schoolId(), id, me.userId());
        return ResponseEntity.noContent().build();
    }
}
