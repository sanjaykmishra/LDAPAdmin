package com.ldapadmin.service;

import com.ldapadmin.dto.audit.AuditEventResponse;
import com.ldapadmin.entity.AuditorLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates CSV and PDF exports for individual auditor portal sections.
 * Delegates to {@link AuditorPortalService} for data and
 * {@link PdfReportService} for PDF rendering.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditorExportService {

    private final AuditorPortalService portalService;
    private final PdfReportService pdfReportService;

    // ── Campaign CSV ──────────────────────────────────────────────────────────

    public byte[] campaignDecisionsCsv(AuditorLink link, java.util.UUID campaignId) {
        Map<String, Object> detail = portalService.getCampaignDetail(link, campaignId);
        if (detail == null) return new byte[0];

        StringBuilder csv = new StringBuilder();
        csv.append("Member DN,Member Name,Decision,Decided By,Decided At\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) detail.get("decisions");
        if (decisions != null) {
            for (Map<String, Object> d : decisions) {
                csv.append(csvEscape(str(d, "memberDn"))).append(',');
                csv.append(csvEscape(str(d, "memberDisplayName"))).append(',');
                csv.append(csvEscape(str(d, "decision"))).append(',');
                csv.append(csvEscape(str(d, "decidedBy"))).append(',');
                csv.append(csvEscape(str(d, "decidedAt"))).append('\n');
            }
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── Campaign PDF ──────────────────────────────────────────────────────────

    public byte[] campaignDecisionsPdf(AuditorLink link, java.util.UUID campaignId) throws IOException {
        Map<String, Object> detail = portalService.getCampaignDetail(link, campaignId);
        if (detail == null) return new byte[0];

        List<String> headers = List.of("Member", "Decision", "Decided By", "Date");
        List<List<String>> rows = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) detail.get("decisions");
        if (decisions != null) {
            for (Map<String, Object> d : decisions) {
                rows.add(List.of(
                        str(d, "memberDisplayName"),
                        str(d, "decision"),
                        str(d, "decidedBy"),
                        str(d, "decidedAt")));
            }
        }

        String title = "Campaign: " + str(detail, "name");
        return pdfReportService.buildPdf(title, "Access Review Decisions", headers, rows);
    }

    // ── SoD PDF ───────────────────────────────────────────────────────────────

    public byte[] sodPdf(AuditorLink link) throws IOException {
        Map<String, Object> data = portalService.getSodData(link);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> policies = (List<Map<String, Object>>) data.get("policies");
        List<String> policyHeaders = List.of("Name", "Group A", "Group B", "Severity", "Action");
        List<List<String>> policyRows = new ArrayList<>();
        if (policies != null) {
            for (Map<String, Object> p : policies) {
                policyRows.add(List.of(
                        str(p, "name"), str(p, "groupAName"), str(p, "groupBName"),
                        str(p, "severity"), str(p, "action")));
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> violations = (List<Map<String, Object>>) data.get("violations");
        List<String> violHeaders = List.of("User", "Policy", "Status", "Detected", "Resolved");
        List<List<String>> violRows = new ArrayList<>();
        if (violations != null) {
            for (Map<String, Object> v : violations) {
                violRows.add(List.of(
                        str(v, "userDisplayName"), str(v, "policyName"), str(v, "status"),
                        str(v, "detectedAt"), str(v, "resolvedAt")));
            }
        }

        return pdfReportService.buildPdfMultiSection(
                "Separation of Duties", "Policies and Violations",
                List.of("Policies", "Violations"),
                List.of(policyHeaders, violHeaders),
                List.of(policyRows, violRows));
    }

    // ── Audit Events CSV ──────────────────────────────────────────────────────

    public byte[] auditEventsCsv(AuditorLink link) {
        List<AuditEventResponse> events = portalService.getAuditEvents(link);
        StringBuilder csv = new StringBuilder();
        csv.append("Occurred At,Actor,Action,Target DN\n");
        for (AuditEventResponse e : events) {
            csv.append(csvEscape(e.occurredAt() != null ? e.occurredAt().toString() : "")).append(',');
            csv.append(csvEscape(e.actorUsername() != null ? e.actorUsername() : "")).append(',');
            csv.append(csvEscape(e.action() != null ? e.action().name() : "")).append(',');
            csv.append(csvEscape(e.targetDn() != null ? e.targetDn() : "")).append('\n');
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── Audit Events PDF ──────────────────────────────────────────────────────

    public byte[] auditEventsPdf(AuditorLink link) throws IOException {
        List<AuditEventResponse> events = portalService.getAuditEvents(link);
        List<String> headers = List.of("When", "Actor", "Action", "Target");
        List<List<String>> rows = new ArrayList<>();
        for (AuditEventResponse e : events) {
            rows.add(List.of(
                    e.occurredAt() != null ? e.occurredAt().toString() : "",
                    e.actorUsername() != null ? e.actorUsername() : "",
                    e.action() != null ? e.action().name() : "",
                    e.targetDn() != null ? e.targetDn() : ""));
        }
        return pdfReportService.buildPdf("Audit Events", "Directory change log", headers, rows);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
