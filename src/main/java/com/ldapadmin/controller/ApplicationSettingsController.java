package com.ldapadmin.controller;

import com.ldapadmin.dto.settings.ApplicationSettingsDto;
import com.ldapadmin.dto.settings.BrandingDto;
import com.ldapadmin.dto.settings.UpdateApplicationSettingsRequest;
import com.ldapadmin.service.ApplicationSettingsService;
import com.ldapadmin.service.siem.SiemExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final SiemExportService          siemExportService;

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

    /** Marks first-run setup as complete. Called by the setup wizard on the final step. */
    @PostMapping("/complete-setup")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApplicationSettingsDto completeSetup() {
        return service.markSetupComplete();
    }

    /** Tests SIEM connectivity by sending a synthetic audit event. */
    @PostMapping("/siem/test")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public java.util.Map<String, String> testSiem() {
        String connectivity = siemExportService.testConnectivity();
        String delivery = siemExportService.sendTestEvent();
        return java.util.Map.of("connectivity", connectivity, "delivery", delivery);
    }
}
