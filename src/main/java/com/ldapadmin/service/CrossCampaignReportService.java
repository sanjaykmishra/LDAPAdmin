package com.ldapadmin.service;

import com.ldapadmin.dto.accessreview.*;
import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.AccessReviewDecision;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.entity.enums.ReviewDecision;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AccessReviewCampaignRepository;
import com.ldapadmin.repository.AccessReviewDecisionRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CrossCampaignReportService {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm Z");
    private static final Font TITLE_FONT =
            new Font(Font.HELVETICA, 18, Font.BOLD, new Color(33, 37, 41));
    private static final Font SUBTITLE_FONT =
            new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(108, 117, 125));
    private static final Font HEADER_FONT =
            new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    private static final Font CELL_FONT =
            new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(33, 37, 41));
    private static final Font SECTION_FONT =
            new Font(Font.HELVETICA, 13, Font.BOLD, new Color(33, 37, 41));
    private static final Color HEADER_BG = new Color(52, 58, 64);
    private static final Color ALT_ROW_BG = new Color(248, 249, 250);

    private final AccessReviewCampaignRepository campaignRepo;
    private final AccessReviewDecisionRepository decisionRepo;
    private final DirectoryConnectionRepository directoryRepo;
    private final ApplicationSettingsService settingsService;

    @Transactional(readOnly = true)
    public CrossCampaignReportDto generateReport(UUID directoryId, OffsetDateTime from,
                                                  OffsetDateTime to, CampaignStatus statusFilter) {
        directoryRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));

        List<AccessReviewCampaign> campaigns = campaignRepo.findByDirectoryIdAndCreatedAtBetween(
                directoryId, from, to);

        if (statusFilter != null) {
            campaigns = campaigns.stream()
                    .filter(c -> c.getStatus() == statusFilter)
                    .toList();
        }

        // Per-campaign metrics
        List<CampaignMetricRow> campaignRows = campaigns.stream()
                .map(this::toCampaignMetricRow)
                .toList();

        // Aggregate counts
        long totalDecisions = campaignRows.stream().mapToLong(CampaignMetricRow::total).sum();
        long totalConfirmed = campaignRows.stream().mapToLong(CampaignMetricRow::confirmed).sum();
        long totalRevoked = campaignRows.stream().mapToLong(CampaignMetricRow::revoked).sum();
        long totalPending = campaignRows.stream().mapToLong(CampaignMetricRow::pending).sum();
        double revocationRate = (totalConfirmed + totalRevoked) > 0
                ? ((double) totalRevoked / (totalConfirmed + totalRevoked)) * 100.0 : 0.0;

        // Average completion time for closed/expired campaigns
        Double avgCompletionDays = campaignRows.stream()
                .filter(r -> r.durationDays() != null)
                .mapToLong(CampaignMetricRow::durationDays)
                .average()
                .stream().boxed().findFirst().orElse(null);

        // Status breakdown
        Map<String, Long> statusBreakdown = campaigns.stream()
                .collect(Collectors.groupingBy(c -> c.getStatus().name(), Collectors.counting()));

        // Reviewer metrics
        List<ReviewerMetricRow> reviewerRows = buildReviewerMetrics(campaigns);

        return new CrossCampaignReportDto(
                from, to,
                campaigns.size(), statusBreakdown,
                totalDecisions, totalConfirmed, totalRevoked, totalPending,
                revocationRate, avgCompletionDays,
                campaignRows, reviewerRows);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(UUID directoryId, OffsetDateTime from,
                            OffsetDateTime to, CampaignStatus statusFilter) {
        CrossCampaignReportDto report = generateReport(directoryId, from, to, statusFilter);

        StringBuilder csv = new StringBuilder();
        csv.append("Campaign,Status,Activated,Completed,Duration Days,Total,Confirmed,Revoked,Pending,Completion %\n");

        for (CampaignMetricRow row : report.campaigns()) {
            csv.append(escapeCsv(row.name())).append(',');
            csv.append(row.status().name()).append(',');
            csv.append(row.activatedAt() != null ? row.activatedAt().toString() : "").append(',');
            csv.append(row.completedAt() != null ? row.completedAt().toString() : "").append(',');
            csv.append(row.durationDays() != null ? row.durationDays() : "").append(',');
            csv.append(row.total()).append(',');
            csv.append(row.confirmed()).append(',');
            csv.append(row.revoked()).append(',');
            csv.append(row.pending()).append(',');
            csv.append(String.format("%.1f", row.percentComplete()));
            csv.append('\n');
        }

        csv.append("\nReviewer,Total Decisions,Confirmed,Revoked,Revocation Rate %,Avg Response Hours\n");
        for (ReviewerMetricRow row : report.reviewers()) {
            csv.append(escapeCsv(row.username())).append(',');
            csv.append(row.totalDecisions()).append(',');
            csv.append(row.confirmed()).append(',');
            csv.append(row.revoked()).append(',');
            csv.append(String.format("%.1f", row.revocationRate())).append(',');
            csv.append(row.avgResponseHours() != null ? String.format("%.1f", row.avgResponseHours()) : "");
            csv.append('\n');
        }

        return csv.toString().getBytes();
    }

    @Transactional(readOnly = true)
    public byte[] exportPdf(UUID directoryId, OffsetDateTime from,
                            OffsetDateTime to, CampaignStatus statusFilter) throws IOException {
        CrossCampaignReportDto report = generateReport(directoryId, from, to, statusFilter);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            var settings = settingsService.getEntity();
            String appName = settings.getAppName() != null ? settings.getAppName() : "LDAP Portal";

            Paragraph titlePara = new Paragraph("Cross-Campaign Report", TITLE_FONT);
            titlePara.setSpacingAfter(4);
            document.add(titlePara);

            String dateRange = from.format(DISPLAY_FMT) + " — " + to.format(DISPLAY_FMT);
            Paragraph subtitlePara = new Paragraph(
                    appName + "  |  " + dateRange + "  |  Generated: "
                            + OffsetDateTime.now().format(DISPLAY_FMT), SUBTITLE_FONT);
            subtitlePara.setSpacingAfter(12);
            document.add(subtitlePara);

            // Summary section
            addSection(document, "Summary",
                    List.of("Metric", "Value"),
                    List.of(
                            List.of("Total Campaigns", String.valueOf(report.totalCampaigns())),
                            List.of("Total Decisions", String.valueOf(report.totalDecisions())),
                            List.of("Confirmed", String.valueOf(report.totalConfirmed())),
                            List.of("Revoked", String.valueOf(report.totalRevoked())),
                            List.of("Pending", String.valueOf(report.totalPending())),
                            List.of("Revocation Rate", String.format("%.1f%%", report.overallRevocationRate())),
                            List.of("Avg Completion Days",
                                    report.avgCompletionDays() != null
                                            ? String.format("%.1f", report.avgCompletionDays()) : "N/A")
                    ));

            // Campaigns table
            List<List<String>> campaignRows = report.campaigns().stream()
                    .map(r -> List.of(
                            safe(r.name()), r.status().name(),
                            String.valueOf(r.total()), String.valueOf(r.confirmed()),
                            String.valueOf(r.revoked()), String.valueOf(r.pending()),
                            String.format("%.1f%%", r.percentComplete()),
                            r.durationDays() != null ? r.durationDays() + "d" : "—"))
                    .toList();
            addSection(document, "Campaigns",
                    List.of("Name", "Status", "Total", "Confirmed", "Revoked", "Pending", "Complete", "Duration"),
                    campaignRows);

            // Reviewers table
            List<List<String>> reviewerRows = report.reviewers().stream()
                    .map(r -> List.of(
                            safe(r.username()),
                            String.valueOf(r.totalDecisions()),
                            String.valueOf(r.confirmed()),
                            String.valueOf(r.revoked()),
                            String.format("%.1f%%", r.revocationRate()),
                            r.avgResponseHours() != null ? String.format("%.1fh", r.avgResponseHours()) : "—"))
                    .toList();
            addSection(document, "Reviewers",
                    List.of("Reviewer", "Total", "Confirmed", "Revoked", "Revocation Rate", "Avg Response"),
                    reviewerRows);

            document.close();
        } catch (DocumentException e) {
            throw new IOException("Failed to generate PDF: " + e.getMessage(), e);
        }

        return baos.toByteArray();
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private CampaignMetricRow toCampaignMetricRow(AccessReviewCampaign c) {
        long total = decisionRepo.countTotalByCampaignId(c.getId());
        long confirmed = decisionRepo.countByCampaignIdAndDecision(c.getId(), ReviewDecision.CONFIRM);
        long revoked = decisionRepo.countByCampaignIdAndDecision(c.getId(), ReviewDecision.REVOKE);
        long pending = total - confirmed - revoked;
        double pct = total > 0 ? ((double) (confirmed + revoked) / total) * 100.0 : 0.0;

        Long durationDays = null;
        if (c.getCompletedAt() != null && c.getStartsAt() != null) {
            durationDays = Duration.between(c.getStartsAt(), c.getCompletedAt()).toDays();
        }

        return new CampaignMetricRow(
                c.getId(), c.getName(), c.getStatus(),
                c.getStartsAt(), c.getCompletedAt(), durationDays,
                total, confirmed, revoked, pending, pct);
    }

    List<ReviewerMetricRow> buildReviewerMetrics(List<AccessReviewCampaign> campaigns) {
        List<UUID> campaignIds = campaigns.stream().map(AccessReviewCampaign::getId).toList();
        if (campaignIds.isEmpty()) {
            return List.of();
        }

        List<AccessReviewDecision> decided = decisionRepo.findDecidedByCampaignIds(campaignIds);

        // Build a map of campaign activation times for response time calculation
        Map<UUID, OffsetDateTime> campaignStartTimes = campaigns.stream()
                .filter(c -> c.getStartsAt() != null)
                .collect(Collectors.toMap(AccessReviewCampaign::getId, AccessReviewCampaign::getStartsAt));

        // Group by reviewer
        Map<UUID, List<AccessReviewDecision>> byReviewer = decided.stream()
                .filter(d -> d.getDecidedBy() != null)
                .collect(Collectors.groupingBy(d -> d.getDecidedBy().getId()));

        return byReviewer.entrySet().stream()
                .map(entry -> {
                    UUID reviewerId = entry.getKey();
                    List<AccessReviewDecision> decisions = entry.getValue();
                    String username = decisions.get(0).getDecidedBy().getUsername();

                    long conf = decisions.stream().filter(d -> d.getDecision() == ReviewDecision.CONFIRM).count();
                    long rev = decisions.stream().filter(d -> d.getDecision() == ReviewDecision.REVOKE).count();
                    long total = conf + rev;
                    double revokeRate = total > 0 ? ((double) rev / total) * 100.0 : 0.0;

                    // Average response time: decidedAt - campaign startsAt
                    Double avgResponseHours = decisions.stream()
                            .filter(d -> d.getDecidedAt() != null)
                            .mapToDouble(d -> {
                                UUID cId = d.getReviewGroup().getCampaign().getId();
                                OffsetDateTime start = campaignStartTimes.get(cId);
                                if (start == null || d.getDecidedAt().isBefore(start)) return 0;
                                return Duration.between(start, d.getDecidedAt()).toHours();
                            })
                            .average()
                            .stream().boxed().findFirst().orElse(null);

                    return new ReviewerMetricRow(reviewerId, username, total, conf, rev, revokeRate, avgResponseHours);
                })
                .sorted(Comparator.comparingLong(ReviewerMetricRow::totalDecisions).reversed())
                .toList();
    }

    private void addSection(Document document, String title, List<String> headers,
                            List<List<String>> rows) throws DocumentException {
        Paragraph sectionPara = new Paragraph(title, SECTION_FONT);
        sectionPara.setSpacingBefore(12);
        sectionPara.setSpacingAfter(6);
        document.add(sectionPara);

        PdfPTable table = new PdfPTable(headers.size());
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);

        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, HEADER_FONT));
            cell.setBackgroundColor(HEADER_BG);
            cell.setPadding(6);
            table.addCell(cell);
        }

        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Color bg = (r % 2 == 1) ? ALT_ROW_BG : Color.WHITE;
            for (int col = 0; col < headers.size(); col++) {
                String value = col < row.size() ? row.get(col) : "";
                PdfPCell cell = new PdfPCell(new Phrase(value, CELL_FONT));
                cell.setBackgroundColor(bg);
                cell.setPadding(5);
                table.addCell(cell);
            }
        }

        if (rows.isEmpty()) {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No data available", CELL_FONT));
            emptyCell.setColspan(headers.size());
            emptyCell.setPadding(8);
            emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(emptyCell);
        }

        document.add(table);
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
