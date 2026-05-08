package com.example.photoapp.common.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class ErrorsHandlerTest {

    @RestController
    @RequestMapping("/test/errors")
    static class StubController {
        @PostMapping("/{kind}")
        public String trigger(@PathVariable String kind) {
            return switch (kind) {
                case "not-found"     -> { throw new Errors.NotFound("widget", "abc"); }
                case "bad-request"   -> { throw new Errors.BadRequest("bad input"); }
                case "conflict"      -> { throw new Errors.Conflict("duplicate"); }
                case "unauthorized"  -> { throw new Errors.Unauthorized("no token"); }
                case "forbidden"     -> { throw new Errors.Forbidden("not allowed"); }
                case "unprocessable" -> { throw new Errors.UnprocessableEntity("can't do it", Map.of("k", "v")); }
                case "internal"      -> { throw new Errors.Internal("kaboom", new RuntimeException("inner")); }
                case "unhandled"     -> { throw new IllegalStateException("not mapped"); }
                default              -> "ok";
            };
        }

        record Body(@NotBlank String name) {}

        @PostMapping("/validate")
        public String validate(@Valid @RequestBody Body body) {
            return body.name();
        }
    }

    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders
                .standaloneSetup(new StubController())
                .setControllerAdvice(new Errors.GlobalHandler())
                .build();
    }

    @Test
    void NotFound_maps_to_404_with_code_and_extras() throws Exception {
        JsonNode body = trigger("not-found");
        assertThat(body.get("status").asInt()).isEqualTo(404);
        assertThat(body.get("code").asText()).isEqualTo("not_found");
        assertThat(body.get("resource").asText()).isEqualTo("widget");
        assertThat(body.get("id").asText()).isEqualTo("abc");
        assertThat(body.get("correlation_id").asText()).isNotBlank();
    }

    @Test
    void BadRequest_maps_to_400() throws Exception {
        JsonNode body = trigger("bad-request");
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("code").asText()).isEqualTo("bad_request");
    }

    @Test
    void Conflict_maps_to_409() throws Exception {
        JsonNode body = trigger("conflict");
        assertThat(body.get("status").asInt()).isEqualTo(409);
        assertThat(body.get("code").asText()).isEqualTo("conflict");
    }

    @Test
    void Unauthorized_maps_to_401() throws Exception {
        JsonNode body = trigger("unauthorized");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("code").asText()).isEqualTo("unauthorized");
    }

    @Test
    void Forbidden_maps_to_403() throws Exception {
        JsonNode body = trigger("forbidden");
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("code").asText()).isEqualTo("forbidden");
    }

    @Test
    void UnprocessableEntity_maps_to_422_with_extras() throws Exception {
        JsonNode body = trigger("unprocessable");
        assertThat(body.get("status").asInt()).isEqualTo(422);
        assertThat(body.get("code").asText()).isEqualTo("unprocessable_entity");
        assertThat(body.get("k").asText()).isEqualTo("v");
    }

    @Test
    void Internal_maps_to_500_without_leaking_detail() throws Exception {
        JsonNode body = trigger("internal");
        assertThat(body.get("status").asInt()).isEqualTo(500);
        assertThat(body.get("code").asText()).isEqualTo("internal_error");
        assertThat(body.get("detail").asText()).doesNotContain("kaboom");
        assertThat(body.get("detail").asText()).doesNotContain("inner");
    }

    @Test
    void unhandled_exception_maps_to_500_without_leaking_inner_message() throws Exception {
        JsonNode body = trigger("unhandled");
        assertThat(body.get("status").asInt()).isEqualTo(500);
        assertThat(body.get("code").asText()).isEqualTo("internal_error");
        assertThat(body.get("detail").asText()).doesNotContain("not mapped");
    }

    @Test
    void bean_validation_failure_returns_400_with_field_problems() throws Exception {
        MvcResult result = mvc.perform(post("/test/errors/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("validation_failed");
        assertThat(body.get("fields").isArray()).isTrue();
        assertThat(body.get("fields").get(0).get("field").asText()).isEqualTo("name");
    }

    private JsonNode trigger(String kind) throws Exception {
        MvcResult r = mvc.perform(post("/test/errors/" + kind)).andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }
}
