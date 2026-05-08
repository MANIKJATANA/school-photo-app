package com.example.photoapp.web.controller;

import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.photo.PhotoUploadService;
import com.example.photoapp.web.dto.PhotoDtos.InitiateUploadRequest;
import com.example.photoapp.web.dto.PhotoDtos.InitiateUploadResponse;
import com.example.photoapp.web.dto.PhotoDtos.PhotoResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/photos")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class PhotoController {

    private final PhotoUploadService uploads;
    private final SchoolContext schoolContext;

    public PhotoController(PhotoUploadService uploads, SchoolContext schoolContext) {
        this.uploads = uploads;
        this.schoolContext = schoolContext;
    }

    @PostMapping("/initiate-upload")
    @ResponseStatus(HttpStatus.CREATED)
    public InitiateUploadResponse initiate(@Valid @RequestBody InitiateUploadRequest req) {
        Principal me = schoolContext.requirePrincipal();
        return uploads.initiate(me.schoolId(), me.userId(), req);
    }

    @PostMapping("/{id}/confirm-upload")
    public PhotoResponse confirm(@PathVariable UUID id) {
        Principal me = schoolContext.requirePrincipal();
        return uploads.confirm(me.schoolId(), me.userId(), id);
    }
}
