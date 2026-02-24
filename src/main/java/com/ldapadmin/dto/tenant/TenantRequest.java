package com.ldapadmin.dto.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create / update request for a {@link com.ldapadmin.entity.Tenant}.
 */
public record TenantRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9-]+$",
                 message = "slug must contain only lowercase letters, digits and hyphens")
        String slug,
        boolean enabled) {
}
