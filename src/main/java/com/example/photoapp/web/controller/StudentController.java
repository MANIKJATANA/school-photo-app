package com.example.photoapp.web.controller;

import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.student.StudentService;
import com.example.photoapp.web.dto.StudentDtos.CreateStudentRequest;
import com.example.photoapp.web.dto.StudentDtos.StudentResponse;
import com.example.photoapp.web.dto.StudentDtos.UpdateStudentRequest;
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
@RequestMapping("/api/v1/students")
@PreAuthorize("hasRole('ADMIN')")
public class StudentController {

    private final StudentService students;
    private final SchoolContext schoolContext;

    public StudentController(StudentService students, SchoolContext schoolContext) {
        this.students = students;
        this.schoolContext = schoolContext;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse create(@Valid @RequestBody CreateStudentRequest req) {
        Principal me = schoolContext.requirePrincipal();
        return students.create(me.schoolId(), me.userId(), req);
    }

    @GetMapping
    public CursorPage<StudentResponse> list(
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit",  required = false) Integer limit) {
        return students.list(schoolContext.requireSchoolId(), cursor, limit);
    }

    @GetMapping("/{id}")
    public StudentResponse get(@PathVariable UUID id) {
        return students.get(schoolContext.requireSchoolId(), id);
    }

    @PatchMapping("/{id}")
    public StudentResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateStudentRequest req) {
        Principal me = schoolContext.requirePrincipal();
        return students.update(me.schoolId(), id, req, me.userId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        Principal me = schoolContext.requirePrincipal();
        students.softDelete(me.schoolId(), id, me.userId());
        return ResponseEntity.noContent().build();
    }
}
