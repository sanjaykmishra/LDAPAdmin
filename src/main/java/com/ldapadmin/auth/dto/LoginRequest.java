package com.ldapadmin.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login request body.
 *
 * <p>When {@code tenantSlug} is absent or blank the attempt is treated as a
 * superadmin login.  When present the admin is authenticated against the
 * tenant's configured authentication method (LDAP bind).</p>
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        String tenantSlug) {
}
