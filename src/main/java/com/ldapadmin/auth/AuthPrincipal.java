package com.ldapadmin.auth;

import java.util.UUID;

/**
 * Immutable principal placed in {@link org.springframework.security.core.context.SecurityContextHolder}
 * after successful JWT validation.
 */
public record AuthPrincipal(
        PrincipalType type,
        UUID id,
        String username) {

    public boolean isSuperadmin() {
        return type == PrincipalType.SUPERADMIN;
    }
}
