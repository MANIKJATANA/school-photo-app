package com.example.photoapp.web.controller;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.pagination.CursorPage;
import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.photo.PhotoQueryService;
import com.example.photoapp.web.dto.PhotoDtos.PhotoListItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class EventPhotoControllerTest {

    private static final UUID SCHOOL = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID ACTOR  = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID EVENT  = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final UUID PHOTO  = UUID.fromString("01900000-0000-7000-8000-000000000004");

    private PhotoQueryService service;
    private SchoolContext schoolContext;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        service = mock(PhotoQueryService.class);
        schoolContext = mock(SchoolContext.class);
        when(schoolContext.requirePrincipal()).thenReturn(new Principal(ACTOR, SCHOOL, Role.ADMIN));
        when(schoolContext.requireSchoolId()).thenReturn(SCHOOL);

        EventPhotoController controller = new EventPhotoController(service, schoolContext);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new Errors.GlobalHandler())
                .build();
    }

    @Test
    void list_returns_page_with_items_and_cursor() throws Exception {
        PhotoListItem item = sampleItem();
        when(service.listByEvent(eq(SCHOOL), eq(EVENT), eq("c0"), eq(50)))
                .thenReturn(CursorPage.of(List.of(item), "cursor.next", 50));

        MvcResult r = mvc.perform(get("/api/v1/events/" + EVENT + "/photos")
                        .param("cursor", "c0").param("limit", "50"))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("items").size()).isEqualTo(1);
        assertThat(body.get("items").get(0).get("getUrl").asText()).startsWith("https://");
        assertThat(body.get("nextCursor").asText()).isEqualTo("cursor.next");
    }

    @Test
    void list_passes_through_404_for_cross_school_event() throws Exception {
        when(service.listByEvent(eq(SCHOOL), eq(EVENT), eq(null), eq(null)))
                .thenThrow(new Errors.NotFound("event", EVENT));

        MvcResult r = mvc.perform(get("/api/v1/events/" + EVENT + "/photos")).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void list_returns_400_on_negative_limit() throws Exception {
        when(service.listByEvent(eq(SCHOOL), eq(EVENT), eq(null), eq(-1)))
                .thenThrow(new Errors.BadRequest("limit must be >= 1"));

        MvcResult r = mvc.perform(get("/api/v1/events/" + EVENT + "/photos")
                        .param("limit", "-1"))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    private static PhotoListItem sampleItem() {
        return new PhotoListItem(
                PHOTO, EVENT,
                "image/jpeg", 2048L, null, null,
                null,
                URI.create("https://s3.example/key?sig=abc"),
                Instant.parse("2030-01-01T00:05:00Z"),
                Instant.parse("2030-01-01T00:00:00Z"));
    }
}
