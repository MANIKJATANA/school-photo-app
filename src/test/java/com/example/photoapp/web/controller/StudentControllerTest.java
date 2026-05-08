package com.example.photoapp.web.controller;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.domain.student.FaceEmbeddingStatus;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.student.StudentService;
import com.example.photoapp.web.dto.StudentDtos.CreateStudentRequest;
import com.example.photoapp.web.dto.StudentDtos.StudentResponse;
import com.example.photoapp.web.dto.StudentDtos.UpdateStudentRequest;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class StudentControllerTest {

    private static final UUID SCHOOL = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID ADMIN  = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID STUDENT_ID = UUID.fromString("01900000-0000-7000-8000-000000000003");

    private StudentService service;
    private SchoolContext schoolContext;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        service = mock(StudentService.class);
        schoolContext = mock(SchoolContext.class);
        Principal me = new Principal(ADMIN, SCHOOL, Role.ADMIN);
        when(schoolContext.requirePrincipal()).thenReturn(me);
        when(schoolContext.requireSchoolId()).thenReturn(SCHOOL);

        StudentController controller = new StudentController(service, schoolContext);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new Errors.GlobalHandler())
                .build();
    }

    @Test
    void post_creates_student_and_returns_201() throws Exception {
        when(service.create(eq(SCHOOL), eq(ADMIN), any())).thenReturn(sampleResponse());

        MvcResult r = mvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validCreate())))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("id").asText()).isEqualTo(STUDENT_ID.toString());
        assertThat(body.get("firstName").asText()).isEqualTo("Alice");
    }

    @Test
    void post_returns_400_on_validation_failure() throws Exception {
        // Password too short.
        String bad = """
                {"firstName": "A", "lastName": "B", "email": "x@y.z", "password": "short"}
                """;
        MvcResult r = mvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("validation_failed");
    }

    @Test
    void list_returns_page_with_items_and_next_cursor() throws Exception {
        CursorPage<StudentResponse> page = CursorPage.of(List.of(sampleResponse()), "cursor.next", 50);
        when(service.list(eq(SCHOOL), eq("c0"), eq(50))).thenReturn(page);

        MvcResult r = mvc.perform(get("/api/v1/students").param("cursor", "c0").param("limit", "50"))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("items").isArray()).isTrue();
        assertThat(body.get("items").size()).isEqualTo(1);
        assertThat(body.get("nextCursor").asText()).isEqualTo("cursor.next");
    }

    @Test
    void get_by_id_returns_200_on_hit() throws Exception {
        when(service.get(SCHOOL, STUDENT_ID)).thenReturn(sampleResponse());

        MvcResult r = mvc.perform(get("/api/v1/students/" + STUDENT_ID)).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void get_by_id_returns_404_on_miss() throws Exception {
        when(service.get(SCHOOL, STUDENT_ID)).thenThrow(new Errors.NotFound("student", STUDENT_ID));

        MvcResult r = mvc.perform(get("/api/v1/students/" + STUDENT_ID)).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void patch_returns_updated_response() throws Exception {
        when(service.update(eq(SCHOOL), eq(STUDENT_ID), any(), eq(ADMIN)))
                .thenReturn(sampleResponse());

        MvcResult r = mvc.perform(patch("/api/v1/students/" + STUDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new UpdateStudentRequest("Alicia", null, null, null))))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void delete_returns_204() throws Exception {
        MvcResult r = mvc.perform(delete("/api/v1/students/" + STUDENT_ID)).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(204);
        verify(service).softDelete(SCHOOL, STUDENT_ID, ADMIN);
    }

    private static StudentResponse sampleResponse() {
        return new StudentResponse(
                STUDENT_ID, SCHOOL, UUID.randomUUID(),
                "Alice", "Liddell", "R-001",
                LocalDate.parse("2010-01-15"),
                FaceEmbeddingStatus.PENDING,
                Instant.parse("2030-01-01T00:00:00Z"),
                Instant.parse("2030-01-02T00:00:00Z"));
    }

    private static CreateStudentRequest validCreate() {
        return new CreateStudentRequest(
                "Alice", "Liddell",
                "alice@example.com", "passw0rd-strong",
                "R-001", LocalDate.parse("2010-01-15"), "555-0100");
    }
}
