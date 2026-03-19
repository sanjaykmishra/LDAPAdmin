package com.ldapadmin.dto.settings;

import com.ldapadmin.entity.enums.AccountType;

import java.util.Set;

/**
 * Public (unauthenticated) read-only DTO containing branding fields and
 * enabled auth types. Used by the login page to determine which login UI
 * elements to show (password form, SSO button, or both).
 */
public record BrandingDto(
        String appName,
        String logoUrl,
        String primaryColour,
        String secondaryColour,
        Set<AccountType> enabledAuthTypes) {}
