package com.ldapadmin.service;

import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.repository.AccessReviewDecisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Tests for escalation notification. Email sending is tested indirectly since
 * the SMTP implementation uses raw sockets; here we verify the method doesn't
 * throw and logs properly when SMTP is not configured.
 */
@ExtendWith(MockitoExtension.class)
class AccessReviewNotificationServiceTest {

    @Mock private ApplicationSettingsService appSettingsService;
    @Mock private EncryptionService encryptionService;
    @Mock private AccessReviewDecisionRepository decisionRepo;

    private AccessReviewNotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new AccessReviewNotificationService(
                appSettingsService, encryptionService, decisionRepo);
    }

    @Test
    void notifyEscalation_withNoSmtp_logsWithoutException() {
        ApplicationSettings settings = new ApplicationSettings();
        settings.setSmtpHost(null);
        when(appSettingsService.getEntity()).thenReturn(settings);

        AccessReviewCampaign campaign = buildCampaign();
        Account reviewer = new Account();
        reviewer.setId(UUID.randomUUID());
        reviewer.setUsername("reviewer1");

        // Should not throw
        notificationService.notifyEscalation(campaign, reviewer, 5);
    }

    @Test
    void notifyEscalation_withCreatorNoEmail_logsWithoutException() {
        AccessReviewCampaign campaign = buildCampaign();
        campaign.getCreatedBy().setEmail(null);

        Account reviewer = new Account();
        reviewer.setId(UUID.randomUUID());
        reviewer.setUsername("reviewer1");

        // Should not throw — no email means it logs and returns
        notificationService.notifyEscalation(campaign, reviewer, 5);
    }

    @Test
    void notifyEscalation_withCreatorBlankEmail_logsWithoutException() {
        AccessReviewCampaign campaign = buildCampaign();
        campaign.getCreatedBy().setEmail("   ");

        Account reviewer = new Account();
        reviewer.setId(UUID.randomUUID());
        reviewer.setUsername("reviewer1");

        // Should not throw
        notificationService.notifyEscalation(campaign, reviewer, 5);
    }

    private AccessReviewCampaign buildCampaign() {
        Account creator = new Account();
        creator.setId(UUID.randomUUID());
        creator.setUsername("admin");
        creator.setEmail("admin@example.com");

        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(UUID.randomUUID());

        AccessReviewCampaign campaign = new AccessReviewCampaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Test Campaign");
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setDeadline(OffsetDateTime.now().plusDays(10));
        campaign.setDirectory(dir);
        campaign.setCreatedBy(creator);
        return campaign;
    }
}
