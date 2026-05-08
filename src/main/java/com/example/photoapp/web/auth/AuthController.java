package com.example.photoapp.web.auth;

import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.security.Principal;
import com.example.photoapp.service.auth.AuthService;
import com.example.photoapp.web.auth.AuthDtos.LoginRequest;
import com.example.photoapp.web.auth.AuthDtos.MeResponse;
import com.example.photoapp.web.auth.AuthDtos.RefreshRequest;
import com.example.photoapp.web.auth.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;
    private final SchoolContext schoolContext;

    public AuthController(AuthService auth, SchoolContext schoolContext) {
        this.auth = auth;
        this.schoolContext = schoolContext;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }

    @GetMapping("/me")
    public MeResponse me() {
        Principal p = schoolContext.requirePrincipal();
        return new MeResponse(p.userId(), p.schoolId(), p.role());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // No server-side state to clear: revocation requires a token store (deferred slice).
        return ResponseEntity.noContent().build();
    }
}
