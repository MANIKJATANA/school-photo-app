package com.example.photoapp.security.jwt;

import com.example.photoapp.security.Principal;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/**
 * Mints access and refresh JWTs. HS256 is pinned at sign time; JwtVerifier
 * pins it at verify time too, preventing alg-confusion attacks.
 */
public class JwtIssuer {

    static final String CLAIM_SCHOOL_ID = "sid";
    static final String CLAIM_ROLE      = "role";
    static final String CLAIM_TYPE      = "typ";
    static final String TYPE_ACCESS     = "access";
    static final String TYPE_REFRESH    = "refresh";

    private final JwtProperties props;
    private final Clock clock;
    private final SecretKey key;

    public JwtIssuer(JwtProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public AppToken issueAccess(Principal principal) {
        return mint(principal, TYPE_ACCESS, props.accessTtl().toMillis());
    }

    public AppToken issueRefresh(Principal principal) {
        return mint(principal, TYPE_REFRESH, props.refreshTtl().toMillis());
    }

    private AppToken mint(Principal principal, String type, long ttlMillis) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusMillis(ttlMillis);
        String token = Jwts.builder()
                .issuer(props.issuer())
                .subject(principal.userId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_SCHOOL_ID, principal.schoolId().toString())
                .claim(CLAIM_ROLE, principal.role().name())
                .claim(CLAIM_TYPE, type)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new AppToken(token, expiresAt);
    }
}
