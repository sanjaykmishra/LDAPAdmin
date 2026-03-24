package com.ldapadmin.dto.dashboard;

import java.util.List;

/**
 * Structured compliance posture dashboard response.
 * Replaces the raw Map returned by the original dashboard endpoint.
 */
public record ComplianceDashboardDto(
        // ── Summary totals ────────────────────────────────────────────────────
        long totalUsers,
        long totalGroups,
        long totalPendingApprovals,

        // ── Compliance posture metrics ────────────────────────────────────────
        long openSodViolations,
        double campaignCompletionPercent,
        long overdueCampaigns,
        long usersNotReviewedIn90Days,

        // ── Approval aging buckets ────────────────────────────────────────────
        ApprovalAgingDto approvalAging,

        // ── Active campaign progress ──────────────────────────────────────────
        List<CampaignProgressDto> campaignProgress,

        // ── Per-directory breakdown ───────────────────────────────────────────
        List<DirectoryStatDto> directories,

        // ── Recent audit events ───────────────────────────────────────────────
        List<?> recentAudit
) {

    public record ApprovalAgingDto(
            long lessThan24h,
            long oneToThreeDays,
            long threeToSevenDays,
            long moreThanSevenDays
    ) {
        public long total() {
            return lessThan24h + oneToThreeDays + threeToSevenDays + moreThanSevenDays;
        }
    }

    public record CampaignProgressDto(
            String campaignId,
            String campaignName,
            String directoryName,
            long totalDecisions,
            long decidedCount,
            double completionPercent,
            boolean overdue,
            String deadline
    ) {}

    public record DirectoryStatDto(
            String id,
            String name,
            boolean enabled,
            long userCount,
            long groupCount,
            long pendingApprovals,
            long activeCampaigns,
            long openSodViolations
    ) {}
}
