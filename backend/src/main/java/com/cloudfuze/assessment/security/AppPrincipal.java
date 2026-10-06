package com.cloudfuze.assessment.security;

import com.cloudfuze.assessment.domain.Role;

/** Who is calling, as read from the session token. */
public record AppPrincipal(Long userId, String email, Role role) {
}
