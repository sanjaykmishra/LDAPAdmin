package com.ldapadmin.service;

import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.*;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.repository.AccessReviewCampaignRepository;
import com.ldapadmin.repository.AccessReviewDecisionRepository;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.CampaignReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessReviewSchedulerTest {

    @Mock private AccessReviewCampaignRepository campaignRepo;
    @Mock private AccessReviewCampaignService campaignService;
    @Mock private AccessReviewNotificationService notificationService;
    @Mock private AccountRepository accountRepo;
    @Mock private CampaignReminderRepository reminderRepo;
    @Mock private AccessReviewDecisionRepository decisionRepo;
    @Mock private LdapGroupService ldapGroupService;
    @Mock private AuditService auditService;

    private AccessReviewScheduler scheduler;

    private Account superadmin;

    @BeforeEach
    void setUp() {
        scheduler = new AccessReviewScheduler(
                campaignRepo, campaignService, notificationService, accountRepo,
                reminderRepo, decisionRepo, ldapGroupService, auditService);

        superadmin = new Account();
        superadmin.setId(UUID.randomUUID());
        superadmin.setUsername("admin");
        superadmin.setRole(AccountRole.SUPERADMIN);
        superadmin.setActive(true);
    }

    @Test
    void processDeadlines_expiresOverdueCampaigns() {
        AccessReviewCampaign overdue = buildCampaign(CampaignStatus.ACTIVE, OffsetDateTime.now().minusDays(1));
        when(campaignRepo.findByStatusAndDeadlineBefore(eq(CampaignStatus.ACTIVE), any()))
                .thenReturn(List.of(overdue))
                .thenReturn(List.of()) // reminders
                .thenReturn(List.of()); // escalation
        when(accountRepo.findAllByRoleAndActiveTrue(AccountRole.SUPERADMIN)).thenReturn(List.of(superadmin));

        scheduler.processDeadlines();

        verify(campaignService).expireCampaign(eq(overdue), any());
    }

    @Test
    void processDeadlines_expiresRecurringCampaign_createsFollowUp() {
        AccessReviewCampaign overdue = buildCampaign(CampaignStatus.ACTIVE, OffsetDateTime.now().minusDays(1));
        overdue.setRecurrenceMonths(3);
        overdue.setDeadlineDays(30);
        when(campaignRepo.findByStatusAndDeadlineBefore(eq(CampaignStatus.ACTIVE), any()))
                .thenReturn(List.of(overdue))
                .thenReturn(List.of())
                .thenReturn(List.of());
        when(accountRepo.findAllByRoleAndActiveTrue(AccountRole.SUPERADMIN)).thenReturn(List.of(superadmin));

        scheduler.processDeadlines();

        verify(campaignService).expireCampaign(eq(overdue), any());
        verify(campaignService).createRecurringFollowUp(eq(overdue), any());
    }

    @Test
    void processDeadlines_sendsRemindersForApproachingDeadlines() {
        AccessReviewCampaign approaching = buildCampaignWithReviewers(
                CampaignStatus.ACTIVE, OffsetDateTime.now().plusDays(2));

        when(campaignRepo.findByStatusAndDeadlineBefore(eq(CampaignStatus.ACTIVE), any()))
                .thenReturn(List.of())   // overdue check
                .thenReturn(List.of(approaching)) // reminder check
                .thenReturn(List.of());  // escalation check
        when(accountRepo.findAllByRoleAndActiveTrue(AccountRole.SUPERADMIN)).thenReturn(List.of(superadmin));
        when(decisionRepo.countByReviewGroupIdAndDecisionIsNull(any())).thenReturn(5L);
        when(reminderRepo.existsByCampaignIdAndReviewerAccountIdAndReminderType(any(), any(), eq(ReminderType.DEADLINE)))
                .thenReturn(false);

        scheduler.processDeadlines();

        verify(notificationService).notifyDeadlineApproaching(any());
        verify(reminderRepo).save(any(CampaignReminder.class));
    }

    @Test
    void processDeadlines_doesNotSendDuplicateReminders() {
        AccessReviewCampaign approaching = buildCampaignWithReviewers(
                CampaignStatus.ACTIVE, OffsetDateTime.now().plusDays(2));

        when(campaignRepo.findByStatusAndDeadlineBefore(eq(CampaignStatus.ACTIVE), any()))
                .thenReturn(List.of())
                .thenReturn(List.of(approaching))
                .thenReturn(List.of());
        when(accountRepo.findAllByRoleAndActiveTrue(AccountRole.SUPERADMIN)).thenReturn(List.of(superadmin));
        when(decisionRepo.countByReviewGroupIdAndDecisionIsNull(any())).thenReturn(5L);
        // Already sent
        when(reminderRepo.existsByCampaignIdAndReviewerAccountIdAndReminderType(any(), any(), eq(ReminderType.DEADLINE)))
                .thenReturn(true);

        scheduler.processDeadlines();

        verify(notificationService, never()).notifyDeadlineApproaching(any());
        verify(reminderRepo, never()).save(any());
    }

    @Test
    void processDeadlines_noOverdueCampaigns_doesNothing() {
        when(campaignRepo.findByStatusAndDeadlineBefore(eq(CampaignStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(accountRepo.findAllByRoleAndActiveTrue(AccountRole.SUPERADMIN)).thenReturn(List.of(superadmin));

        scheduler.processDeadlines();

        verify(campaignService, never()).expireCampaign(any(), any());
    }

    // ── Escalation tests ──────────────────────────────────────────────────

    @Test
    void processEscalations_sendsEscalationAfterThreshold() {
        AccessReviewCampaign campaign = buildCampaignWithReviewers(
                CampaignStatus.ACTIVE, OffsetDateTime.now().plusDays(5));
        // Campaign was started 15 days ago (beyond default 14-day threshold)
        campaign.setCreatedAt(OffsetDateTime.now().minusDays(15));

        when(decisionRepo.countByReviewGroupIdAndDecisionIsNull(any())).thenReturn(3L);
        when(reminderRepo.existsByCampaignIdAndReviewerAccountIdAndReminderType(
                any(), any(), eq(ReminderType.ESCALATION))).thenReturn(false);

        scheduler.processEscalations(campaign);

        verify(notificationService).notifyEscalation(eq(campaign), any(Account.class), eq(3L));
        verify(reminderRepo).save(any(CampaignReminder.class));
    }

    @Test
    void processEscalations_doesNotEscalateBeforeThreshold() {
        AccessReviewCampaign campaign = buildCampaignWithReviewers(
                CampaignStatus.ACTIVE, OffsetDateTime.now().plusDays(20));
        // Campaign started only 5 days ago
        campaign.setCreatedAt(OffsetDateTime.now().minusDays(5));

        scheduler.processEscalations(campaign);

        verify(notificationService, never()).notifyEscalation(any(), any(), anyLong());
        verify(reminderRepo, never()).save(any());
    }

    @Test
    void processEscalations_doesNotDuplicateEscalation() {
        AccessReviewCampaign campaign = buildCampaignWithReviewers(
                CampaignStatus.ACTIVE, OffsetDateTime.now().plusDays(5));
        campaign.setCreatedAt(OffsetDateTime.now().minusDays(15));

        when(decisionRepo.countByReviewGroupIdAndDecisionIsNull(any())).thenReturn(3L);
        when(reminderRepo.existsByCampaignIdAndReviewerAccountIdAndReminderType(
                any(), any(), eq(ReminderType.ESCALATION))).thenReturn(true); // already sent

        scheduler.processEscalations(campaign);

        verify(notificationService, never()).notifyEscalation(any(), any(), anyLong());
    }

    @Test
    void processEscalations_skipsReviewersWithNoPending() {
        AccessReviewCampaign campaign = buildCampaignWithReviewers(
                CampaignStatus.ACTIVE, OffsetDateTime.now().plusDays(5));
        campaign.setCreatedAt(OffsetDateTime.now().minusDays(15));

        when(decisionRepo.countByReviewGroupIdAndDecisionIsNull(any())).thenReturn(0L);

        scheduler.processEscalations(campaign);

        verify(notificationService, never()).notifyEscalation(any(), any(), anyLong());
    }

    // ── Auto-revoke on expiry tests ───────────────────────────────────────

    @Test
    void executeAutoRevokeOnExpiry_revokesUndecidedMemberships() {
        AccessReviewCampaign campaign = buildCampaignWithReviewers(
                CampaignStatus.ACTIVE, OffsetDateTime.now().minusDays(1));

        AccessReviewDecision undecided = new AccessReviewDecision();
        undecided.setId(UUID.randomUUID());
        undecided.setMemberDn("uid=testuser,ou=people,dc=example,dc=com");
        undecided.setMemberDisplay("Test User");
        undecided.setDecision(null);
        undecided.setReviewGroup(campaign.getReviewGroups().get(0));

        AccessReviewDecision alreadyDecided = new AccessReviewDecision();
        alreadyDecided.setId(UUID.randomUUID());
        alreadyDecided.setMemberDn("uid=other,ou=people,dc=example,dc=com");
        alreadyDecided.setDecision(ReviewDecision.CONFIRM);
        alreadyDecided.setReviewGroup(campaign.getReviewGroups().get(0));

        when(decisionRepo.findByReviewGroupId(any())).thenReturn(List.of(undecided, alreadyDecided));
        when(accountRepo.findById(any())).thenReturn(Optional.of(superadmin));

        var systemPrincipal = new com.ldapadmin.auth.AuthPrincipal(
                com.ldapadmin.auth.PrincipalType.SUPERADMIN, superadmin.getId(), superadmin.getUsername());

        scheduler.executeAutoRevokeOnExpiry(campaign, systemPrincipal);

        // Only the undecided one should be revoked
        verify(ldapGroupService, times(1)).removeMember(
                any(), any(), any(), eq("uid=testuser,ou=people,dc=example,dc=com"));
        verify(decisionRepo, times(1)).save(any());

        assertThat(undecided.getDecision()).isEqualTo(ReviewDecision.REVOKE);
        assertThat(undecided.getComment()).isEqualTo("Auto-revoked on campaign expiry");
        assertThat(undecided.getRevokedAt()).isNotNull();
    }

    @Test
    void executeAutoRevokeOnExpiry_continuesOnPartialFailure() {
        AccessReviewCampaign campaign = buildCampaignWithReviewers(
                CampaignStatus.ACTIVE, OffsetDateTime.now().minusDays(1));

        AccessReviewDecision d1 = new AccessReviewDecision();
        d1.setId(UUID.randomUUID());
        d1.setMemberDn("uid=user1,ou=people,dc=example,dc=com");
        d1.setDecision(null);
        d1.setReviewGroup(campaign.getReviewGroups().get(0));

        AccessReviewDecision d2 = new AccessReviewDecision();
        d2.setId(UUID.randomUUID());
        d2.setMemberDn("uid=user2,ou=people,dc=example,dc=com");
        d2.setDecision(null);
        d2.setReviewGroup(campaign.getReviewGroups().get(0));

        when(decisionRepo.findByReviewGroupId(any())).thenReturn(List.of(d1, d2));
        when(accountRepo.findById(any())).thenReturn(Optional.of(superadmin));
        // First call throws, second succeeds
        doThrow(new RuntimeException("LDAP error"))
                .doNothing()
                .when(ldapGroupService).removeMember(any(), any(), any(), any());

        var systemPrincipal = new com.ldapadmin.auth.AuthPrincipal(
                com.ldapadmin.auth.PrincipalType.SUPERADMIN, superadmin.getId(), superadmin.getUsername());

        scheduler.executeAutoRevokeOnExpiry(campaign, systemPrincipal);

        // Both attempted
        verify(ldapGroupService, times(2)).removeMember(any(), any(), any(), any());
        // Only second saved (first threw before save)
        verify(decisionRepo, times(1)).save(any());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private AccessReviewCampaign buildCampaign(CampaignStatus status, OffsetDateTime deadline) {
        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(UUID.randomUUID());

        AccessReviewCampaign c = new AccessReviewCampaign();
        c.setId(UUID.randomUUID());
        c.setDirectory(dir);
        c.setName("Test Campaign");
        c.setStatus(status);
        c.setDeadline(deadline);
        c.setDeadlineDays(30);
        c.setCreatedBy(superadmin);
        return c;
    }

    private AccessReviewCampaign buildCampaignWithReviewers(CampaignStatus status, OffsetDateTime deadline) {
        AccessReviewCampaign campaign = buildCampaign(status, deadline);

        Account reviewer = new Account();
        reviewer.setId(UUID.randomUUID());
        reviewer.setUsername("reviewer");
        reviewer.setEmail("reviewer@example.com");

        AccessReviewGroup group = new AccessReviewGroup();
        group.setId(UUID.randomUUID());
        group.setCampaign(campaign);
        group.setGroupDn("cn=testgroup,dc=example,dc=com");
        group.setGroupName("testgroup");
        group.setMemberAttribute("member");
        group.setReviewer(reviewer);
        group.setDecisions(new ArrayList<>());

        campaign.setReviewGroups(new ArrayList<>(List.of(group)));
        return campaign;
    }
}
