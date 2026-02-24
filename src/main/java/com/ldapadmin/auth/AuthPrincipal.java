package com.ldapadmin.auth;

import java.util.UUID;

/**
 * Immutable principal placed in {@link org.springframework.security.core.context.SecurityContextHolder}
 * after successful JWT validation.
 *
 * <p>{@code tenantId} is {@code null} for {@link PrincipalType#SUPERADMIN} accounts.</p>
 */
public record AuthPrincipal(
        PrincipalType type,
        UUID id,
        UUID tenantId,
        String username) {

    public boolean isSuperadmin() {
        return type == PrincipalType.SUPERADMIN;
    }
}
