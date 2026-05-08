package com.example.photoapp.service.auth;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.domain.user.AppUser;
import com.example.photoapp.domain.user.UserStatus;
import com.example.photoapp.repository.user.AppUserRepository;
import com.example.photoapp.security.Principal;
import com.example.photoapp.security.jwt.AppToken;
import com.example.photoapp.security.jwt.JwtIssuer;
import com.example.photoapp.security.jwt.JwtVerifier;
import com.example.photoapp.web.auth.AuthDtos.LoginRequest;
import com.example.photoapp.web.auth.AuthDtos.MeResponse;
import com.example.photoapp.web.auth.AuthDtos.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Login + refresh. Refresh tokens are rotated (a fresh access + refresh pair is
 * returned each time) — defence-in-depth against a leaked refresh, although
 * without a revocation store an attacker who steals a refresh can keep the
 * chain going. Adding revocation tracking is deferred to a dedicated slice.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer issuer;
    private final JwtVerifier verifier;
    private final String dummyHash;

    public AuthService(AppUserRepository users,
                       PasswordEncoder passwordEncoder,
                       JwtIssuer issuer,
                       JwtVerifier verifier) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.issuer = issuer;
        this.verifier = verifier;
        // Generate a real, validly-formatted hash from the same encoder so password verification
        // for unknown users runs the full BCrypt cost. Using a literal string risks malformed
        // hashes that BCryptPasswordEncoder short-circuits on (returning false in microseconds
        // and breaking timing uniformity → user enumeration).
        this.dummyHash = passwordEncoder.encode("never-matches-real-password-marker-" + System.nanoTime());
    }

    public TokenResponse login(LoginRequest req) {
        Optional<AppUser> userOpt = users.findActiveBySchoolAndEmail(req.schoolId(), req.email());
        AppUser user = userOpt.orElse(null);
        String storedHash = user == null ? dummyHash : user.getPasswordHash();
        boolean passwordOk = passwordEncoder.matches(req.password(), storedHash);

        if (user == null || !passwordOk || user.getStatus() != UserStatus.ACTIVE) {
            log.info("login failed for school={} email={}", req.schoolId(), req.email());
            throw new Errors.Unauthorized("Invalid credentials");
        }

        log.info("login ok user={} school={}", user.getId(), user.getSchoolId());
        Principal principal = new Principal(user.getId(), user.getSchoolId(), user.getRole());
        return mintPair(principal);
    }

    public TokenResponse refresh(String refreshToken) {
        Principal claimed = verifier.verifyRefresh(refreshToken);
        // Re-check the user is still active — a refresh issued before suspension shouldn't
        // grant a fresh access token.
        AppUser user = users.findByIdAndDeletedAtIsNull(claimed.userId())
                .orElseThrow(() -> new Errors.Unauthorized("Invalid credentials"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new Errors.Unauthorized("Invalid credentials");
        }

        Principal principal = new Principal(user.getId(), user.getSchoolId(), user.getRole());
        return mintPair(principal);
    }

    private TokenResponse mintPair(Principal principal) {
        AppToken access = issuer.issueAccess(principal);
        AppToken refresh = issuer.issueRefresh(principal);
        MeResponse me = new MeResponse(principal.userId(), principal.schoolId(), principal.role());
        return new TokenResponse(
                access.token(), access.expiresAt(),
                refresh.token(), refresh.expiresAt(),
                me);
    }
}
