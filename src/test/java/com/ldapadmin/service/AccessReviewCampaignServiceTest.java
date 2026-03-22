package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.dto.accessreview.CreateCampaignRequest;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.entity.enums.ReviewDecision;
import com.ldapadmin.exception.LdapAdminException;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapGroup;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessReviewCampaignServiceTest {

    @Mock private AccessReviewCampaignRepository campaignRepo;
    @Mock private AccessReviewGroupRepository groupRepo;
    @Mock private AccessReviewDecisionRepository decisionRepo;
    @Mock private AccessReviewCampaignHistoryRepository historyRepo;
    @Mock private LdapGroupService ldapGroupService;
    @Mock private LdapUserService ldapUserService;
    @Mock private DirectoryConnectionRepository directoryRepo;
    @Mock private AccountRepository accountRepo;
    @Mock private AuditService auditService;
    @Mock private AccessReviewNotificationService notificationService;

    private AccessReviewCampaignService service;

    private final UUID directoryId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID reviewerId = UUID.randomUUID();
    private final AuthPrincipal principal = new AuthPrincipal(PrincipalType.SUPERADMIN, adminId, "admin");

    private DirectoryConnection directory;
    private Account adminAccount;
    private Account reviewerAccount;

    @BeforeEach
    void setUp() {
        service = new AccessReviewCampaignService(
                campaignRepo, groupRepo, decisionRepo, historyRepo,
                ldapGroupService, ldapUserService, directoryRepo, accountRepo,
                auditService, notificationService);

        directory = new DirectoryConnection();
        directory.setId(directoryId);
        directory.setDisplayName("Test Directory");

        adminAccount = new Account();
        adminAccount.setId(adminId);
        adminAccount.setUsername("admin");

        reviewerAccount = new Account();
        reviewerAccount.setId(reviewerId);
        reviewerAccount.setUsername("reviewer");
    }

    @Test
    void create_validRequest_createsDraftCampaign() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount));
        when(accountRepo.findById(reviewerId)).thenReturn(Optional.of(reviewerAccount));
        when(ldapGroupService.getGroup(any(), any(), any()))
                .thenReturn(new LdapGroup("cn=admins,dc=test", Map.of("cn", List.of("admins"))));
        when(campaignRepo.save(any())).thenAnswer(inv -> {
            AccessReviewCampaign c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        var groups = List.of(new CreateCampaignRequest.GroupAssignment(
                "cn=admins,dc=test", "member", reviewerId));
        var req = new CreateCampaignRequest("Q1 Review", "Test", null,
                OffsetDateTime.now().plusDays(30), false, false, groups);

        AccessReviewCampaign result = service.create(directoryId, req, principal);

        assertThat(result.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        assertThat(result.getName()).isEqualTo("Q1 Review");
        assertThat(result.getReviewGroups()).hasSize(1);
        verify(historyRepo).save(any());
        verify(auditService).record(eq(principal), eq(directoryId), any(), any(), any());
    }

    @Test
    void create_pastDeadline_throwsException() {
        when(directoryRepo.findById(directoryId)).thenReturn(Optional.of(directory));

        var groups = List.of(new CreateCampaignRequest.GroupAssignment(
                "cn=admins,dc=test", "member", reviewerId));
        var req = new CreateCampaignRequest("Q1 Review", null, null,
                OffsetDateTime.now().minusDays(1), false, false, groups);

        assertThatThrownBy(() -> service.create(directoryId, req, principal))
                .isInstanceOf(LdapAdminException.class)
                .hasMessageContaining("future");
    }

    @Test
    void activate_draftCampaign_snapshotsMembersAndNotifies() {
        AccessReviewCampaign campaign = buildDraftCampaign();
        when(campaignRepo.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount));
        when(ldapGroupService.getMembers(any(), any(), any()))
                .thenReturn(List.of("uid=user1,dc=test", "uid=user2,dc=test"));
        when(ldapUserService.getUser(any(), any(), any()))
                .thenReturn(new LdapUser("uid=user1,dc=test", Map.of("cn", List.of("User One"))));
        when(campaignRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessReviewCampaign result = service.activate(campaign.getId(), principal);

        assertThat(result.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(result.getReviewGroups().get(0).getDecisions()).hasSize(2);
        verify(notificationService).notifyReviewersAssigned(campaign);
    }

    @Test
    void activate_nonDraftCampaign_throwsException() {
        AccessReviewCampaign campaign = buildDraftCampaign();
        campaign.setStatus(CampaignStatus.ACTIVE);
        when(campaignRepo.findById(campaign.getId())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.activate(campaign.getId(), principal))
                .isInstanceOf(LdapAdminException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void close_withPendingItems_requiresForce() {
        AccessReviewCampaign campaign = buildActiveCampaign();
        when(campaignRepo.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(decisionRepo.countPendingByCampaignId(campaign.getId())).thenReturn(5L);

        assertThatThrownBy(() -> service.close(campaign.getId(), false, principal))
                .isInstanceOf(LdapAdminException.class)
                .hasMessageContaining("undecided");
    }

    @Test
    void close_forceClose_succeeds() {
        AccessReviewCampaign campaign = buildActiveCampaign();
        when(campaignRepo.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount));
        when(decisionRepo.countPendingByCampaignId(campaign.getId())).thenReturn(5L);
        when(campaignRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessReviewCampaign result = service.close(campaign.getId(), true, principal);

        assertThat(result.getStatus()).isEqualTo(CampaignStatus.CLOSED);
        assertThat(result.getCompletedAt()).isNotNull();
        verify(notificationService).notifyCampaignClosed(campaign);
    }

    @Test
    void cancel_activeCampaign_cancels() {
        AccessReviewCampaign campaign = buildActiveCampaign();
        when(campaignRepo.findById(campaign.getId())).thenReturn(Optional.of(campaign));
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount));
        when(campaignRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessReviewCampaign result = service.cancel(campaign.getId(), principal);

        assertThat(result.getStatus()).isEqualTo(CampaignStatus.CANCELLED);
    }

    @Test
    void cancel_closedCampaign_throwsException() {
        AccessReviewCampaign campaign = buildActiveCampaign();
        campaign.setStatus(CampaignStatus.CLOSED);
        when(campaignRepo.findById(campaign.getId())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service.cancel(campaign.getId(), principal))
                .isInstanceOf(LdapAdminException.class)
                .hasMessageContaining("DRAFT or ACTIVE");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private AccessReviewCampaign buildDraftCampaign() {
        AccessReviewCampaign c = new AccessReviewCampaign();
        c.setId(UUID.randomUUID());
        c.setDirectory(directory);
        c.setName("Test Campaign");
        c.setStatus(CampaignStatus.DRAFT);
        c.setDeadline(OffsetDateTime.now().plusDays(30));
        c.setCreatedBy(adminAccount);

        AccessReviewGroup g = new AccessReviewGroup();
        g.setId(UUID.randomUUID());
        g.setCampaign(c);
        g.setGroupDn("cn=admins,dc=test");
        g.setGroupName("admins");
        g.setMemberAttribute("member");
        g.setReviewer(reviewerAccount);
        c.setReviewGroups(new ArrayList<>(List.of(g)));

        return c;
    }

    private AccessReviewCampaign buildActiveCampaign() {
        AccessReviewCampaign c = buildDraftCampaign();
        c.setStatus(CampaignStatus.ACTIVE);
        return c;
    }
}
