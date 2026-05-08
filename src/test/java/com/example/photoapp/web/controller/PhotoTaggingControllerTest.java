package com.example.photoapp.web.controller;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.tagging.TaggingService;
import com.example.photoapp.web.dto.TaggingDtos.AddTagRequest;
import com.example.photoapp.web.dto.TaggingDtos.TagResponse;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class PhotoTaggingControllerTest {

    private static final UUID SCHOOL  = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID ADMIN   = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID PHOTO   = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final UUID EVENT   = UUID.fromString("01900000-0000-7000-8000-000000000004");
    private static final UUID STUDENT = UUID.fromString("01900000-0000-7000-8000-000000000005");

    private TaggingService tagging;
    private SchoolContext schoolContext;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        tagging = mock(TaggingService.class);
        schoolContext = mock(SchoolContext.class);
        when(schoolContext.requirePrincipal()).thenReturn(new Principal(ADMIN, SCHOOL, Role.ADMIN));

        PhotoTaggingController controller = new PhotoTaggingController(tagging, schoolContext);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new Errors.GlobalHandler())
                .build();
    }

    @Test
    void post_tag_returns_201() throws Exception {
        TagResponse resp = new TagResponse(PHOTO, STUDENT, EVENT, 1.0f, Boolean.TRUE,
                Instant.parse("2030-01-01T00:00:00Z"));
        when(tagging.addTag(eq(SCHOOL), eq(ADMIN), eq(PHOTO), eq(STUDENT), eq(1.0f))).thenReturn(resp);

        MvcResult r = mvc.perform(post("/api/v1/photos/" + PHOTO + "/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AddTagRequest(STUDENT, 1.0f))))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("studentId").asText()).isEqualTo(STUDENT.toString());
    }

    @Test
    void post_tag_returns_400_on_missing_studentId() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/photos/" + PHOTO + "/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confidence\":1.0}"))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void post_tag_passes_through_404() throws Exception {
        when(tagging.addTag(any(), any(), any(), any(), any()))
                .thenThrow(new Errors.NotFound("photo", PHOTO));

        MvcResult r = mvc.perform(post("/api/v1/photos/" + PHOTO + "/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AddTagRequest(STUDENT, 1.0f))))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void delete_tag_returns_204() throws Exception {
        MvcResult r = mvc.perform(delete("/api/v1/photos/" + PHOTO + "/tags/" + STUDENT)).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(204);
        verify(tagging).removeTag(SCHOOL, ADMIN, PHOTO, STUDENT);
    }

    @Test
    void confirm_returns_200() throws Exception {
        TagResponse resp = new TagResponse(PHOTO, STUDENT, EVENT, 1.0f, Boolean.TRUE,
                Instant.parse("2030-01-01T00:00:00Z"));
        when(tagging.confirmTag(eq(SCHOOL), eq(ADMIN), eq(PHOTO), eq(STUDENT), anyBoolean())).thenReturn(resp);

        MvcResult r = mvc.perform(post("/api/v1/photos/" + PHOTO + "/tags/" + STUDENT + "/confirm")
                        .param("confirmed", "true"))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }
}
