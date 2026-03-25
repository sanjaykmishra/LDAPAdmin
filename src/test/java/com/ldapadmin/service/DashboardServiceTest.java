package com.ldapadmin.service;

import com.ldapadmin.dto.audit.AuditEventResponse;
import com.ldapadmin.dto.dashboard.ComplianceDashboardDto;
import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.PendingApproval;
import com.ldapadmin.entity.enums.ApprovalStatus;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.entity.enums.SodViolationStatus;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private DirectoryConnectionRepository dirRepo;
    @Mock private PendingApprovalRepository approvalRepo;
    @Mock private AccessReviewCampaignRepository campaignRepo;
    @Mock private AccessReviewDecisionRepository decisionRepo;
    @Mock private SodViolationRepository sodViolationRepo;
    @Mock private AuditQueryService auditQueryService;
    @Mock private LdapUserService userService;
    @Mock private LdapGroupService groupService;
    @Mock private ScheduledReportJobRepository reportJobRepo;

    private DashboardService service;

    private DirectoryConnection directory;
    private final UUID directoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DashboardService(
                dirRepo, approvalRepo, campaignRepo, decisionRepo,
                sodViolationRepo, auditQueryService, userService, groupService, reportJobRepo);
        service.invalidateCache();

        directory = new DirectoryConnection();
        directory.setId(directoryId);
        directory.setDisplayName("Test Dir");
        directory.setEnabled(true);
    }

    @Test
    void getDashboard_returnsComplianceDashboardDto() {
        stubCommon();
        when(approvalRepo.countByDirectoryIdAndStatus(directoryId, ApprovalStatus.PENDING)).thenReturn(5L);
        when(approvalRepo.findAllByStatus(ApprovalStatus.PENDING)).thenReturn(List.of());

        ComplianceDashboardDto result = service.getDashboard();

        assertThat(result).isNotNull();
        assertThat(result.totalPendingApprovals()).isEqualTo(5);
        assertThat(result.openSodViolations()).isEqualTo(0);
        assertThat(result.directories()).hasSize(1);
        assertThat(result.directories().get(0).name()).isEqualTo("Test Dir");
    }

    @Test
    void getDashboard_campaignCompletionPercent_calculatedCorrectly() {
        stubCommon();

        AccessReviewCampaign campaign = new AccessReviewCampaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Q1 Review");
        campaign.setDirectory(directory);
        campaign.setDeadline(OffsetDateTime.now().plusDays(7));
        when(campaignRepo.countByDirectoryIdAndStatus(directoryId, CampaignStatus.ACTIVE)).thenReturn(1L);
        when(campaignRepo.findByStatus(CampaignStatus.ACTIVE)).thenReturn(List.of(campaign));

        when(decisionRepo.countTotalByCampaignId(campaign.getId())).thenReturn(100L);
        when(decisionRepo.countPendingByCampaignId(campaign.getId())).thenReturn(20L);

        ComplianceDashboardDto result = service.getDashboard();

        assertThat(result.campaignCompletionPercent()).isEqualTo(80.0);
        assertThat(result.campaignProgress()).hasSize(1);
        assertThat(result.campaignProgress().get(0).completionPercent()).isEqualTo(80.0);
        assertThat(result.campaignProgress().get(0).overdue()).isFalse();
    }

    @Test
    void getDashboard_overdueCampaign_flaggedCorrectly() {
        stubCommon();

        AccessReviewCampaign overdue = new AccessReviewCampaign();
        overdue.setId(UUID.randomUUID());
        overdue.setName("Overdue Review");
        overdue.setDirectory(directory);
        overdue.setDeadline(OffsetDateTime.now().minusDays(3));
        when(campaignRepo.countByDirectoryIdAndStatus(directoryId, CampaignStatus.ACTIVE)).thenReturn(1L);
        when(campaignRepo.findByStatus(CampaignStatus.ACTIVE)).thenReturn(List.of(overdue));
        when(campaignRepo.countByStatusAndDeadlineBefore(eq(CampaignStatus.ACTIVE), any())).thenReturn(1L);

        when(decisionRepo.countTotalByCampaignId(overdue.getId())).thenReturn(50L);
        when(decisionRepo.countPendingByCampaignId(overdue.getId())).thenReturn(30L);

        ComplianceDashboardDto result = service.getDashboard();

        assertThat(result.overdueCampaigns()).isEqualTo(1);
        assertThat(result.campaignProgress().get(0).overdue()).isTrue();
    }

    @Test
    void getDashboard_approvalAgingBuckets_computedCorrectly() {
        stubCommon();
        when(approvalRepo.countByDirectoryIdAndStatus(any(), any())).thenReturn(4L);

        OffsetDateTime now = OffsetDateTime.now();
        List<PendingApproval> approvals = List.of(
                buildApproval(now.minusHours(2)),
                buildApproval(now.minusDays(2)),
                buildApproval(now.minusDays(5)),
                buildApproval(now.minusDays(10))
        );
        when(approvalRepo.findAllByStatus(ApprovalStatus.PENDING)).thenReturn(approvals);

        ComplianceDashboardDto result = service.getDashboard();

        assertThat(result.approvalAging().lessThan24h()).isEqualTo(1);
        assertThat(result.approvalAging().oneToThreeDays()).isEqualTo(1);
        assertThat(result.approvalAging().threeToSevenDays()).isEqualTo(1);
        assertThat(result.approvalAging().moreThanSevenDays()).isEqualTo(1);
    }

    @Test
    void getDashboard_usersNotReviewedIn90Days_calculatedPerDirectory() {
        var users = new ArrayList<com.ldapadmin.ldap.model.LdapUser>();
        for (int i = 0; i < 50; i++) users.add(mock(com.ldapadmin.ldap.model.LdapUser.class));
        when(dirRepo.findAll()).thenReturn(List.of(directory));
        when(userService.searchUsers(eq(directory), anyString(), any(), anyInt(), anyString()))
                .thenReturn(users);
        when(groupService.searchGroups(eq(directory), anyString(), any(), anyInt(), anyString()))
                .thenReturn(List.of());
        when(approvalRepo.countByDirectoryIdAndStatus(any(), any())).thenReturn(0L);
        when(approvalRepo.findAllByStatus(any())).thenReturn(List.of());
        when(sodViolationRepo.countByDirectoryIdAndStatus(any(), any())).thenReturn(0L);
        when(sodViolationRepo.countByStatus(any())).thenReturn(0L);
        when(campaignRepo.countByDirectoryIdAndStatus(any(), any())).thenReturn(0L);
        when(campaignRepo.findByStatus(any())).thenReturn(List.of());
        when(campaignRepo.countByStatusAndDeadlineBefore(any(), any())).thenReturn(0L);
        when(auditQueryService.query(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));
        when(reportJobRepo.countByEnabledTrue()).thenReturn(0L);
        when(reportJobRepo.countByEnabledTrueAndLastRunStatus(any())).thenReturn(0L);
        when(decisionRepo.countDistinctReviewedUsersSince(eq(directoryId), any())).thenReturn(30L);

        ComplianceDashboardDto result = service.getDashboard();

        assertThat(result.usersNotReviewedIn90Days()).isEqualTo(20);
    }

    @Test
    void getDashboard_noCampaigns_returnsNullCompletion() {
        stubCommon();

        ComplianceDashboardDto result = service.getDashboard();

        assertThat(result.campaignCompletionPercent()).isNull();
        assertThat(result.campaignProgress()).isEmpty();
    }

    @Test
    void getDashboard_disabledDirectory_skipLdapCalls() {
        directory.setEnabled(false);
        when(dirRepo.findAll()).thenReturn(List.of(directory));
        when(approvalRepo.countByDirectoryIdAndStatus(any(), any())).thenReturn(0L);
        when(approvalRepo.findAllByStatus(any())).thenReturn(List.of());
        when(sodViolationRepo.countByDirectoryIdAndStatus(any(), any())).thenReturn(0L);
        when(sodViolationRepo.countByStatus(any())).thenReturn(0L);
        when(campaignRepo.countByDirectoryIdAndStatus(any(), any())).thenReturn(0L);
        when(campaignRepo.findByStatus(any())).thenReturn(List.of());
        when(campaignRepo.countByStatusAndDeadlineBefore(any(), any())).thenReturn(0L);
        when(auditQueryService.query(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));
        when(reportJobRepo.countByEnabledTrue()).thenReturn(0L);
        when(reportJobRepo.countByEnabledTrueAndLastRunStatus(any())).thenReturn(0L);

        ComplianceDashboardDto result = service.getDashboard();

        verify(userService, never()).searchUsers(any(), anyString(), any(), anyInt(), anyString());
        assertThat(result.directories().get(0).userCount()).isEqualTo(0);
    }

    @Test
    void getDashboard_perDirectorySodViolations_included() {
        stubCommon();
        when(sodViolationRepo.countByDirectoryIdAndStatus(directoryId, SodViolationStatus.OPEN))
                .thenReturn(3L);
        when(sodViolationRepo.countByStatus(SodViolationStatus.OPEN)).thenReturn(3L);

        ComplianceDashboardDto result = service.getDashboard();

        assertThat(result.openSodViolations()).isEqualTo(3);
        assertThat(result.directories().get(0).openSodViolations()).isEqualTo(3);
    }

    @Test
    void getDashboard_includesReportJobStats() {
        stubCommon();
        when(reportJobRepo.countByEnabledTrue()).thenReturn(5L);
        when(reportJobRepo.countByEnabledTrueAndLastRunStatus("FAILURE")).thenReturn(2L);

        ComplianceDashboardDto result = service.getDashboard();

        assertThat(result.enabledReportJobs()).isEqualTo(5);
        assertThat(result.failedReportJobs()).isEqualTo(2);
    }

    @Test
    void getDashboard_cacheReturnsCachedResult() {
        stubCommon();

        ComplianceDashboardDto first = service.getDashboard();
        ComplianceDashboardDto second = service.getDashboard();

        assertThat(second).isSameAs(first);
        verify(dirRepo, times(1)).findAll();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void stubCommon() {
        when(dirRepo.findAll()).thenReturn(List.of(directory));
        when(userService.searchUsers(eq(directory), anyString(), any(), anyInt(), anyString()))
                .thenReturn(List.of());
        when(groupService.searchGroups(eq(directory), anyString(), any(), anyInt(), anyString()))
                .thenReturn(List.of());
        when(approvalRepo.countByDirectoryIdAndStatus(any(), any())).thenReturn(0L);
        when(approvalRepo.findAllByStatus(any())).thenReturn(List.of());
        when(sodViolationRepo.countByDirectoryIdAndStatus(any(), any())).thenReturn(0L);
        when(sodViolationRepo.countByStatus(any())).thenReturn(0L);
        when(campaignRepo.countByDirectoryIdAndStatus(any(), any())).thenReturn(0L);
        when(campaignRepo.findByStatus(any())).thenReturn(List.of());
        when(campaignRepo.countByStatusAndDeadlineBefore(any(), any())).thenReturn(0L);
        when(decisionRepo.countDistinctReviewedUsersSince(any(), any())).thenReturn(0L);
        when(auditQueryService.query(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));
        when(reportJobRepo.countByEnabledTrue()).thenReturn(0L);
        when(reportJobRepo.countByEnabledTrueAndLastRunStatus(any())).thenReturn(0L);
    }

    private PendingApproval buildApproval(OffsetDateTime createdAt) {
        PendingApproval pa = new PendingApproval();
        pa.setId(UUID.randomUUID());
        pa.setStatus(ApprovalStatus.PENDING);
        pa.setCreatedAt(createdAt);
        return pa;
    }
}
