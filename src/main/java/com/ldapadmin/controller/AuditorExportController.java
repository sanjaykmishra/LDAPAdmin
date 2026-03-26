package com.ldapadmin.controller;

import com.ldapadmin.auth.IpRateLimiter;
import com.ldapadmin.entity.AuditorLink;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.service.AuditorExportService;
import com.ldapadmin.service.AuditorLinkService;
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
import java.util.UUID;

/**
 * Public (unauthenticated) per-section export endpoints for the auditor portal.
 * CSV and PDF downloads for individual evidence sections, plus a combined
 * audit workpaper PDF.
 */
@RestController
@RequestMapping("/api/v1/auditor/{token}/export")
@RequiredArgsConstructor
public class AuditorExportController {

    private final AuditorLinkService auditorLinkService;
    private final AuditorExportService exportService;
    private final EvidencePackageService evidencePackageService;
    private final IpRateLimiter ipRateLimiter;

    // ── Campaign exports ──────────────────────────────────────────────────

    @GetMapping("/campaigns/{campaignId}/csv")
    public ResponseEntity<byte[]> campaignCsv(
            @PathVariable String token, @PathVariable UUID campaignId,
            HttpServletRequest request) {
        AuditorLink link = validate(token, request);
        byte[] csv = exportService.campaignDecisionsCsv(link, campaignId);
        if (csv.length == 0) throw new ResourceNotFoundException("Campaign not found");
        return csvResponse(csv, "campaign-decisions.csv");
    }

    @GetMapping("/campaigns/{campaignId}/pdf")
    public ResponseEntity<byte[]> campaignPdf(
            @PathVariable String token, @PathVariable UUID campaignId,
            HttpServletRequest request) throws IOException {
        AuditorLink link = validate(token, request);
        byte[] pdf = exportService.campaignDecisionsPdf(link, campaignId);
        if (pdf.length == 0) throw new ResourceNotFoundException("Campaign not found");
        return pdfResponse(pdf, "campaign-decisions.pdf");
    }

    // ── SoD exports ───────────────────────────────────────────────────────

    @GetMapping("/sod/pdf")
    public ResponseEntity<byte[]> sodPdf(
            @PathVariable String token, HttpServletRequest request) throws IOException {
        AuditorLink link = validate(token, request);
        if (!link.isIncludeSod()) throw new ResourceNotFoundException("SoD not included");
        return pdfResponse(exportService.sodPdf(link), "sod-report.pdf");
    }

    // ── Audit events exports ──────────────────────────────────────────────

    @GetMapping("/audit-events/csv")
    public ResponseEntity<byte[]> auditEventsCsv(
            @PathVariable String token, HttpServletRequest request) {
        AuditorLink link = validate(token, request);
        if (!link.isIncludeAuditEvents()) throw new ResourceNotFoundException("Audit events not included");
        return csvResponse(exportService.auditEventsCsv(link), "audit-events.csv");
    }

    @GetMapping("/audit-events/pdf")
    public ResponseEntity<byte[]> auditEventsPdf(
            @PathVariable String token, HttpServletRequest request) throws IOException {
        AuditorLink link = validate(token, request);
        if (!link.isIncludeAuditEvents()) throw new ResourceNotFoundException("Audit events not included");
        return pdfResponse(exportService.auditEventsPdf(link), "audit-events.pdf");
    }

    // ── Combined workpaper ────────────────────────────────────────────────

    @GetMapping("/workpaper")
    public ResponseEntity<byte[]> workpaper(
            @PathVariable String token, HttpServletRequest request) throws IOException {
        AuditorLink link = validate(token, request);
        byte[] zip = evidencePackageService.generateEvidencePackage(
                link.getDirectory().getId(),
                link.getCampaignIds(),
                link.isIncludeSod(),
                link.isIncludeEntitlements(),
                link.isIncludeAuditEvents(),
                "auditor-workpaper:" + link.getId());
        return ResponseEntity.ok()
                .headers(portalHeaders())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("audit-workpaper.zip").build().toString())
                .body(zip);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private AuditorLink validate(String token, HttpServletRequest request) {
        ipRateLimiter.check(request.getRemoteAddr());
        return auditorLinkService.validateToken(token,
                request.getRemoteAddr(), request.getHeader("User-Agent"));
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

    private static ResponseEntity<byte[]> csvResponse(byte[] csv, String filename) {
        HttpHeaders headers = portalHeaders();
        headers.setContentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    private static ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        HttpHeaders headers = portalHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
