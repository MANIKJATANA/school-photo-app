package com.example.photoapp.web.onboarding;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.service.onboarding.OnboardingService;
import com.example.photoapp.web.auth.AuthDtos.MeResponse;
import com.example.photoapp.web.auth.AuthDtos.TokenResponse;
import com.example.photoapp.web.onboarding.OnboardingDtos.AdminPart;
import com.example.photoapp.web.onboarding.OnboardingDtos.CreateSchoolRequest;
import com.example.photoapp.web.onboarding.OnboardingDtos.OnboardingResponse;
import com.example.photoapp.web.onboarding.OnboardingDtos.SchoolPart;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OnboardingControllerTest {

    private static final String VALID_KEY = "test-key";
    private static final UUID SCHOOL_ID = UUID.fromString("01900000-0000-7000-8000-000000000010");
    private static final UUID ADMIN_ID = UUID.fromString("01900000-0000-7000-8000-000000000011");
    private static final UUID EVENT_ID = UUID.fromString("01900000-0000-7000-8000-000000000012");

    private OnboardingService service;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = mock(OnboardingService.class);
        OnboardingController controller = new OnboardingController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new Errors.GlobalHandler())
                .build();
    }

    @Test
    void post_with_valid_key_and_body_returns_200_and_response() throws Exception {
        OnboardingResponse resp = new OnboardingResponse(
                SCHOOL_ID, ADMIN_ID, EVENT_ID,
                new TokenResponse(
                        "access.tok", Instant.parse("2030-01-01T00:15:00Z"),
                        "refresh.tok", Instant.parse("2030-01-15T00:00:00Z"),
                        new MeResponse(ADMIN_ID, SCHOOL_ID, Role.ADMIN)));
        when(service.bootstrapSchool(eq(VALID_KEY), any())).thenReturn(resp);

        MvcResult r = mvc.perform(post()
                        .header("X-Onboarding-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validBody())))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("schoolId").asText()).isEqualTo(SCHOOL_ID.toString());
        assertThat(body.get("adminUserId").asText()).isEqualTo(ADMIN_ID.toString());
        assertThat(body.get("defaultEventId").asText()).isEqualTo(EVENT_ID.toString());
        assertThat(body.get("tokens").get("accessToken").asText()).isEqualTo("access.tok");
    }

    @Test
    void missing_header_results_in_403_via_service() throws Exception {
        when(service.bootstrapSchool(eq(null), any())).thenThrow(new Errors.Forbidden("Missing onboarding key"));

        MvcResult r = mvc.perform(post()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validBody())))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("forbidden");
    }

    @Test
    void wrong_header_results_in_403_via_service() throws Exception {
        when(service.bootstrapSchool(eq("nope"), any())).thenThrow(new Errors.Forbidden("Invalid onboarding key"));

        MvcResult r = mvc.perform(post()
                        .header("X-Onboarding-Key", "nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(validBody())))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void validation_failure_returns_400_with_field_problems() throws Exception {
        // Password too short.
        String bad = """
                {
                  "school": {"name": "A"},
                  "admin": {"email": "x@y.z", "password": "short"}
                }
                """;
        MvcResult r = mvc.perform(post()
                        .header("X-Onboarding-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("validation_failed");
    }

    @Test
    void blank_school_name_returns_400() throws Exception {
        String bad = """
                {
                  "school": {"name": ""},
                  "admin": {"email": "a@b.c", "password": "longenough"}
                }
                """;
        MvcResult r = mvc.perform(post()
                        .header("X-Onboarding-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post() {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/onboarding");
    }

    private CreateSchoolRequest validBody() {
        return new CreateSchoolRequest(
                new SchoolPart("Test School", "1 Main St", "info@example.com"),
                new AdminPart("admin@example.com", "correct-horse-battery", "555-0100"));
    }
}
