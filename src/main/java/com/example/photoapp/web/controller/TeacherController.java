package com.example.photoapp.web.controller;

import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.teacher.TeacherService;
import com.example.photoapp.web.dto.TeacherDtos.CreateTeacherRequest;
import com.example.photoapp.web.dto.TeacherDtos.TeacherResponse;
import com.example.photoapp.web.dto.TeacherDtos.UpdateTeacherRequest;
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

@RestController
@RequestMapping("/api/v1/teachers")
@PreAuthorize("hasRole('ADMIN')")
public class TeacherController {

    private final TeacherService teachers;
    private final SchoolContext schoolContext;

    public TeacherController(TeacherService teachers, SchoolContext schoolContext) {
        this.teachers = teachers;
        this.schoolContext = schoolContext;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherResponse create(@Valid @RequestBody CreateTeacherRequest req) {
        Principal me = schoolContext.requirePrincipal();
        return teachers.create(me.schoolId(), me.userId(), req);
    }

    @GetMapping
    public CursorPage<TeacherResponse> list(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit",  required = false) Integer limit) {
        return teachers.list(schoolContext.requireSchoolId(), cursor, limit);
    }

    @GetMapping("/{id}")
    public TeacherResponse get(@PathVariable UUID id) {
        return teachers.get(schoolContext.requireSchoolId(), id);
    }

    @PatchMapping("/{id}")
    public TeacherResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateTeacherRequest req) {
        Principal me = schoolContext.requirePrincipal();
        return teachers.update(me.schoolId(), id, req, me.userId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        Principal me = schoolContext.requirePrincipal();
        teachers.softDelete(me.schoolId(), id, me.userId());
        return ResponseEntity.noContent().build();
    }
}
