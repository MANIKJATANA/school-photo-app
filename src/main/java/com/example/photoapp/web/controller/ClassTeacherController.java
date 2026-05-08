package com.example.photoapp.web.controller;

import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.enrolment.ClassTeacherService;
import com.example.photoapp.web.dto.ClassTeacherDtos.AssignTeacherRequest;
import com.example.photoapp.web.dto.ClassTeacherDtos.AssignmentResponse;
import com.example.photoapp.web.dto.ClassTeacherDtos.UpdateAssignmentRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/classes/{classId}/teachers")
@PreAuthorize("hasRole('ADMIN')")
public class ClassTeacherController {

    private final ClassTeacherService assignments;
    private final SchoolContext schoolContext;

    public ClassTeacherController(ClassTeacherService assignments, SchoolContext schoolContext) {
        this.assignments = assignments;
        this.schoolContext = schoolContext;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse assign(@PathVariable UUID classId,
                                      @Valid @RequestBody AssignTeacherRequest req) {
        Principal me = schoolContext.requirePrincipal();
        return assignments.assign(me.schoolId(), classId, req.teacherId(), req.role(), me.userId());
    }

    @GetMapping
    public List<AssignmentResponse> list(@PathVariable UUID classId) {
        return assignments.listByClass(schoolContext.requireSchoolId(), classId);
    }

    @PatchMapping("/{teacherId}")
    public AssignmentResponse updateRole(@PathVariable UUID classId,
                                          @PathVariable UUID teacherId,
                                          @Valid @RequestBody UpdateAssignmentRequest req) {
        Principal me = schoolContext.requirePrincipal();
        return assignments.updateRole(me.schoolId(), classId, teacherId, req.role(), me.userId());
    }

    @DeleteMapping("/{teacherId}")
    public ResponseEntity<Void> remove(@PathVariable UUID classId, @PathVariable UUID teacherId) {
        Principal me = schoolContext.requirePrincipal();
        assignments.remove(me.schoolId(), classId, teacherId, me.userId());
        return ResponseEntity.noContent().build();
    }
}
