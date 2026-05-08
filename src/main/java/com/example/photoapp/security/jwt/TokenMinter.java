package com.example.photoapp.security.jwt;

import com.example.photoapp.security.Principal;
import com.example.photoapp.web.auth.AuthDtos.MeResponse;
import com.example.photoapp.web.auth.AuthDtos.TokenResponse;
import org.springframework.stereotype.Service;

/**
 * Mints an access + refresh JWT pair for a {@link Principal} and packages
 * them with the matching {@link MeResponse} into a {@link TokenResponse}.
 * Single source of truth for the token-pair shape — login, refresh, and
 * onboarding all go through this.
 */
@Service
public class TokenMinter {

    private final JwtIssuer issuer;

    public TokenMinter(JwtIssuer issuer) {
        this.issuer = issuer;
    }

    public TokenResponse mintPair(Principal principal) {
        AppToken access = issuer.issueAccess(principal);
        AppToken refresh = issuer.issueRefresh(principal);
        MeResponse me = new MeResponse(principal.userId(), principal.schoolId(), principal.role());
        return new TokenResponse(
                access.token(), access.expiresAt(),
                refresh.token(), refresh.expiresAt(),
                me);
    }
}
