package com.example.photoapp.common.school;

import com.example.photoapp.common.error.Errors;
import com.example.photoapp.security.Principal;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.UUID;

/**
 * Request-scoped holder for the viewer's identity. Populated by the JWT
 * filter (Slice 2b) once it validates the access token. Services compose
 * {@code SchoolScopes.activeInSchool(ctx.requireSchoolId())} instead of
 * threading school_id through every method signature.
 *
 * <p>Reading {@link #requireSchoolId()} or {@link #requirePrincipal()} on an
 * unauthenticated request throws {@link Errors.Unauthorized}; that defends
 * against a route accidentally configured outside the JWT filter chain.
 */
@Component
@RequestScope
public class SchoolContext {

    private Principal principal;

    public void set(Principal principal) {
        this.principal = principal;
    }

    public void clear() {
        this.principal = null;
    }

    public Principal requirePrincipal() {
        if (principal == null) {
            throw new Errors.Unauthorized("No authenticated principal in this request");
        }
        return principal;
    }

    public UUID requireSchoolId() {
        return requirePrincipal().schoolId();
    }

    public boolean isAuthenticated() {
        return principal != null;
    }
}
