package com.ldapadmin.dto.admin;

import com.ldapadmin.entity.enums.FeatureKey;
import jakarta.validation.constraints.NotNull;

/**
 * Sets or clears a single feature permission override for an admin.
 */
public record FeaturePermissionRequest(
        @NotNull FeatureKey featureKey,
        boolean enabled) {
}
