package com.example.photoapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes a {@link ProblemDetail} JSON body when Spring Security rejects an
 * unauthenticated request — keeps 401s in the same shape as the rest of the
 * API's errors (see {@code Errors.GlobalHandler}). Errors raised by code
 * inside controllers / advice still flow through the global handler; only
 * filter-chain-level rejections come here.
 */
@Component
public class PhotoAppAuthEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(PhotoAppAuthEntryPoint.class);
    private static final URI TYPE = URI.create("https://photoapp.example/errors/unauthorized");

    private final ObjectMapper json;

    public PhotoAppAuthEntryPoint(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest req,
                         HttpServletResponse resp,
                         AuthenticationException authEx) throws IOException {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication required");
        body.setType(TYPE);
        body.setTitle(HttpStatus.UNAUTHORIZED.getReasonPhrase());
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("code", "unauthorized");
        extras.put("correlation_id", UUID.randomUUID().toString());
        extras.put("path", req.getRequestURI());
        extras.forEach(body::setProperty);

        log.debug("[{}] auth-entry-point: {}", extras.get("correlation_id"), authEx.getClass().getSimpleName());

        resp.setStatus(HttpStatus.UNAUTHORIZED.value());
        resp.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(resp.getOutputStream(), body);
    }
}
