package com.example.photoapp.web.controller;

import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.enrolment.StudentEnrolmentService;
import com.example.photoapp.web.dto.EnrolmentDtos.EnrolStudentRequest;
import com.example.photoapp.web.dto.EnrolmentDtos.EnrolmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/classes/{classId}/students")
@PreAuthorize("hasRole('ADMIN')")
public class ClassEnrolmentController {

    private final StudentEnrolmentService enrolment;
    private final SchoolContext schoolContext;

    public ClassEnrolmentController(StudentEnrolmentService enrolment, SchoolContext schoolContext) {
        this.enrolment = enrolment;
        this.schoolContext = schoolContext;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnrolmentResponse enrol(@PathVariable UUID classId,
                                    @Valid @RequestBody EnrolStudentRequest req) {
        Principal me = schoolContext.requirePrincipal();
        return enrolment.enrol(me.schoolId(), classId, req.studentId(), me.userId());
    }

    @GetMapping
    public List<EnrolmentResponse> list(@PathVariable UUID classId) {
        return enrolment.listActiveByClass(schoolContext.requireSchoolId(), classId);
    }

    @DeleteMapping("/{studentId}")
    public ResponseEntity<Void> unenrol(@PathVariable UUID classId, @PathVariable UUID studentId) {
        Principal me = schoolContext.requirePrincipal();
        enrolment.unenrol(me.schoolId(), classId, studentId, me.userId());
        return ResponseEntity.noContent().build();
    }
}
