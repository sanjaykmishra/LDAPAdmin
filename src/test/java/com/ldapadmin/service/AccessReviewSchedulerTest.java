package com.ldapadmin.service;

import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AccountRole;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.repository.AccessReviewCampaignRepository;
import com.ldapadmin.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessReviewSchedulerTest {

    @Mock private AccessReviewCampaignRepository campaignRepo;
    @Mock private AccessReviewCampaignService campaignService;
    @Mock private AccessReviewNotificationService notificationService;
    @Mock private AccountRepository accountRepo;

    private AccessReviewScheduler scheduler;

    private Account superadmin;

    @BeforeEach
    void setUp() {
        scheduler = new AccessReviewScheduler(campaignRepo, campaignService, notificationService, accountRepo);

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
                .thenReturn(List.of()); // second call for reminders
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
                .thenReturn(List.of());
        when(accountRepo.findAllByRoleAndActiveTrue(AccountRole.SUPERADMIN)).thenReturn(List.of(superadmin));

        scheduler.processDeadlines();

        verify(campaignService).expireCampaign(eq(overdue), any());
        verify(campaignService).createRecurringFollowUp(eq(overdue), any());
    }

    @Test
    void processDeadlines_sendsRemindersForApproachingDeadlines() {
        // No overdue campaigns
        when(campaignRepo.findByStatusAndDeadlineBefore(eq(CampaignStatus.ACTIVE), any()))
                .thenReturn(List.of()) // overdue check
                .thenReturn(List.of(buildCampaign(CampaignStatus.ACTIVE, OffsetDateTime.now().plusDays(2)))); // reminder check
        when(accountRepo.findAllByRoleAndActiveTrue(AccountRole.SUPERADMIN)).thenReturn(List.of(superadmin));

        scheduler.processDeadlines();

        verify(notificationService).notifyDeadlineApproaching(any());
    }

    @Test
    void processDeadlines_noOverdueCampaigns_doesNothing() {
        when(campaignRepo.findByStatusAndDeadlineBefore(eq(CampaignStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(accountRepo.findAllByRoleAndActiveTrue(AccountRole.SUPERADMIN)).thenReturn(List.of(superadmin));

        scheduler.processDeadlines();

        verify(campaignService, never()).expireCampaign(any(), any());
    }

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
}
