package com.example.photoapp.security.jwt;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.domain.user.Role;
import com.example.photoapp.security.Principal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;

import static com.example.photoapp.security.jwt.JwtIssuer.CLAIM_ROLE;
import static com.example.photoapp.security.jwt.JwtIssuer.CLAIM_SCHOOL_ID;
import static com.example.photoapp.security.jwt.JwtIssuer.CLAIM_TYPE;
import static com.example.photoapp.security.jwt.JwtIssuer.TYPE_ACCESS;
import static com.example.photoapp.security.jwt.JwtIssuer.TYPE_REFRESH;

/**
 * Validates JWTs minted by {@link JwtIssuer}. HS256 is pinned at the parser
 * level so a token claiming a different alg is rejected (alg-confusion
 * defence). Every verification failure surfaces as
 * {@link Errors.Unauthorized}; the underlying message is logged but never
 * returned to the caller.
 */
public class JwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(JwtVerifier.class);

    private final JwtProperties props;
    private final Clock clock;
    private final SecretKey key;

    public JwtVerifier(JwtProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public Principal verifyAccess(String token) {
        return verify(token, TYPE_ACCESS);
    }

    public Principal verifyRefresh(String token) {
        return verify(token, TYPE_REFRESH);
    }

    private Principal verify(String token, String expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(props.issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("jwt verify failed (parse/sig): {}", e.getClass().getSimpleName());
            throw new Errors.Unauthorized("Invalid token");
        }

        String type = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.equals(type)) {
            log.debug("jwt verify failed (type mismatch): expected {} got {}", expectedType, type);
            throw new Errors.Unauthorized("Invalid token");
        }

        UUID userId;
        UUID schoolId;
        Role role;
        try {
            userId = UUID.fromString(claims.getSubject());
            schoolId = UUID.fromString(claims.get(CLAIM_SCHOOL_ID, String.class));
            role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));
        } catch (IllegalArgumentException | NullPointerException e) {
            log.debug("jwt verify failed (claims): {}", e.getClass().getSimpleName());
            throw new Errors.Unauthorized("Invalid token");
        }

        return new Principal(userId, schoolId, role);
    }
}
