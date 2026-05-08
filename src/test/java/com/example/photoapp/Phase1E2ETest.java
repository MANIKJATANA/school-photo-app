package com.example.photoapp;

import com.example.photoapp.domain.enrolment.TeachingRole;
import com.example.photoapp.web.auth.AuthDtos.LoginRequest;
import com.example.photoapp.web.auth.AuthDtos.RefreshRequest;
import com.example.photoapp.web.auth.AuthDtos.TokenResponse;
import com.example.photoapp.web.dto.ClassTeacherDtos.AssignTeacherRequest;
import com.example.photoapp.web.dto.EnrolmentDtos.EnrolStudentRequest;
import com.example.photoapp.web.dto.KlassDtos.ClassResponse;
import com.example.photoapp.web.dto.KlassDtos.CreateClassRequest;
import com.example.photoapp.web.dto.StudentDtos.CreateStudentRequest;
import com.example.photoapp.web.dto.StudentDtos.StudentResponse;
import com.example.photoapp.web.dto.TeacherDtos.CreateTeacherRequest;
import com.example.photoapp.web.dto.TeacherDtos.TeacherResponse;
import com.example.photoapp.web.onboarding.OnboardingDtos.AdminPart;
import com.example.photoapp.web.onboarding.OnboardingDtos.CreateSchoolRequest;
import com.example.photoapp.web.onboarding.OnboardingDtos.OnboardingResponse;
import com.example.photoapp.web.onboarding.OnboardingDtos.SchoolPart;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end happy path + auth boundary checks for Phase 1.
 *
 * Skipped silently when Docker isn't reachable from the test JVM (matches the
 * existing pattern). On a developer machine with Docker, this brings up
 * Postgres in a container, runs Flyway, and walks the canonical workflow.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.MethodName.class)
class Phase1E2ETest {

    @Autowired TestRestTemplate http;

    private static final String ONBOARDING_KEY = "dev-only-onboarding-key-replace-in-prod";
    private static final String ONBOARDING_KEY_HEADER = "X-Onboarding-Key";

    // ============================================================
    // Happy path
    // ============================================================

    @Test
    void a_happy_path_onboard_through_assignments() {
        // 1. Onboard a school + admin.
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        String adminPassword = "passw0rd-strong";
        OnboardingResponse onb = onboard("Test School", adminEmail, adminPassword);
        assertThat(onb.schoolId()).isNotNull();
        assertThat(onb.adminUserId()).isNotNull();
        assertThat(onb.defaultEventId()).isNotNull();
        assertThat(onb.tokens().accessToken()).isNotBlank();

        String adminAccess = onb.tokens().accessToken();

        // 2. /auth/me returns the admin's principal.
        ResponseEntity<JsonNode> me = exchangeJson(HttpMethod.GET, "/api/v1/auth/me", null, adminAccess, JsonNode.class);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody().get("userId").asText()).isEqualTo(onb.adminUserId().toString());
        assertThat(me.getBody().get("role").asText()).isEqualTo("ADMIN");

        // 3. Create a teacher.
        ResponseEntity<TeacherResponse> teacherResp = exchangeJson(HttpMethod.POST, "/api/v1/teachers",
                new CreateTeacherRequest("Mary", "Poppins", uniqueEmail("teacher"), "passw0rd-strong",
                        "E-001", "555-0100"),
                adminAccess, TeacherResponse.class);
        assertThat(teacherResp.getStatusCode().value()).isEqualTo(201);
        UUID teacherId = teacherResp.getBody().id();

        // 4. Create a student.
        ResponseEntity<StudentResponse> studentResp = exchangeJson(HttpMethod.POST, "/api/v1/students",
                new CreateStudentRequest("Alice", "Liddell", uniqueEmail("student"), "passw0rd-strong",
                        "R-001", null, "555-0200"),
                adminAccess, StudentResponse.class);
        assertThat(studentResp.getStatusCode().value()).isEqualTo(201);
        UUID studentId = studentResp.getBody().id();

        // 5. Create a class.
        ResponseEntity<ClassResponse> classResp = exchangeJson(HttpMethod.POST, "/api/v1/classes",
                new CreateClassRequest("Grade 5 - A", "2025-2026"),
                adminAccess, ClassResponse.class);
        assertThat(classResp.getStatusCode().value()).isEqualTo(201);
        UUID classId = classResp.getBody().id();

        // 6. Enrol the student.
        ResponseEntity<JsonNode> enrolResp = exchangeJson(HttpMethod.POST,
                "/api/v1/classes/" + classId + "/students",
                new EnrolStudentRequest(studentId), adminAccess, JsonNode.class);
        assertThat(enrolResp.getStatusCode().value()).isEqualTo(201);
        assertThat(enrolResp.getBody().get("studentId").asText()).isEqualTo(studentId.toString());

        // 7. Assign the teacher.
        ResponseEntity<JsonNode> assignResp = exchangeJson(HttpMethod.POST,
                "/api/v1/classes/" + classId + "/teachers",
                new AssignTeacherRequest(teacherId, TeachingRole.CLASS_TEACHER),
                adminAccess, JsonNode.class);
        assertThat(assignResp.getStatusCode().value()).isEqualTo(201);
        assertThat(assignResp.getBody().get("role").asText()).isEqualTo("CLASS_TEACHER");

        // 8. List students in the class.
        ResponseEntity<JsonNode> studentsList = exchangeJson(HttpMethod.GET,
                "/api/v1/classes/" + classId + "/students", null, adminAccess, JsonNode.class);
        assertThat(studentsList.getStatusCode().value()).isEqualTo(200);
        assertThat(studentsList.getBody().isArray()).isTrue();
        assertThat(streamIds(studentsList.getBody(), "studentId")).contains(studentId.toString());

        // 9. List teachers in the class.
        ResponseEntity<JsonNode> teachersList = exchangeJson(HttpMethod.GET,
                "/api/v1/classes/" + classId + "/teachers", null, adminAccess, JsonNode.class);
        assertThat(teachersList.getStatusCode().value()).isEqualTo(200);
        assertThat(streamIds(teachersList.getBody(), "teacherId")).contains(teacherId.toString());

        // 10. Refresh round-trip.
        ResponseEntity<TokenResponse> refreshed = exchangeJson(HttpMethod.POST, "/api/v1/auth/refresh",
                new RefreshRequest(onb.tokens().refreshToken()), null, TokenResponse.class);
        assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
        assertThat(refreshed.getBody().accessToken()).isNotBlank();

        // 11. /auth/login flow with the same admin credentials.
        ResponseEntity<TokenResponse> loginResp = exchangeJson(HttpMethod.POST, "/api/v1/auth/login",
                new LoginRequest(onb.schoolId(), adminEmail, adminPassword),
                null, TokenResponse.class);
        assertThat(loginResp.getStatusCode().value()).isEqualTo(200);
        assertThat(loginResp.getBody().user().role().name()).isEqualTo("ADMIN");
    }

    // ============================================================
    // Auth boundary
    // ============================================================

    @Test
    void b_protected_route_without_token_returns_401() {
        ResponseEntity<JsonNode> r = exchangeJson(HttpMethod.GET, "/api/v1/students", null, null, JsonNode.class);
        assertThat(r.getStatusCode().value()).isEqualTo(401);
        assertThat(r.getBody().get("code").asText()).isEqualTo("unauthorized");
    }

    @Test
    void c_admin_route_rejects_student_role_with_403() {
        // Onboard a fresh school so this test is independent of method `a`'s state.
        OnboardingResponse onb = onboard("Auth Boundary School",
                "admin-" + UUID.randomUUID() + "@example.com", "passw0rd-strong");
        String adminAccess = onb.tokens().accessToken();

        // Admin creates a student account.
        String studentEmail = uniqueEmail("authz-student");
        String studentPassword = "passw0rd-strong";
        ResponseEntity<StudentResponse> sresp = exchangeJson(HttpMethod.POST, "/api/v1/students",
                new CreateStudentRequest("Bob", "Builder", studentEmail, studentPassword,
                        null, null, null),
                adminAccess, StudentResponse.class);
        assertThat(sresp.getStatusCode().value()).isEqualTo(201);

        // Student logs in with their own credentials.
        ResponseEntity<TokenResponse> studentLogin = exchangeJson(HttpMethod.POST, "/api/v1/auth/login",
                new LoginRequest(onb.schoolId(), studentEmail, studentPassword),
                null, TokenResponse.class);
        assertThat(studentLogin.getStatusCode().value()).isEqualTo(200);
        String studentAccess = studentLogin.getBody().accessToken();

        // Student attempts an ADMIN-only route → 403.
        ResponseEntity<JsonNode> attempt = exchangeJson(HttpMethod.POST, "/api/v1/students",
                new CreateStudentRequest("Charlie", "Brown", uniqueEmail("blocked"),
                        "passw0rd-strong", null, null, null),
                studentAccess, JsonNode.class);
        assertThat(attempt.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void d_bad_onboarding_key_returns_403() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set(ONBOARDING_KEY_HEADER, "definitely-not-the-real-key");

        ResponseEntity<JsonNode> r = http.exchange(
                "/api/v1/onboarding", HttpMethod.POST,
                new HttpEntity<>(validOnboardBody("Bad Key School", "x@example.com", "passw0rd-strong"), h),
                JsonNode.class);
        assertThat(r.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void e_login_failures_return_uniform_401() {
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        OnboardingResponse onb = onboard("Login Failure School", adminEmail, "correct-password-123");

        // Unknown email — no enumeration: same shape as wrong password.
        ResponseEntity<JsonNode> unknownEmail = exchangeJson(HttpMethod.POST, "/api/v1/auth/login",
                new LoginRequest(onb.schoolId(), "nobody-by-this-email@example.com", "anything"),
                null, JsonNode.class);
        assertThat(unknownEmail.getStatusCode().value()).isEqualTo(401);
        assertThat(unknownEmail.getBody().get("code").asText()).isEqualTo("unauthorized");

        // Real email + wrong password — also 401 with the same code.
        ResponseEntity<JsonNode> wrongPassword = exchangeJson(HttpMethod.POST, "/api/v1/auth/login",
                new LoginRequest(onb.schoolId(), adminEmail, "definitely-not-the-password"),
                null, JsonNode.class);
        assertThat(wrongPassword.getStatusCode().value()).isEqualTo(401);
        assertThat(wrongPassword.getBody().get("code").asText()).isEqualTo("unauthorized");
    }

    // ============================================================
    // helpers
    // ============================================================

    private OnboardingResponse onboard(String schoolName, String email, String password) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set(ONBOARDING_KEY_HEADER, ONBOARDING_KEY);

        ResponseEntity<OnboardingResponse> resp = http.exchange(
                "/api/v1/onboarding", HttpMethod.POST,
                new HttpEntity<>(validOnboardBody(schoolName, email, password), h),
                OnboardingResponse.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        return resp.getBody();
    }

    private static CreateSchoolRequest validOnboardBody(String schoolName, String email, String password) {
        return new CreateSchoolRequest(
                new SchoolPart(schoolName, "1 Main St", "info@example.com"),
                new AdminPart(email, password, "555-0100"));
    }

    private <T> ResponseEntity<T> exchangeJson(HttpMethod method, String path, Object body,
                                                String bearer, Class<T> responseType) {
        HttpHeaders h = new HttpHeaders();
        if (body != null) h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        if (bearer != null) h.setBearerAuth(bearer);
        return http.exchange(path, method, new HttpEntity<>(body, h), responseType);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static List<String> streamIds(JsonNode arr, String fieldName) {
        return arr.findValues(fieldName).stream().map(JsonNode::asText).toList();
    }
}
