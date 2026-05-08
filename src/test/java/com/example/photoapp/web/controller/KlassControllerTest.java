package com.example.photoapp.web.controller;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.klass.KlassService;
import com.example.photoapp.web.dto.KlassDtos.ClassResponse;
import com.example.photoapp.web.dto.KlassDtos.CreateClassRequest;
import com.example.photoapp.web.dto.KlassDtos.UpdateClassRequest;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class KlassControllerTest {

    private static final UUID SCHOOL = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID ADMIN  = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID CLASS_ID = UUID.fromString("01900000-0000-7000-8000-000000000003");

    private KlassService service;
    private SchoolContext schoolContext;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        service = mock(KlassService.class);
        schoolContext = mock(SchoolContext.class);
        Principal me = new Principal(ADMIN, SCHOOL, Role.ADMIN);
        when(schoolContext.requirePrincipal()).thenReturn(me);
        when(schoolContext.requireSchoolId()).thenReturn(SCHOOL);

        KlassController controller = new KlassController(service, schoolContext);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new Errors.GlobalHandler())
                .build();
    }

    @Test
    void post_creates_class_and_returns_201() throws Exception {
        when(service.create(eq(SCHOOL), eq(ADMIN), any())).thenReturn(sampleResponse());

        MvcResult r = mvc.perform(post("/api/v1/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validCreate())))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("id").asText()).isEqualTo(CLASS_ID.toString());
        assertThat(body.get("name").asText()).isEqualTo("Grade 5 - A");
    }

    @Test
    void post_returns_400_on_validation_failure() throws Exception {
        // Blank name.
        String bad = """
                {"name": "", "academicYear": "2025-2026"}
                """;
        MvcResult r = mvc.perform(post("/api/v1/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void list_returns_page_with_items_and_next_cursor() throws Exception {
        CursorPage<ClassResponse> page = CursorPage.of(List.of(sampleResponse()), "cursor.next", 50);
        when(service.list(eq(SCHOOL), eq("c0"), eq(50))).thenReturn(page);

        MvcResult r = mvc.perform(get("/api/v1/classes").param("cursor", "c0").param("limit", "50"))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("items").size()).isEqualTo(1);
        assertThat(body.get("nextCursor").asText()).isEqualTo("cursor.next");
    }

    @Test
    void get_by_id_returns_200_on_hit() throws Exception {
        when(service.get(SCHOOL, CLASS_ID)).thenReturn(sampleResponse());

        MvcResult r = mvc.perform(get("/api/v1/classes/" + CLASS_ID)).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void get_by_id_returns_404_on_miss() throws Exception {
        when(service.get(SCHOOL, CLASS_ID))
                .thenThrow(new Errors.NotFound("class", CLASS_ID));

        MvcResult r = mvc.perform(get("/api/v1/classes/" + CLASS_ID)).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void patch_returns_updated_response() throws Exception {
        when(service.update(eq(SCHOOL), eq(CLASS_ID), any(), eq(ADMIN)))
                .thenReturn(sampleResponse());

        MvcResult r = mvc.perform(patch("/api/v1/classes/" + CLASS_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new UpdateClassRequest("Renamed", null))))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void delete_returns_204() throws Exception {
        MvcResult r = mvc.perform(delete("/api/v1/classes/" + CLASS_ID)).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(204);
        verify(service).softDelete(SCHOOL, CLASS_ID, ADMIN);
    }

    private static ClassResponse sampleResponse() {
        return new ClassResponse(
                CLASS_ID, SCHOOL, "Grade 5 - A", "2025-2026",
                Instant.parse("2030-01-01T00:00:00Z"),
                Instant.parse("2030-01-02T00:00:00Z"));
    }

    private static CreateClassRequest validCreate() {
        return new CreateClassRequest("Grade 5 - A", "2025-2026");
    }
}
