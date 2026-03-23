package com.ldapadmin.controller;

import com.ldapadmin.dto.settings.ApplicationSettingsDto;
import com.ldapadmin.dto.settings.BrandingDto;
import com.ldapadmin.dto.settings.UpdateApplicationSettingsRequest;
import com.ldapadmin.service.ApplicationSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global application settings.
 *
 * <pre>
 *   GET /api/v1/settings  — returns current settings (or defaults if not yet configured)
 *   PUT /api/v1/settings  — create or replace settings
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class ApplicationSettingsController {

    private final ApplicationSettingsService service;

    /** Returns current settings (superadmin only). */
    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApplicationSettingsDto get() {
        return service.get();
    }

    /** Returns only branding fields (public — no auth required). */
    @GetMapping("/branding")
    public BrandingDto getBranding() {
        return service.getBranding();
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApplicationSettingsDto upsert(@Valid @RequestBody UpdateApplicationSettingsRequest req) {
        return service.upsert(req);
    }
}
