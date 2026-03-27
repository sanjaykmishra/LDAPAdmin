package com.ldapadmin.controller;

import com.ldapadmin.auth.IpRateLimiter;
import com.ldapadmin.dto.audit.AuditEventResponse;
import com.ldapadmin.dto.settings.BrandingDto;
import com.ldapadmin.entity.AuditorLink;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.service.ApplicationSettingsService;
import com.ldapadmin.service.AuditorLinkService;
import com.ldapadmin.service.AuditorPortalService;
import com.ldapadmin.service.EvidencePackageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public (unauthenticated) auditor portal endpoints. The token in the URL
 * is the sole credential — no JWT or session required.
 *
 * <p>All endpoints return 404 for invalid/expired/revoked tokens to avoid
 * leaking information about token validity. Response headers include
 * {@code X-Robots-Tag: noindex} and {@code Cache-Control: no-store}.</p>
 */
@RestController
@RequestMapping("/api/v1/auditor/{token}")
@RequiredArgsConstructor
public class AuditorPortalController {

    private final AuditorLinkService auditorLinkService;
    private final AuditorPortalService portalService;
    private final ApplicationSettingsService settingsService;
    private final EvidencePackageService evidencePackageService;
    private final IpRateLimiter ipRateLimiter;

    // ── Portal metadata (landing page) ─────────────────────────────────────

    @GetMapping
    public ResponseEntity<Map<String, Object>> metadata(
            @PathVariable String token, HttpServletRequest request) {
        AuditorLink link = validate(token, request);
        BrandingDto branding = settingsService.getBranding();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("label", link.getLabel());
        metadata.put("directoryName", link.getDirectory().getDisplayName());
        metadata.put("expiresAt", link.getExpiresAt().toString());
        metadata.put("createdAt", link.getCreatedAt().toString());

        // Scope
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("campaignIds", link.getCampaignIds());
        scope.put("includeSod", link.isIncludeSod());
        scope.put("includeEntitlements", link.isIncludeEntitlements());
        scope.put("includeAuditEvents", link.isIncludeAuditEvents());
        scope.put("dataFrom", link.getDataFrom() != null ? link.getDataFrom().toString() : null);
        scope.put("dataTo", link.getDataTo() != null ? link.getDataTo().toString() : null);
        metadata.put("scope", scope);

        // Branding
        Map<String, Object> brand = new LinkedHashMap<>();
        brand.put("appName", branding.appName());
        brand.put("logoUrl", branding.logoUrl());
        brand.put("primaryColour", branding.primaryColour());
        brand.put("secondaryColour", branding.secondaryColour());
        metadata.put("branding", brand);

        return portalResponse(metadata);
    }

    // ── Campaigns ──────────────────────────────────────────────────────────

    @GetMapping("/campaigns")
    public ResponseEntity<List<Map<String, Object>>> campaigns(
            @PathVariable String token, HttpServletRequest request) {
        AuditorLink link = validate(token, request);
        return portalResponse(portalService.getCampaigns(link));
    }

    @GetMapping("/campaigns/{campaignId}")
    public ResponseEntity<Map<String, Object>> campaignDetail(
            @PathVariable String token, @PathVariable UUID campaignId,
            HttpServletRequest request) {
        AuditorLink link = validate(token, request);
        Map<String, Object> detail = portalService.getCampaignDetail(link, campaignId);
        if (detail == null) {
            throw new ResourceNotFoundException("Campaign not found");
        }
        return portalResponse(detail);
    }

    // ── SoD ────────────────────────────────────────────────────────────────

    @GetMapping("/sod")
    public ResponseEntity<Map<String, Object>> sod(
            @PathVariable String token, HttpServletRequest request) {
        AuditorLink link = validate(token, request);
        if (!link.isIncludeSod()) {
            throw new ResourceNotFoundException("SoD data not included in this link");
        }
        return portalResponse(portalService.getSodData(link));
    }

    // ── Entitlements ───────────────────────────────────────────────────────

    @GetMapping("/entitlements")
    public ResponseEntity<List<Map<String, Object>>> entitlements(
            @PathVariable String token, HttpServletRequest request) {
        AuditorLink link = validate(token, request);
        if (!link.isIncludeEntitlements()) {
            throw new ResourceNotFoundException("Entitlements not included in this link");
        }
        return portalResponse(portalService.getEntitlements(link));
    }

    // ── Audit events ───────────────────────────────────────────────────────

    @GetMapping("/audit-events")
    public ResponseEntity<List<AuditEventResponse>> auditEvents(
            @PathVariable String token, HttpServletRequest request) {
        AuditorLink link = validate(token, request);
        if (!link.isIncludeAuditEvents()) {
            throw new ResourceNotFoundException("Audit events not included in this link");
        }
        return portalResponse(portalService.getAuditEvents(link));
    }

    // ── Approvals ──────────────────────────────────────────────────────────

    @GetMapping("/approvals")
    public ResponseEntity<List<Map<String, Object>>> approvals(
            @PathVariable String token, HttpServletRequest request) {
        AuditorLink link = validate(token, request);
        return portalResponse(portalService.getApprovals(link));
    }

    // ── Integrity verification ───────────────────────────────────────────

    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @PathVariable String token, HttpServletRequest request) {
        AuditorLink link = validate(token, request);

        // The token was validated (exists, not revoked, not expired).
        // Scope and expiry are enforced from the DB at access time.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("verified", true);
        result.put("tokenValid", true);
        result.put("revoked", link.isRevoked());
        result.put("expired", link.isExpired());
        result.put("expiresAt", link.getExpiresAt().toString());
        result.put("accessCount", link.getAccessCount());
        result.put("verifiedAt", OffsetDateTime.now().toString());

        return portalResponse(result);
    }

    // ── Export (full ZIP) ──────────────────────────────────────────────────

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @PathVariable String token, HttpServletRequest request) throws IOException {
        AuditorLink link = validate(token, request);

        byte[] zip = evidencePackageService.generateEvidencePackage(
                link.getDirectory().getId(),
                link.getCampaignIds(),
                link.isIncludeSod(),
                link.isIncludeEntitlements(),
                link.isIncludeAuditEvents(),
                "auditor:" + link.getId());

        HttpHeaders headers = portalHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("evidence-package.zip").build());

        return ResponseEntity.ok().headers(headers).body(zip);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private AuditorLink validate(String token, HttpServletRequest request) {
        try {
            return auditorLinkService.validateToken(token,
                    request.getRemoteAddr(), request.getHeader("User-Agent"));
        } catch (ResourceNotFoundException e) {
            // Rate-limit only failed lookups (brute-force protection)
            ipRateLimiter.check(request.getRemoteAddr());
            throw e;
        }
    }

    private static HttpHeaders portalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Robots-Tag", "noindex");
        headers.setCacheControl("no-store");
        headers.set("Content-Security-Policy",
                "default-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                + "font-src 'self' https://fonts.gstatic.com; img-src 'self' data: https:; "
                + "script-src 'self'; frame-ancestors 'none'");
        return headers;
    }

    private static <T> ResponseEntity<T> portalResponse(T body) {
        return ResponseEntity.ok().headers(portalHeaders()).body(body);
    }
}
