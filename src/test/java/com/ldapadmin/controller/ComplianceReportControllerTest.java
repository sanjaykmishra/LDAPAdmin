package com.ldapadmin.controller;

import com.ldapadmin.controller.directory.ComplianceReportController;
import com.ldapadmin.service.PdfReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ComplianceReportController.class)
class ComplianceReportControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private PdfReportService pdfReportService;

    private final UUID dirId = UUID.randomUUID();
    private final UUID campaignId = UUID.randomUUID();
    private static final byte[] FAKE_PDF = "%PDF-1.4 fake content".getBytes();

    // ── User Access Report ─────────────────────────────────────────────────

    @Test
    void userAccessReport_superadmin_returns200WithPdf() throws Exception {
        when(pdfReportService.generateUserAccessReport(eq(dirId), isNull()))
                .thenReturn(FAKE_PDF);

        mvc.perform(get("/api/v1/directories/{dirId}/compliance-reports/user-access", dirId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"user-access-report.pdf\""));

        verify(pdfReportService).generateUserAccessReport(eq(dirId), isNull());
    }

    @Test
    void userAccessReport_withGroupDnFilter_passesParameter() throws Exception {
        String groupDn = "cn=admins,dc=example,dc=com";
        when(pdfReportService.generateUserAccessReport(eq(dirId), eq(groupDn)))
                .thenReturn(FAKE_PDF);

        mvc.perform(get("/api/v1/directories/{dirId}/compliance-reports/user-access", dirId)
                        .param("groupDn", groupDn)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        verify(pdfReportService).generateUserAccessReport(eq(dirId), eq(groupDn));
    }

    @Test
    void userAccessReport_admin_returns200() throws Exception {
        when(pdfReportService.generateUserAccessReport(eq(dirId), isNull()))
                .thenReturn(FAKE_PDF);

        mvc.perform(get("/api/v1/directories/{dirId}/compliance-reports/user-access", dirId)
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void userAccessReport_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/v1/directories/{dirId}/compliance-reports/user-access", dirId))
                .andExpect(status().isUnauthorized());
    }

    // ── Access Review Summary ──────────────────────────────────────────────

    @Test
    void accessReviewSummary_superadmin_returns200WithPdf() throws Exception {
        when(pdfReportService.generateAccessReviewSummary(eq(campaignId)))
                .thenReturn(FAKE_PDF);

        mvc.perform(get("/api/v1/directories/{dirId}/compliance-reports/access-review-summary/{campaignId}",
                        dirId, campaignId)
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"access-review-summary.pdf\""));

        verify(pdfReportService).generateAccessReviewSummary(eq(campaignId));
    }

    @Test
    void accessReviewSummary_admin_returns200() throws Exception {
        when(pdfReportService.generateAccessReviewSummary(eq(campaignId)))
                .thenReturn(FAKE_PDF);

        mvc.perform(get("/api/v1/directories/{dirId}/compliance-reports/access-review-summary/{campaignId}",
                        dirId, campaignId)
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void accessReviewSummary_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/v1/directories/{dirId}/compliance-reports/access-review-summary/{campaignId}",
                        dirId, campaignId))
                .andExpect(status().isUnauthorized());
    }

    // ── Privileged Account Inventory ───────────────────────────────────────

    @Test
    void privilegedAccounts_superadmin_returns200WithPdf() throws Exception {
        when(pdfReportService.generatePrivilegedAccountInventory())
                .thenReturn(FAKE_PDF);

        mvc.perform(get("/api/v1/compliance-reports/privileged-accounts")
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"privileged-account-inventory.pdf\""));

        verify(pdfReportService).generatePrivilegedAccountInventory();
    }

    @Test
    void privilegedAccounts_admin_returns403() throws Exception {
        mvc.perform(get("/api/v1/compliance-reports/privileged-accounts")
                        .with(authentication(adminAuth())))
                .andExpect(status().isForbidden());
    }

    @Test
    void privilegedAccounts_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/v1/compliance-reports/privileged-accounts"))
                .andExpect(status().isUnauthorized());
    }
}
