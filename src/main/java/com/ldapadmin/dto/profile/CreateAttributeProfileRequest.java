package com.ldapadmin.dto.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Write DTO for creating or replacing an attribute profile.
 *
 * <p>Use {@code branchDn = "*"} and {@code isDefault = true} to create the
 * directory-level fallback profile.</p>
 */
public record CreateAttributeProfileRequest(
        @NotBlank String branchDn,
        String displayName,
        boolean isDefault,
        @NotNull @Valid List<UpsertAttributeProfileEntryRequest> entries) {}
