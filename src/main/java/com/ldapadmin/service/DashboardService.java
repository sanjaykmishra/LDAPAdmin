package com.ldapadmin.service;

import com.ldapadmin.dto.dashboard.ComplianceDashboardDto;
import com.ldapadmin.dto.dashboard.ComplianceDashboardDto.*;
import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.PendingApproval;
import com.ldapadmin.entity.enums.ApprovalStatus;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.entity.enums.SodViolationStatus;
import com.ldapadmin.ldap.LdapConnectionFactory;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardService {

    private final DirectoryConnectionRepository dirRepo;
    private final PendingApprovalRepository approvalRepo;
    private final AccessReviewCampaignRepository campaignRepo;
    private final AccessReviewDecisionRepository decisionRepo;
    private final SodViolationRepository sodViolationRepo;
    private final AuditQueryService auditQueryService;
    private final LdapUserService userService;
    private final LdapGroupService groupService;
    private final LdapConnectionFactory connectionFactory;

    private static final String GROUP_OBJECTCLASS_FILTER =
            "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup)(objectClass=group)(objectClass=groupOfURLs))";

    @Transactional(readOnly = true)
    public ComplianceDashboardDto getDashboard() {
        List<DirectoryConnection> dirs = dirRepo.findAll();
        OffsetDateTime now = OffsetDateTime.now();

        // ── Per-directory stats ──────────────────────────────────────────────
        List<DirectoryStatDto> dirStats = new ArrayList<>();
        long totalUsers = 0;
        long totalGroups = 0;
        long totalPending = 0;

        for (DirectoryConnection dc : dirs) {
            long userCount = 0;
            long groupCount = 0;

            if (dc.isEnabled()) {
                try {
                    userCount = userService.searchUsers(dc, "(objectClass=*)", null, Integer.MAX_VALUE, "1.1").size();
                } catch (Exception e) {
                    log.warn("Failed to count users for directory {}: {}", dc.getDisplayName(), e.getMessage());
                    userCount = -1;
                }
                try {
                    groupCount = groupService.searchGroups(dc, GROUP_OBJECTCLASS_FILTER, null, Integer.MAX_VALUE, "1.1").size();
                } catch (Exception e) {
                    log.warn("Failed to count groups for directory {}: {}", dc.getDisplayName(), e.getMessage());
                    groupCount = -1;
                }
            }

            long pending = approvalRepo.countByDirectoryIdAndStatus(dc.getId(), ApprovalStatus.PENDING);
            long activeCampaigns = campaignRepo.findByDirectoryIdAndStatus(dc.getId(), CampaignStatus.ACTIVE).size();
            long dirSodViolations = sodViolationRepo.findByDirectoryIdAndStatus(dc.getId(), SodViolationStatus.OPEN).size();

            if (userCount >= 0) totalUsers += userCount;
            if (groupCount >= 0) totalGroups += groupCount;
            totalPending += pending;

            dirStats.add(new DirectoryStatDto(
                    dc.getId().toString(), dc.getDisplayName(), dc.isEnabled(),
                    userCount, groupCount, pending, activeCampaigns, dirSodViolations));
        }

        // ── SoD violation count ──────────────────────────────────────────────
        long openSodViolations = sodViolationRepo.countByStatus(SodViolationStatus.OPEN);

        // ── Campaign completion % ────────────────────────────────────────────
        List<AccessReviewCampaign> activeCampaigns = campaignRepo.findByStatus(CampaignStatus.ACTIVE);
        List<CampaignProgressDto> campaignProgress = new ArrayList<>();
        long globalTotalDecisions = 0;
        long globalDecided = 0;

        for (AccessReviewCampaign c : activeCampaigns) {
            long total = decisionRepo.countTotalByCampaignId(c.getId());
            long pending = decisionRepo.countPendingByCampaignId(c.getId());
            long decided = total - pending;
            double pct = total > 0 ? Math.round((decided * 100.0 / total) * 10) / 10.0 : 0;
            boolean overdue = c.getDeadline() != null && c.getDeadline().isBefore(now);

            globalTotalDecisions += total;
            globalDecided += decided;

            campaignProgress.add(new CampaignProgressDto(
                    c.getId().toString(), c.getName(),
                    c.getDirectory().getDisplayName(),
                    total, decided, pct, overdue,
                    c.getDeadline() != null ? c.getDeadline().toString() : null));
        }

        double campaignCompletionPct = globalTotalDecisions > 0
                ? Math.round((globalDecided * 100.0 / globalTotalDecisions) * 10) / 10.0
                : 100.0;

        // ── Overdue campaigns ────────────────────────────────────────────────
        long overdueCampaigns = campaignRepo.countByStatusAndDeadlineBefore(CampaignStatus.ACTIVE, now);

        // ── Approval aging buckets ───────────────────────────────────────────
        ApprovalAgingDto approvalAging = computeApprovalAging(now);

        // ── Users not reviewed in 90 days ────────────────────────────────────
        long usersNotReviewedIn90Days = computeUsersNotReviewedIn90Days(dirs, totalUsers, now);

        // ── Recent audit events ──────────────────────────────────────────────
        var recentAudit = auditQueryService.query(null, null, null, null, null, 0, 10);

        return new ComplianceDashboardDto(
                totalUsers, totalGroups, totalPending,
                openSodViolations, campaignCompletionPct, overdueCampaigns,
                usersNotReviewedIn90Days,
                approvalAging, campaignProgress, dirStats,
                recentAudit.getContent());
    }

    private ApprovalAgingDto computeApprovalAging(OffsetDateTime now) {
        List<PendingApproval> pendingApprovals = approvalRepo.findAllByStatus(ApprovalStatus.PENDING);

        long lt24h = 0, d1to3 = 0, d3to7 = 0, gt7d = 0;
        for (PendingApproval pa : pendingApprovals) {
            Duration age = Duration.between(pa.getCreatedAt(), now);
            long hours = age.toHours();
            if (hours < 24) lt24h++;
            else if (hours < 72) d1to3++;
            else if (hours < 168) d3to7++;
            else gt7d++;
        }
        return new ApprovalAgingDto(lt24h, d1to3, d3to7, gt7d);
    }

    private long computeUsersNotReviewedIn90Days(List<DirectoryConnection> dirs, long totalUsers, OffsetDateTime now) {
        OffsetDateTime ninetyDaysAgo = now.minusDays(90);
        long reviewedUsers = 0;
        for (DirectoryConnection dc : dirs) {
            if (dc.isEnabled()) {
                try {
                    reviewedUsers += decisionRepo.countDistinctReviewedUsersSince(dc.getId(), ninetyDaysAgo);
                } catch (Exception e) {
                    log.warn("Failed to count reviewed users for directory {}: {}", dc.getDisplayName(), e.getMessage());
                }
            }
        }
        return Math.max(0, totalUsers - reviewedUsers);
    }
}
