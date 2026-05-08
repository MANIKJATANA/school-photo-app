package com.example.photoapp.web.controller;

import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.service.photo.StudentPhotoQueryService;
import com.example.photoapp.web.dto.PhotoDtos.PhotoListItem;
import com.example.photoapp.web.dto.StudentPhotoDtos.StudentEventResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Student-scoped photo views. Phase 3 keeps these gated to ADMIN+TEACHER;
 * Phase 4's AccessPolicy widens to STUDENT-can-see-self and per-class
 * TEACHER scoping.
 */
@RestController
@RequestMapping("/api/v1/students/{studentId}")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class StudentPhotoController {

    private final StudentPhotoQueryService queries;
    private final SchoolContext schoolContext;

    public StudentPhotoController(StudentPhotoQueryService queries, SchoolContext schoolContext) {
        this.queries = queries;
        this.schoolContext = schoolContext;
    }

    @GetMapping("/events")
    public List<StudentEventResponse> listEvents(@PathVariable UUID studentId) {
        return queries.listEventsForStudent(schoolContext.requireSchoolId(), studentId);
    }

    @GetMapping("/events/{eventId}/photos")
    public CursorPage<PhotoListItem> listPhotosInEvent(
            @PathVariable UUID studentId,
            @PathVariable UUID eventId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit",  required = false) Integer limit) {
        return queries.listPhotosForStudentInEvent(
                schoolContext.requireSchoolId(), studentId, eventId, cursor, limit);
    }
}
