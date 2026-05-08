package com.example.photoapp.web.controller;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.domain.photo.MlStatus;
import com.example.photoapp.domain.photo.UploadStatus;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.photo.PhotoUploadService;
import com.example.photoapp.web.dto.PhotoDtos.InitiateUploadRequest;
import com.example.photoapp.web.dto.PhotoDtos.InitiateUploadResponse;
import com.example.photoapp.web.dto.PhotoDtos.PhotoResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class PhotoControllerTest {

    private static final UUID SCHOOL = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID ACTOR  = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID EVENT  = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final UUID PHOTO  = UUID.fromString("01900000-0000-7000-8000-000000000004");

    private PhotoUploadService service;
    private SchoolContext schoolContext;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private com.example.photoapp.service.photo.PhotoQueryService queries;

    @BeforeEach
    void setUp() {
        service = mock(PhotoUploadService.class);
        queries = mock(com.example.photoapp.service.photo.PhotoQueryService.class);
        schoolContext = mock(SchoolContext.class);
        when(schoolContext.requirePrincipal()).thenReturn(new Principal(ACTOR, SCHOOL, Role.ADMIN));
        when(schoolContext.requireSchoolId()).thenReturn(SCHOOL);

        PhotoController controller = new PhotoController(service, queries, schoolContext);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new Errors.GlobalHandler())
                .build();
    }

    @Test
    void initiate_returns_201_with_put_url() throws Exception {
        InitiateUploadResponse resp = new InitiateUploadResponse(
                PHOTO, EVENT, URI.create("https://s3.example/key?sig=abc"),
                Instant.parse("2030-01-01T00:10:00Z"));
        when(service.initiate(eq(SCHOOL), eq(ACTOR), any())).thenReturn(resp);

        MvcResult r = mvc.perform(post("/api/v1/photos/initiate-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new InitiateUploadRequest(EVENT, "image/jpeg", 1024L))))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("photoId").asText()).isEqualTo(PHOTO.toString());
        assertThat(body.get("putUrl").asText()).startsWith("https://");
    }

    @Test
    void initiate_returns_400_on_validation_failure() throws Exception {
        // Missing eventId.
        String bad = "{\"contentType\":\"image/jpeg\",\"sizeBytes\":1024}";
        MvcResult r = mvc.perform(post("/api/v1/photos/initiate-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void initiate_returns_400_on_negative_size() throws Exception {
        String bad = json.writeValueAsString(new InitiateUploadRequest(EVENT, "image/jpeg", -1L));
        MvcResult r = mvc.perform(post("/api/v1/photos/initiate-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void initiate_passes_through_404_from_service() throws Exception {
        when(service.initiate(any(), any(), any()))
                .thenThrow(new Errors.NotFound("event", EVENT));

        MvcResult r = mvc.perform(post("/api/v1/photos/initiate-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                new InitiateUploadRequest(EVENT, "image/jpeg", 1024L))))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void confirm_returns_200_with_photo_response() throws Exception {
        PhotoResponse resp = new PhotoResponse(
                PHOTO, EVENT, SCHOOL, "image/jpeg", 2048L,
                null, null, null,
                UploadStatus.UPLOADED, MlStatus.PENDING,
                Instant.parse("2030-01-01T00:00:00Z"),
                Instant.parse("2030-01-01T00:01:00Z"));
        when(service.confirm(SCHOOL, ACTOR, PHOTO)).thenReturn(resp);

        MvcResult r = mvc.perform(post("/api/v1/photos/" + PHOTO + "/confirm-upload")).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("uploadStatus").asText()).isEqualTo("UPLOADED");
        assertThat(body.get("sizeBytes").asLong()).isEqualTo(2048L);
    }

    @Test
    void confirm_returns_404_when_blob_missing() throws Exception {
        when(service.confirm(SCHOOL, ACTOR, PHOTO))
                .thenThrow(new Errors.NotFound("Blob not found"));

        MvcResult r = mvc.perform(post("/api/v1/photos/" + PHOTO + "/confirm-upload")).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void get_url_returns_200_with_presigned_url() throws Exception {
        when(queries.getPresignedUrl(SCHOOL, PHOTO))
                .thenReturn(new com.example.photoapp.web.dto.PhotoDtos.PhotoUrlResponse(
                        URI.create("https://s3.example/key?sig=abc"),
                        Instant.parse("2030-01-01T00:05:00Z")));

        MvcResult r = mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/v1/photos/" + PHOTO + "/url")).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("url").asText()).startsWith("https://");
        assertThat(body.get("expiresAt").asText()).isNotBlank();
    }

    @Test
    void get_url_returns_404_on_miss() throws Exception {
        when(queries.getPresignedUrl(SCHOOL, PHOTO))
                .thenThrow(new Errors.NotFound("photo", PHOTO));

        MvcResult r = mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/v1/photos/" + PHOTO + "/url")).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }
}
