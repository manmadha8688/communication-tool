package com.cloudfuze.assessment.security;

import com.cloudfuze.assessment.entity.AppUser;
import com.cloudfuze.assessment.exception.ApiException;
import com.cloudfuze.assessment.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** The signed-in person. Never taken from a request parameter. */
@Component
public class CurrentUser {

    private final AppUserRepository users;

    public CurrentUser(AppUserRepository users) {
        this.users = users;
    }

    public AppPrincipal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppPrincipal p)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in.");
        }
        return p;
    }

    public AppUser user() {
        AppPrincipal p = principal();
        AppUser u = users.findById(p.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in again."));
        // The role as the admin_email table says right now (worked out per request in the filter).
        u.setRole(p.role());
        return u;
    }
}
