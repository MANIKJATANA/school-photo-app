package com.example.photoapp.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single home for every domain exception type and the global HTTP error handler.
 *
 * Per project convention (.claude/memory/feedback_single_error_file.md) we do
 * NOT split exceptions into one-class-per-file. Add a new domain error here as
 * a nested subclass of {@link AppException}, then update {@link GlobalHandler}
 * only if the mapping is non-default.
 */
public final class Errors {

    private Errors() {}

    // ============================================================
    // Exception taxonomy
    // ============================================================

    /** Base domain exception. Carries HTTP status + stable error code + human detail. */
    public static class AppException extends RuntimeException {
        private final HttpStatus status;
        private final String code;
        private final transient Map<String, Object> extras;

        public AppException(HttpStatus status, String code, String detail) {
            this(status, code, detail, Map.of(), null);
        }

        public AppException(HttpStatus status, String code, String detail, Map<String, Object> extras) {
            this(status, code, detail, extras, null);
        }

        public AppException(HttpStatus status, String code, String detail, Map<String, Object> extras, Throwable cause) {
            super(detail, cause);
            this.status = status;
            this.code = code;
            this.extras = extras == null ? Map.of() : Map.copyOf(extras);
        }

        public HttpStatus status() { return status; }
        public String code() { return code; }
        public Map<String, Object> extras() { return extras; }
    }

    public static class NotFound extends AppException {
        public NotFound(String resource, Object id) {
            super(HttpStatus.NOT_FOUND, "not_found",
                    resource + " not found: " + id,
                    Map.of("resource", resource, "id", String.valueOf(id)));
        }
        public NotFound(String detail) {
            super(HttpStatus.NOT_FOUND, "not_found", detail);
        }
    }

    public static class BadRequest extends AppException {
        public BadRequest(String detail) {
            super(HttpStatus.BAD_REQUEST, "bad_request", detail);
        }
        public BadRequest(String detail, Map<String, Object> extras) {
            super(HttpStatus.BAD_REQUEST, "bad_request", detail, extras);
        }
    }

    public static class Conflict extends AppException {
        public Conflict(String detail) {
            super(HttpStatus.CONFLICT, "conflict", detail);
        }
        public Conflict(String detail, Map<String, Object> extras) {
            super(HttpStatus.CONFLICT, "conflict", detail, extras);
        }
    }

    public static class Unauthorized extends AppException {
        public Unauthorized(String detail) {
            super(HttpStatus.UNAUTHORIZED, "unauthorized", detail);
        }
    }

    public static class Forbidden extends AppException {
        public Forbidden(String detail) {
            super(HttpStatus.FORBIDDEN, "forbidden", detail);
        }
    }

    public static class UnprocessableEntity extends AppException {
        public UnprocessableEntity(String detail, Map<String, Object> extras) {
            super(HttpStatus.UNPROCESSABLE_CONTENT, "unprocessable_entity", detail, extras);
        }
    }

    public static class Internal extends AppException {
        public Internal(String detail, Throwable cause) {
            super(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", detail, Map.of(), cause);
        }
    }

    /** Field-level validation problem reported under ProblemDetail extras. */
    public record FieldProblem(String field, String message, Object rejectedValue) {}

    // ============================================================
    // Global handler — maps every exception to a ProblemDetail body
    // ============================================================

    @RestControllerAdvice
    public static class GlobalHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalHandler.class);
        private static final URI TYPE_BASE = URI.create("https://photoapp.example/errors/");

        @ExceptionHandler(AppException.class)
        public ResponseEntity<ProblemDetail> handleApp(AppException ex, HttpServletRequest req) {
            // Never leak the developer-supplied detail on 5xx — even an Errors.Internal author
            // could accidentally embed sensitive context. The full message + stack go to the log
            // alongside the correlation id; the response carries only the generic detail.
            boolean serverError = ex.status().is5xxServerError();
            String detail = serverError ? "Internal server error" : ex.getMessage();
            ProblemDetail body = build(ex.status(), ex.code(), detail, req);
            if (!serverError) {
                ex.extras().forEach(body::setProperty);
            }
            if (serverError) {
                log.error("[{}] {} — {}", body.getProperties().get("correlation_id"), ex.code(), ex.getMessage(), ex);
            } else {
                log.debug("[{}] {} — {}", body.getProperties().get("correlation_id"), ex.code(), ex.getMessage());
            }
            return ResponseEntity.status(ex.status()).body(body);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ProblemDetail> handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
            List<FieldProblem> fields = ex.getBindingResult().getFieldErrors().stream()
                    .map(fe -> new FieldProblem(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
                    .toList();
            ProblemDetail body = build(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed", req);
            body.setProperty("fields", fields);
            return ResponseEntity.badRequest().body(body);
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
            List<FieldProblem> fields = ex.getConstraintViolations().stream()
                    .map(v -> new FieldProblem(v.getPropertyPath().toString(), v.getMessage(), v.getInvalidValue()))
                    .toList();
            ProblemDetail body = build(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed", req);
            body.setProperty("fields", fields);
            return ResponseEntity.badRequest().body(body);
        }

        @ExceptionHandler({HttpMessageNotReadableException.class,
                           MissingServletRequestParameterException.class,
                           MethodArgumentTypeMismatchException.class})
        public ResponseEntity<ProblemDetail> handleMalformedRequest(Exception ex, HttpServletRequest req) {
            ProblemDetail body = build(HttpStatus.BAD_REQUEST, "malformed_request",
                    "Request could not be parsed", req);
            return ResponseEntity.badRequest().body(body);
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ProblemDetail> handleAuth(AuthenticationException ex, HttpServletRequest req) {
            ProblemDetail body = build(HttpStatus.UNAUTHORIZED, "unauthorized",
                    "Authentication required", req);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
            ProblemDetail body = build(HttpStatus.FORBIDDEN, "forbidden",
                    "Access denied", req);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        @ExceptionHandler(AuthenticationServiceException.class)
        public ResponseEntity<ProblemDetail> handleAuthService(AuthenticationServiceException ex, HttpServletRequest req) {
            // Internal auth-pipeline failure (e.g., JWT decode crash) — treat as 500, don't leak detail.
            ProblemDetail body = build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                    "Internal server error", req);
            log.error("[{}] auth-pipeline failure", body.getProperties().get("correlation_id"), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest req) {
            // Never leak the underlying message — fall back to a generic detail with a correlation id.
            ProblemDetail body = build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                    "Internal server error", req);
            log.error("[{}] unhandled exception", body.getProperties().get("correlation_id"), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }

        private static ProblemDetail build(HttpStatus status, String code, String detail, HttpServletRequest req) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
            pd.setType(TYPE_BASE.resolve(code));
            pd.setTitle(status.getReasonPhrase());
            // LinkedHashMap to keep insertion order for predictable JSON in tests.
            Map<String, Object> base = new LinkedHashMap<>();
            base.put("code", code);
            base.put("correlation_id", UUID.randomUUID().toString());
            base.put("path", req == null ? null : req.getRequestURI());
            base.forEach(pd::setProperty);
            return pd;
        }
    }
}
