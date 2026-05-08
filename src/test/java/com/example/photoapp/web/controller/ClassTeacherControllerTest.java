package com.example.photoapp.web.controller;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.domain.enrolment.TeachingRole;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.enrolment.ClassTeacherService;
import com.example.photoapp.web.dto.ClassTeacherDtos.AssignTeacherRequest;
import com.example.photoapp.web.dto.ClassTeacherDtos.AssignmentResponse;
import com.example.photoapp.web.dto.ClassTeacherDtos.UpdateAssignmentRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class ClassTeacherControllerTest {

    private static final UUID SCHOOL    = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID ADMIN     = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID CLASS_ID  = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final UUID TEACHER_ID = UUID.fromString("01900000-0000-7000-8000-000000000004");

    private ClassTeacherService service;
    private SchoolContext schoolContext;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        service = mock(ClassTeacherService.class);
        schoolContext = mock(SchoolContext.class);
        Principal me = new Principal(ADMIN, SCHOOL, Role.ADMIN);
        when(schoolContext.requirePrincipal()).thenReturn(me);
        when(schoolContext.requireSchoolId()).thenReturn(SCHOOL);

        ClassTeacherController controller = new ClassTeacherController(service, schoolContext);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new Errors.GlobalHandler())
                .build();
    }

    @Test
    void post_assigns_teacher_returns_201() throws Exception {
        when(service.assign(eq(SCHOOL), eq(CLASS_ID), eq(TEACHER_ID),
                eq(TeachingRole.CLASS_TEACHER), eq(ADMIN)))
                .thenReturn(sampleResponse(TeachingRole.CLASS_TEACHER));

        MvcResult r = mvc.perform(post("/api/v1/classes/" + CLASS_ID + "/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new AssignTeacherRequest(TEACHER_ID, TeachingRole.CLASS_TEACHER))))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("teacherId").asText()).isEqualTo(TEACHER_ID.toString());
        assertThat(body.get("role").asText()).isEqualTo("CLASS_TEACHER");
    }

    @Test
    void post_returns_400_when_role_missing() throws Exception {
        String bad = "{\"teacherId\":\"" + TEACHER_ID + "\"}";
        MvcResult r = mvc.perform(post("/api/v1/classes/" + CLASS_ID + "/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void post_returns_400_on_unknown_role() throws Exception {
        // Jackson rejects unknown enum at parse time → handled by the malformed-request branch.
        String bad = "{\"teacherId\":\"" + TEACHER_ID + "\",\"role\":\"NOT_A_ROLE\"}";
        MvcResult r = mvc.perform(post("/api/v1/classes/" + CLASS_ID + "/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void post_returns_409_when_already_assigned() throws Exception {
        when(service.assign(any(), any(), any(), any(), any()))
                .thenThrow(new Errors.Conflict("already assigned"));

        MvcResult r = mvc.perform(post("/api/v1/classes/" + CLASS_ID + "/teachers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new AssignTeacherRequest(TEACHER_ID, TeachingRole.TEACHER))))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void list_returns_assignments() throws Exception {
        when(service.listByClass(SCHOOL, CLASS_ID))
                .thenReturn(List.of(sampleResponse(TeachingRole.CLASS_TEACHER)));

        MvcResult r = mvc.perform(get("/api/v1/classes/" + CLASS_ID + "/teachers")).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(1);
    }

    @Test
    void patch_updates_role_returns_200() throws Exception {
        when(service.updateRole(eq(SCHOOL), eq(CLASS_ID), eq(TEACHER_ID),
                eq(TeachingRole.SUBJECT_TEACHER), eq(ADMIN)))
                .thenReturn(sampleResponse(TeachingRole.SUBJECT_TEACHER));

        MvcResult r = mvc.perform(patch("/api/v1/classes/" + CLASS_ID + "/teachers/" + TEACHER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new UpdateAssignmentRequest(TeachingRole.SUBJECT_TEACHER))))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("role").asText()).isEqualTo("SUBJECT_TEACHER");
    }

    @Test
    void delete_removes_assignment_returns_204() throws Exception {
        MvcResult r = mvc.perform(delete("/api/v1/classes/" + CLASS_ID + "/teachers/" + TEACHER_ID))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(204);
        verify(service).remove(SCHOOL, CLASS_ID, TEACHER_ID, ADMIN);
    }

    @Test
    void delete_returns_404_when_no_assignment() throws Exception {
        doThrow(new Errors.NotFound("assignment"))
                .when(service).remove(eq(SCHOOL), eq(CLASS_ID), eq(TEACHER_ID), eq(ADMIN));

        MvcResult r = mvc.perform(delete("/api/v1/classes/" + CLASS_ID + "/teachers/" + TEACHER_ID))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }

    private static AssignmentResponse sampleResponse(TeachingRole role) {
        return new AssignmentResponse(CLASS_ID, TEACHER_ID, role,
                Instant.parse("2030-01-01T00:00:00Z"));
    }
}
