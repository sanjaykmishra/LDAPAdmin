package com.ldapadmin.dto.settings;

/**
 * Public (unauthenticated) read-only DTO containing only branding fields.
 * Used by the login page and any other pre-auth UI that needs the app name
 * and theme colours.
 */
public record BrandingDto(
        String appName,
        String logoUrl,
        String primaryColour,
        String secondaryColour) {}
