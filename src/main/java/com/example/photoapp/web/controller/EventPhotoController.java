package com.example.photoapp.web.controller;

import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.service.photo.PhotoQueryService;
import com.example.photoapp.web.dto.PhotoDtos.PhotoListItem;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/photos")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class EventPhotoController {

    private final PhotoQueryService queries;
    private final SchoolContext schoolContext;

    public EventPhotoController(PhotoQueryService queries, SchoolContext schoolContext) {
        this.queries = queries;
        this.schoolContext = schoolContext;
    }

    @GetMapping
    public CursorPage<PhotoListItem> list(
            @PathVariable UUID eventId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return queries.listByEvent(schoolContext.requireSchoolId(), eventId, cursor, limit);
    }
}
