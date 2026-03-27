package com.ldapadmin.dto.dashboard;

import com.ldapadmin.dto.audit.AuditEventResponse;

import java.util.List;

/**
 * Dashboard response scoped to the admin's authorized directories.
 */
public record AdminDashboardDto(

        // ── Summary totals (across authorized directories) ──────────────────
        long totalUsers,
        long totalGroups,
        long totalPendingApprovals,

        // ── Compliance indicators ───────────────────────────────────────────
        long openSodViolations,
        long activeAccessReviewCampaigns,
        Double campaignCompletionPercent,
        long overdueCampaigns,

        // ── Approval aging ──────────────────────────────────────────────────
        ApprovalAgingDto approvalAging,

        // ── Active campaign progress ────────────────────────────────────────
        List<CampaignProgressDto> campaignProgress,

        // ── Per-directory breakdown ─────────────────────────────────────────
        List<DirectoryStatDto> directories,

        // ── Recent activity (across authorized directories) ─────────────────
        List<AuditEventResponse> recentActivity,

        // ── Quick-action context ────────────────────────────────────────────
        String firstDirectoryId
) {

    /** Re-use the same nested records from ComplianceDashboardDto. */
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
