package com.example.photoapp.web.auth;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.auth.AuthService;
import com.example.photoapp.web.auth.AuthDtos.LoginRequest;
import com.example.photoapp.web.auth.AuthDtos.MeResponse;
import com.example.photoapp.web.auth.AuthDtos.TokenResponse;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthControllerTest {

    private static final UUID SCHOOL_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("01900000-0000-7000-8000-000000000002");

    private AuthService auth;
    private SchoolContext schoolContext;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        auth = mock(AuthService.class);
        schoolContext = mock(SchoolContext.class);
        AuthController controller = new AuthController(auth, schoolContext);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new Errors.GlobalHandler())
                .build();
    }

    @Test
    void login_returns_token_response_on_success() throws Exception {
        TokenResponse resp = new TokenResponse(
                "access.tok", Instant.parse("2030-01-01T00:15:00Z"),
                "refresh.tok", Instant.parse("2030-01-15T00:00:00Z"),
                new MeResponse(USER_ID, SCHOOL_ID, Role.ADMIN));
        when(auth.login(any())).thenReturn(resp);

        MvcResult r = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest(SCHOOL_ID, "user@example.com", "password"))))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("accessToken").asText()).isEqualTo("access.tok");
        assertThat(body.get("user").get("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void login_returns_401_on_invalid_credentials() throws Exception {
        when(auth.login(any())).thenThrow(new Errors.Unauthorized("Invalid credentials"));

        MvcResult r = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginRequest(SCHOOL_ID, "user@example.com", "x"))))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("unauthorized");
    }

    @Test
    void login_returns_400_on_validation_failure() throws Exception {
        // Missing schoolId.
        String bad = "{\"email\":\"user@example.com\",\"password\":\"pw\"}";
        MvcResult r = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("validation_failed");
    }

    @Test
    void me_returns_current_principal() throws Exception {
        when(schoolContext.requirePrincipal())
                .thenReturn(new Principal(USER_ID, SCHOOL_ID, Role.TEACHER));

        MvcResult r = mvc.perform(get("/api/v1/auth/me")).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("userId").asText()).isEqualTo(USER_ID.toString());
        assertThat(body.get("schoolId").asText()).isEqualTo(SCHOOL_ID.toString());
        assertThat(body.get("role").asText()).isEqualTo("TEACHER");
    }

    @Test
    void me_returns_401_when_no_principal_in_context() throws Exception {
        when(schoolContext.requirePrincipal())
                .thenThrow(new Errors.Unauthorized("No authenticated principal in this request"));

        MvcResult r = mvc.perform(get("/api/v1/auth/me")).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void logout_returns_204() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/logout")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(204);
    }
}
