package com.example.photoapp.security.jwt;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.common.school.SchoolContext;
import com.example.photoapp.security.Principal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates {@code Authorization: Bearer <token>} on every request. On
 * success populates {@link SchoolContext} and Spring's
 * {@link SecurityContextHolder}; on absence the filter is a no-op (Spring
 * Security's downstream chain will reject if the route requires auth via
 * {@link com.example.photoapp.security.PhotoAppAuthEntryPoint}). On a
 * malformed/expired/tampered token the filter clears any prior auth and lets
 * the chain proceed unauthenticated — the entry point produces the 401.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtVerifier verifier;
    private final SchoolContext schoolContext;

    public JwtAuthFilter(JwtVerifier verifier, SchoolContext schoolContext) {
        this.verifier = verifier;
        this.schoolContext = schoolContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            chain.doFilter(req, resp);
            return;
        }

        String token = header.substring(PREFIX.length()).trim();
        Principal principal;
        try {
            principal = verifier.verifyAccess(token);
        } catch (Errors.Unauthorized e) {
            // Clear any previously-set auth on this request and let the entry point write 401.
            SecurityContextHolder.clearContext();
            log.debug("rejecting request: invalid jwt");
            chain.doFilter(req, resp);
            return;
        }

        try {
            schoolContext.set(principal);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(req, resp);
        } finally {
            SecurityContextHolder.clearContext();
            schoolContext.clear();
        }
    }
}
