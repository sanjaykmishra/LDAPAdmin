package com.ldapadmin.controller;

import com.ldapadmin.auth.IpRateLimiter;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.AuditorLink;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditorExportController.class)
class AuditorExportControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private AuditorLinkService auditorLinkService;
    @MockBean private AuditorExportService exportService;
    @MockBean private EvidencePackageService evidencePackageService;
    @MockBean private IpRateLimiter ipRateLimiter;

    private static final String TOKEN = "export-test-token";
    private AuditorLink link;
    private final UUID campaignId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(UUID.randomUUID());
        dir.setDisplayName("Test Dir");

        Account creator = new Account();
        creator.setUsername("admin");

        link = AuditorLink.builder()
                .directory(dir)
                .token(TOKEN)
                .label("Export Test")
                .campaignIds(List.of(campaignId))
                .includeSod(true)
                .includeEntitlements(true)
                .includeAuditEvents(true)
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .hmacSignature("sig")
                .createdBy(creator)
                .build();
        link.setId(UUID.randomUUID());
        link.setCreatedAt(OffsetDateTime.now());

        when(auditorLinkService.validateToken(TOKEN)).thenReturn(link);
    }

    // ── Campaign CSV ──────────────────────────────────────────────────────

    @Test
    void campaignCsv_returns200WithCsv() throws Exception {
        when(exportService.campaignDecisionsCsv(any(), eq(campaignId)))
                .thenReturn("header\nrow1\n".getBytes());

        mvc.perform(get("/api/v1/auditor/{token}/export/campaigns/{id}/csv", TOKEN, campaignId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("X-Robots-Tag", "noindex"));
    }

    @Test
    void campaignCsv_emptyResult_returns404() throws Exception {
        when(exportService.campaignDecisionsCsv(any(), eq(campaignId)))
                .thenReturn(new byte[0]);

        mvc.perform(get("/api/v1/auditor/{token}/export/campaigns/{id}/csv", TOKEN, campaignId))
                .andExpect(status().isNotFound());
    }

    @Test
    void campaignCsv_invalidToken_returns404() throws Exception {
        when(auditorLinkService.validateToken("bad"))
                .thenThrow(new ResourceNotFoundException("not found"));

        mvc.perform(get("/api/v1/auditor/{token}/export/campaigns/{id}/csv", "bad", campaignId))
                .andExpect(status().isNotFound());
    }

    // ── Campaign PDF ──────────────────────────────────────────────────────

    @Test
    void campaignPdf_returns200() throws Exception {
        when(exportService.campaignDecisionsPdf(any(), eq(campaignId)))
                .thenReturn("fake-pdf".getBytes());

        mvc.perform(get("/api/v1/auditor/{token}/export/campaigns/{id}/pdf", TOKEN, campaignId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    // ── SoD PDF ───────────────────────────────────────────────────────────

    @Test
    void sodPdf_returns200() throws Exception {
        when(exportService.sodPdf(any())).thenReturn("fake-pdf".getBytes());

        mvc.perform(get("/api/v1/auditor/{token}/export/sod/pdf", TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    void sodPdf_notIncluded_returns404() throws Exception {
        link.setIncludeSod(false);

        mvc.perform(get("/api/v1/auditor/{token}/export/sod/pdf", TOKEN))
                .andExpect(status().isNotFound());
    }

    // ── Audit events CSV ──────────────────────────────────────────────────

    @Test
    void auditEventsCsv_returns200() throws Exception {
        when(exportService.auditEventsCsv(any())).thenReturn("csv".getBytes());

        mvc.perform(get("/api/v1/auditor/{token}/export/audit-events/csv", TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"));
    }

    @Test
    void auditEventsCsv_notIncluded_returns404() throws Exception {
        link.setIncludeAuditEvents(false);

        mvc.perform(get("/api/v1/auditor/{token}/export/audit-events/csv", TOKEN))
                .andExpect(status().isNotFound());
    }

    // ── Audit events PDF ──────────────────────────────────────────────────

    @Test
    void auditEventsPdf_returns200() throws Exception {
        when(exportService.auditEventsPdf(any())).thenReturn("pdf".getBytes());

        mvc.perform(get("/api/v1/auditor/{token}/export/audit-events/pdf", TOKEN))
                .andExpect(status().isOk());
    }

    // ── Workpaper ─────────────────────────────────────────────────────────

    @Test
    void workpaper_returns200WithZip() throws Exception {
        when(evidencePackageService.generateEvidencePackage(any(), any(), anyBoolean(),
                anyBoolean(), anyBoolean(), any())).thenReturn("fake-zip".getBytes());

        mvc.perform(get("/api/v1/auditor/{token}/export/workpaper", TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("audit-workpaper.zip")))
                .andExpect(header().string("X-Robots-Tag", "noindex"));
    }

    // ── No auth required ──────────────────────────────────────────────────

    @Test
    void exports_noAuthRequired() throws Exception {
        when(exportService.auditEventsCsv(any())).thenReturn("csv".getBytes());

        // No authentication() call — should still work
        mvc.perform(get("/api/v1/auditor/{token}/export/audit-events/csv", TOKEN))
                .andExpect(status().isOk());
    }
}
