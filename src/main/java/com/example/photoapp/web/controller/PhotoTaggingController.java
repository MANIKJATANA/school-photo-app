package com.example.photoapp.web.controller;

import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.tagging.TaggingService;
import com.example.photoapp.web.dto.TaggingDtos.AddTagRequest;
import com.example.photoapp.web.dto.TaggingDtos.TagResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/photos/{photoId}/tags")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class PhotoTaggingController {

    private final TaggingService tagging;
    private final SchoolContext schoolContext;

    public PhotoTaggingController(TaggingService tagging, SchoolContext schoolContext) {
        this.tagging = tagging;
        this.schoolContext = schoolContext;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TagResponse addTag(@PathVariable UUID photoId, @Valid @RequestBody AddTagRequest req) {
        Principal me = schoolContext.requirePrincipal();
        return tagging.addTag(me.schoolId(), me.userId(), photoId, req.studentId(), req.confidence());
    }

    @DeleteMapping("/{studentId}")
    public ResponseEntity<Void> removeTag(@PathVariable UUID photoId, @PathVariable UUID studentId) {
        Principal me = schoolContext.requirePrincipal();
        tagging.removeTag(me.schoolId(), me.userId(), photoId, studentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{studentId}/confirm")
    public TagResponse confirmTag(@PathVariable UUID photoId,
                                   @PathVariable UUID studentId,
                                   @RequestParam(defaultValue = "true") boolean confirmed) {
        Principal me = schoolContext.requirePrincipal();
        return tagging.confirmTag(me.schoolId(), me.userId(), photoId, studentId, confirmed);
    }
}
