package com.example.photoapp.web.controller;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.enrolment.StudentEnrolmentService;
import com.example.photoapp.web.dto.EnrolmentDtos.EnrolStudentRequest;
import com.example.photoapp.web.dto.EnrolmentDtos.EnrolmentResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class ClassEnrolmentControllerTest {

    private static final UUID SCHOOL  = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID ADMIN   = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID CLASS_ID  = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final UUID STUDENT_ID = UUID.fromString("01900000-0000-7000-8000-000000000004");
    private static final UUID ENROL_ID = UUID.fromString("01900000-0000-7000-8000-000000000005");

    private StudentEnrolmentService service;
    private SchoolContext schoolContext;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        service = mock(StudentEnrolmentService.class);
        schoolContext = mock(SchoolContext.class);
        Principal me = new Principal(ADMIN, SCHOOL, Role.ADMIN);
        when(schoolContext.requirePrincipal()).thenReturn(me);
        when(schoolContext.requireSchoolId()).thenReturn(SCHOOL);

        ClassEnrolmentController controller = new ClassEnrolmentController(service, schoolContext);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new Errors.GlobalHandler())
                .build();
    }

    @Test
    void post_enrols_student_returns_201() throws Exception {
        when(service.enrol(eq(SCHOOL), eq(CLASS_ID), eq(STUDENT_ID), eq(ADMIN)))
                .thenReturn(sampleResponse());

        MvcResult r = mvc.perform(post("/api/v1/classes/" + CLASS_ID + "/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new EnrolStudentRequest(STUDENT_ID))))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("studentId").asText()).isEqualTo(STUDENT_ID.toString());
        assertThat(body.get("classId").asText()).isEqualTo(CLASS_ID.toString());
    }

    @Test
    void post_returns_400_when_studentId_missing() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/classes/" + CLASS_ID + "/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void post_returns_404_when_class_or_student_cross_school() throws Exception {
        when(service.enrol(eq(SCHOOL), eq(CLASS_ID), eq(STUDENT_ID), eq(ADMIN)))
                .thenThrow(new Errors.NotFound("class", CLASS_ID));

        MvcResult r = mvc.perform(post("/api/v1/classes/" + CLASS_ID + "/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new EnrolStudentRequest(STUDENT_ID))))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void list_returns_active_enrolments() throws Exception {
        when(service.listActiveByClass(SCHOOL, CLASS_ID)).thenReturn(List.of(sampleResponse()));

        MvcResult r = mvc.perform(get("/api/v1/classes/" + CLASS_ID + "/students")).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(1);
    }

    @Test
    void delete_unenrols_returns_204() throws Exception {
        MvcResult r = mvc.perform(delete("/api/v1/classes/" + CLASS_ID + "/students/" + STUDENT_ID))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(204);
        verify(service).unenrol(SCHOOL, CLASS_ID, STUDENT_ID, ADMIN);
    }

    @Test
    void delete_returns_404_when_no_active_enrolment() throws Exception {
        org.mockito.Mockito.doThrow(new Errors.NotFound("active enrolment for student"))
                .when(service).unenrol(eq(SCHOOL), eq(CLASS_ID), eq(STUDENT_ID), eq(ADMIN));

        MvcResult r = mvc.perform(delete("/api/v1/classes/" + CLASS_ID + "/students/" + STUDENT_ID))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }

    private static EnrolmentResponse sampleResponse() {
        return new EnrolmentResponse(ENROL_ID, STUDENT_ID, CLASS_ID,
                LocalDate.parse("2030-06-01"), null);
    }
}
