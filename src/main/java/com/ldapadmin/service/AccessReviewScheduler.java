package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.enums.AccountRole;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.repository.AccessReviewCampaignRepository;
import com.ldapadmin.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class AccessReviewScheduler {

    private final AccessReviewCampaignRepository campaignRepo;
    private final AccessReviewCampaignService campaignService;
    private final AccessReviewNotificationService notificationService;
    private final AccountRepository accountRepo;

    @Value("${ldapadmin.access-review.reminder-days:3}")
    private int reminderDays;

    @Scheduled(cron = "${ldapadmin.access-review.expiry-cron:0 0 2 * * ?}")
    public void processDeadlines() {
        log.info("Running access review deadline processor");

        // 1. Expire overdue campaigns
        List<AccessReviewCampaign> overdue = campaignRepo.findByStatusAndDeadlineBefore(
                CampaignStatus.ACTIVE, OffsetDateTime.now());

        AuthPrincipal systemPrincipal = resolveSystemPrincipal();

        for (AccessReviewCampaign campaign : overdue) {
            try {
                log.info("Expiring campaign '{}' (id={})", campaign.getName(), campaign.getId());
                campaignService.expireCampaign(campaign, systemPrincipal);
            } catch (Exception e) {
                log.error("Failed to expire campaign {}: {}", campaign.getId(), e.getMessage(), e);
            }
        }

        // 2. Send deadline reminders for campaigns approaching deadline
        OffsetDateTime reminderThreshold = OffsetDateTime.now().plusDays(reminderDays);
        List<AccessReviewCampaign> approaching = campaignRepo.findByStatusAndDeadlineBefore(
                CampaignStatus.ACTIVE, reminderThreshold);

        // Filter out already expired ones (they were just processed above)
        approaching.removeAll(overdue);

        for (AccessReviewCampaign campaign : approaching) {
            try {
                log.info("Sending deadline reminder for campaign '{}' (id={})", campaign.getName(), campaign.getId());
                notificationService.notifyDeadlineApproaching(campaign);
            } catch (Exception e) {
                log.error("Failed to send reminder for campaign {}: {}", campaign.getId(), e.getMessage(), e);
            }
        }
    }

    private AuthPrincipal resolveSystemPrincipal() {
        // Use the first active superadmin as the system principal for audit records
        List<Account> superadmins = accountRepo.findAllByRoleAndActiveTrue(AccountRole.SUPERADMIN);
        if (!superadmins.isEmpty()) {
            Account sa = superadmins.get(0);
            return new AuthPrincipal(PrincipalType.SUPERADMIN, sa.getId(), sa.getUsername());
        }
        // Fallback — should not happen in a properly configured system
        log.warn("No active superadmin found for system audit principal");
        return new AuthPrincipal(PrincipalType.SUPERADMIN, java.util.UUID.randomUUID(), "system");
    }
}
