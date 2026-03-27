package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PermissionService;
import com.ldapadmin.dto.audit.AuditEventResponse;
import com.ldapadmin.dto.dashboard.AdminDashboardDto;
import com.ldapadmin.dto.dashboard.AdminDashboardDto.*;
import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.PendingApproval;
import com.ldapadmin.entity.enums.ApprovalStatus;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.entity.enums.SodViolationStatus;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Builds the admin dashboard, scoped to the admin's authorized directories.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminDashboardService {

    private final DirectoryConnectionRepository dirRepo;
    private final PendingApprovalRepository approvalRepo;
    private final AccessReviewCampaignRepository campaignRepo;
    private final AccessReviewDecisionRepository decisionRepo;
    private final SodViolationRepository sodViolationRepo;
    private final AuditQueryService auditQueryService;
    private final LdapUserService userService;
    private final LdapGroupService groupService;
    private final PermissionService permissionService;

    private static final String USER_OBJECTCLASS_FILTER =
            "(|(objectClass=inetOrgPerson)(&(objectClass=user)(!(objectClass=computer))))";

    private static final String GROUP_OBJECTCLASS_FILTER =
            "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup)(objectClass=group)(objectClass=groupOfURLs))";

    private static final int MAX_COUNT = 100_000;

    @Transactional(readOnly = true)
    public AdminDashboardDto getDashboard(AuthPrincipal principal) {
        Set<UUID> authorizedDirIds = permissionService.getAuthorizedDirectoryIds(principal);

        if (authorizedDirIds.isEmpty()) {
            return emptyDashboard();
        }

        List<DirectoryConnection> dirs = dirRepo.findAllById(authorizedDirIds);
        OffsetDateTime now = OffsetDateTime.now();

        // ── Per-directory stats ──────────────────────────────────────────────
        List<DirectoryStatDto> dirStats = new ArrayList<>();
        long totalUsers = 0;
        long totalGroups = 0;
        long totalPending = 0;
        long totalActiveCampaigns = 0;
        long totalSodViolations = 0;

        for (DirectoryConnection dc : dirs) {
            long userCount = 0;
            long groupCount = 0;

            if (dc.isEnabled()) {
                try {
                    userCount = userService.searchUsers(dc, USER_OBJECTCLASS_FILTER, null, MAX_COUNT, "1.1").size();
                } catch (Exception e) {
                    log.warn("Failed to count users for directory {}: {}", dc.getDisplayName(), e.getMessage());
                    userCount = -1;
                }
                try {
                    groupCount = groupService.searchGroups(dc, GROUP_OBJECTCLASS_FILTER, null, MAX_COUNT, "1.1").size();
                } catch (Exception e) {
                    log.warn("Failed to count groups for directory {}: {}", dc.getDisplayName(), e.getMessage());
                    groupCount = -1;
                }
            }

            long pending = approvalRepo.countByDirectoryIdAndStatus(dc.getId(), ApprovalStatus.PENDING);
            long activeCampaigns = campaignRepo.countByDirectoryIdAndStatus(dc.getId(), CampaignStatus.ACTIVE);
            long dirSodViolations = sodViolationRepo.countByDirectoryIdAndStatus(dc.getId(), SodViolationStatus.OPEN);

            if (userCount >= 0) totalUsers += userCount;
            if (groupCount >= 0) totalGroups += groupCount;
            totalPending += pending;
            totalActiveCampaigns += activeCampaigns;
            totalSodViolations += dirSodViolations;

            dirStats.add(new DirectoryStatDto(
                    dc.getId().toString(), dc.getDisplayName(), dc.isEnabled(),
                    userCount, groupCount, pending, activeCampaigns, dirSodViolations));
        }

        // ── Campaign completion ──────────────────────────────────────────────
        List<CampaignProgressDto> campaignProgress = new ArrayList<>();
        long globalTotalDecisions = 0;
        long globalDecided = 0;

        for (UUID dirId : authorizedDirIds) {
            List<AccessReviewCampaign> activeCampaigns =
                    campaignRepo.findByDirectoryIdAndStatus(dirId, CampaignStatus.ACTIVE);

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
        }

        Double campaignCompletionPct = campaignProgress.isEmpty() ? null
                : globalTotalDecisions > 0
                    ? Math.round((globalDecided * 100.0 / globalTotalDecisions) * 10) / 10.0
                    : 0.0;

        // ── Overdue campaigns ────────────────────────────────────────────────
        long overdueCampaigns = campaignProgress.stream().filter(CampaignProgressDto::overdue).count();

        // ── Approval aging ───────────────────────────────────────────────────
        ApprovalAgingDto approvalAging = computeApprovalAging(authorizedDirIds, now);

        // ── Recent activity ──────────────────────────────────────────────────
        var recentAudit = auditQueryService.queryForDirectories(
                authorizedDirIds, null, null, null, null, null, 0, 10);

        String firstDirId = dirs.isEmpty() ? null : dirs.get(0).getId().toString();

        return new AdminDashboardDto(
                totalUsers, totalGroups, totalPending,
                totalSodViolations, totalActiveCampaigns, campaignCompletionPct, overdueCampaigns,
                approvalAging, campaignProgress, dirStats,
                recentAudit.getContent(),
                firstDirId);
    }

    private ApprovalAgingDto computeApprovalAging(Set<UUID> directoryIds, OffsetDateTime now) {
        long lt24h = 0, d1to3 = 0, d3to7 = 0, gt7d = 0;

        for (UUID dirId : directoryIds) {
            List<PendingApproval> pendingApprovals =
                    approvalRepo.findAllByDirectoryIdAndStatus(dirId, ApprovalStatus.PENDING);

            for (PendingApproval pa : pendingApprovals) {
                Duration age = Duration.between(pa.getCreatedAt(), now);
                long hours = age.toHours();
                if (hours < 24) lt24h++;
                else if (hours < 72) d1to3++;
                else if (hours < 168) d3to7++;
                else gt7d++;
            }
        }
        return new ApprovalAgingDto(lt24h, d1to3, d3to7, gt7d);
    }

    private AdminDashboardDto emptyDashboard() {
        return new AdminDashboardDto(
                0, 0, 0, 0, 0, null, 0,
                new ApprovalAgingDto(0, 0, 0, 0),
                List.of(), List.of(), List.of(), null);
    }
}
