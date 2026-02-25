package com.ldapadmin.controller;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.settings.ApplicationSettingsDto;
import com.ldapadmin.dto.settings.UpdateApplicationSettingsRequest;
import com.ldapadmin.service.ApplicationSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-tenant application settings (§10.2).
 *
 * <pre>
 *   GET /api/settings  — returns current settings (or defaults if not yet configured)
 *   PUT /api/settings  — create or replace settings
 * </pre>
 *
 * <p>These endpoints are scoped to the authenticated principal's own tenant.
 * Superadmin accounts do not have a tenant and will receive a 400 response.</p>
 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class ApplicationSettingsController {

    private final ApplicationSettingsService service;

    @GetMapping
    public ApplicationSettingsDto get(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.get(principal);
    }

    @PutMapping
    public ApplicationSettingsDto upsert(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateApplicationSettingsRequest req) {
        return service.upsert(req, principal);
    }
}
