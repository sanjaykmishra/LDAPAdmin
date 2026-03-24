package com.ldapadmin.controller;

import com.ldapadmin.controller.directory.CrossCampaignReportController;
import com.ldapadmin.dto.accessreview.CampaignMetricRow;
import com.ldapadmin.dto.accessreview.CrossCampaignReportDto;
import com.ldapadmin.dto.accessreview.ReviewerMetricRow;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.service.CrossCampaignReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CrossCampaignReportController.class)
class CrossCampaignReportControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private CrossCampaignReportService reportService;

    private final UUID dirId = UUID.randomUUID();

    @Test
    void getReport_returns200WithMetrics() throws Exception {
        CrossCampaignReportDto report = buildReport();
        when(reportService.generateReport(eq(dirId), any(), any(), any())).thenReturn(report);

        mvc.perform(get("/api/v1/directories/{dirId}/access-reviews/cross-campaign-report", dirId)
                        .param("from", OffsetDateTime.now().minusMonths(6).toString())
                        .param("to", OffsetDateTime.now().toString())
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCampaigns").value(2))
                .andExpect(jsonPath("$.totalDecisions").value(15))
                .andExpect(jsonPath("$.totalConfirmed").value(8))
                .andExpect(jsonPath("$.totalRevoked").value(3))
                .andExpect(jsonPath("$.campaigns").isArray())
                .andExpect(jsonPath("$.campaigns.length()").value(2))
                .andExpect(jsonPath("$.reviewers").isArray());
    }

    @Test
    void getReport_withStatusFilter_passes() throws Exception {
        CrossCampaignReportDto report = buildReport();
        when(reportService.generateReport(eq(dirId), any(), any(), eq(CampaignStatus.CLOSED)))
                .thenReturn(report);

        mvc.perform(get("/api/v1/directories/{dirId}/access-reviews/cross-campaign-report", dirId)
                        .param("from", OffsetDateTime.now().minusMonths(6).toString())
                        .param("to", OffsetDateTime.now().toString())
                        .param("status", "CLOSED")
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void getReport_asAdmin_returns200() throws Exception {
        CrossCampaignReportDto report = buildReport();
        when(reportService.generateReport(eq(dirId), any(), any(), any())).thenReturn(report);

        mvc.perform(get("/api/v1/directories/{dirId}/access-reviews/cross-campaign-report", dirId)
                        .param("from", OffsetDateTime.now().minusMonths(6).toString())
                        .param("to", OffsetDateTime.now().toString())
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk());
    }

    @Test
    void getReport_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/v1/directories/{dirId}/access-reviews/cross-campaign-report", dirId)
                        .param("from", OffsetDateTime.now().minusMonths(6).toString())
                        .param("to", OffsetDateTime.now().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getReport_missingFromParam_returnsError() throws Exception {
        mvc.perform(get("/api/v1/directories/{dirId}/access-reviews/cross-campaign-report", dirId)
                        .param("to", OffsetDateTime.now().toString())
                        .with(authentication(superadminAuth())))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void exportCsv_returns200WithCsvContentType() throws Exception {
        when(reportService.exportCsv(eq(dirId), any(), any(), any()))
                .thenReturn("header\nrow1\n".getBytes());

        mvc.perform(get("/api/v1/directories/{dirId}/access-reviews/cross-campaign-report/export", dirId)
                        .param("from", OffsetDateTime.now().minusMonths(6).toString())
                        .param("to", OffsetDateTime.now().toString())
                        .param("format", "csv")
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"cross-campaign-report.csv\""));
    }

    @Test
    void exportPdf_returns200WithPdfContentType() throws Exception {
        when(reportService.exportPdf(eq(dirId), any(), any(), any()))
                .thenReturn("%PDF-1.4 fake content".getBytes());

        mvc.perform(get("/api/v1/directories/{dirId}/access-reviews/cross-campaign-report/export", dirId)
                        .param("from", OffsetDateTime.now().minusMonths(6).toString())
                        .param("to", OffsetDateTime.now().toString())
                        .param("format", "pdf")
                        .with(authentication(superadminAuth())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"cross-campaign-report.pdf\""));
    }

    @Test
    void export_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/api/v1/directories/{dirId}/access-reviews/cross-campaign-report/export", dirId)
                        .param("from", OffsetDateTime.now().minusMonths(6).toString())
                        .param("to", OffsetDateTime.now().toString())
                        .param("format", "csv"))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private CrossCampaignReportDto buildReport() {
        var c1 = new CampaignMetricRow(UUID.randomUUID(), "Q1 Review", CampaignStatus.CLOSED,
                OffsetDateTime.now().minusDays(30), OffsetDateTime.now().minusDays(5), 25L,
                10, 6, 3, 1, 90.0);
        var c2 = new CampaignMetricRow(UUID.randomUUID(), "Q2 Review", CampaignStatus.ACTIVE,
                OffsetDateTime.now().minusDays(10), null, null,
                5, 2, 0, 3, 40.0);
        var r1 = new ReviewerMetricRow(UUID.randomUUID(), "reviewer1", 5, 3, 2, 40.0, 36.0);

        return new CrossCampaignReportDto(
                OffsetDateTime.now().minusMonths(6), OffsetDateTime.now(),
                2, Map.of("CLOSED", 1L, "ACTIVE", 1L),
                15, 8, 3, 4,
                27.3, 25.0,
                List.of(c1, c2), List.of(r1));
    }
}
