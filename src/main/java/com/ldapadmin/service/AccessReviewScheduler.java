package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.AccessReviewDecision;
import com.ldapadmin.entity.AccessReviewGroup;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.CampaignReminder;
import com.ldapadmin.entity.enums.AccountRole;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.entity.enums.ReminderType;
import com.ldapadmin.entity.enums.ReviewDecision;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.repository.AccessReviewCampaignRepository;
import com.ldapadmin.repository.AccessReviewDecisionRepository;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.CampaignReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class AccessReviewScheduler {

    private final AccessReviewCampaignRepository campaignRepo;
    private final AccessReviewCampaignService campaignService;
    private final AccessReviewNotificationService notificationService;
    private final AccountRepository accountRepo;
    private final CampaignReminderRepository reminderRepo;
    private final AccessReviewDecisionRepository decisionRepo;
    private final LdapGroupService ldapGroupService;
    private final AuditService auditService;

    @Value("${ldapadmin.access-review.reminder-days:3}")
    private int reminderDays;

    @Value("${ldapadmin.access-review.escalation-days:14}")
    private int escalationDays;

    @Value("${ldapadmin.access-review.auto-revoke-enabled:false}")
    private boolean autoRevokeEnabled;

    @Scheduled(cron = "${ldapadmin.access-review.expiry-cron:0 0 2 * * ?}")
    public void processDeadlines() {
        log.info("Running access review deadline processor");

        AuthPrincipal systemPrincipal = resolveSystemPrincipal();

        // 1. Expire overdue campaigns
        List<AccessReviewCampaign> overdue = campaignRepo.findByStatusAndDeadlineBefore(
                CampaignStatus.ACTIVE, OffsetDateTime.now());

        for (AccessReviewCampaign campaign : overdue) {
            try {
                log.info("Expiring campaign '{}' (id={})", campaign.getName(), campaign.getId());

                // Auto-revoke undecided memberships on expiry if configured
                if (campaign.isAutoRevokeOnExpiry() && autoRevokeEnabled) {
                    executeAutoRevokeOnExpiry(campaign, systemPrincipal);
                }

                campaignService.expireCampaign(campaign, systemPrincipal);

                // Create recurring follow-up if configured
                if (campaign.getRecurrenceMonths() != null) {
                    try {
                        campaignService.createRecurringFollowUp(campaign, systemPrincipal);
                    } catch (Exception e) {
                        log.error("Failed to create recurring follow-up for campaign {}: {}",
                                campaign.getId(), e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to expire campaign {}: {}", campaign.getId(), e.getMessage(), e);
            }
        }

        // 2. Send deadline reminders for campaigns approaching deadline
        OffsetDateTime reminderThreshold = OffsetDateTime.now().plusDays(reminderDays);
        List<AccessReviewCampaign> approaching = new java.util.ArrayList<>(
                campaignRepo.findByStatusAndDeadlineBefore(CampaignStatus.ACTIVE, reminderThreshold));

        // Filter out already expired ones (they were just processed above)
        approaching.removeAll(overdue);

        for (AccessReviewCampaign campaign : approaching) {
            try {
                log.info("Sending deadline reminder for campaign '{}' (id={})", campaign.getName(), campaign.getId());
                sendDeadlineRemindersWithTracking(campaign);
            } catch (Exception e) {
                log.error("Failed to send reminder for campaign {}: {}", campaign.getId(), e.getMessage(), e);
            }
        }

        // 3. Process escalations for all active campaigns
        List<AccessReviewCampaign> allActive = campaignRepo.findByStatusAndDeadlineBefore(
                CampaignStatus.ACTIVE, OffsetDateTime.now().plusYears(10));
        for (AccessReviewCampaign campaign : allActive) {
            try {
                processEscalations(campaign);
            } catch (Exception e) {
                log.error("Failed to process escalation for campaign {}: {}", campaign.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Sends deadline reminders only to reviewers who have pending decisions
     * and haven't already received a DEADLINE reminder for this campaign.
     */
    private void sendDeadlineRemindersWithTracking(AccessReviewCampaign campaign) {
        Set<Account> notified = new HashSet<>();
        for (AccessReviewGroup group : campaign.getReviewGroups()) {
            Account reviewer = group.getReviewer();
            if (notified.contains(reviewer)) continue;

            long pending = decisionRepo.countByReviewGroupIdAndDecisionIsNull(group.getId());
            if (pending == 0) continue;

            boolean alreadySent = reminderRepo.existsByCampaignIdAndReviewerAccountIdAndReminderType(
                    campaign.getId(), reviewer.getId(), ReminderType.DEADLINE);
            if (alreadySent) continue;

            notified.add(reviewer);
            reminderRepo.save(new CampaignReminder(campaign, reviewer, ReminderType.DEADLINE));
        }
        // Delegate actual email sending to the existing method
        if (!notified.isEmpty()) {
            notificationService.notifyDeadlineApproaching(campaign);
        }
    }

    /**
     * Escalation: if campaign has been active for >= escalationDays and a reviewer
     * has pending decisions with no ESCALATION reminder sent yet, escalate to
     * the campaign creator.
     */
    void processEscalations(AccessReviewCampaign campaign) {
        if (campaign.getStartsAt() == null && campaign.getCreatedAt() == null) return;

        OffsetDateTime activeSince = campaign.getStartsAt() != null ? campaign.getStartsAt() : campaign.getCreatedAt();
        OffsetDateTime escalationThreshold = activeSince.plusDays(escalationDays);

        if (OffsetDateTime.now().isBefore(escalationThreshold)) return;

        for (AccessReviewGroup group : campaign.getReviewGroups()) {
            Account reviewer = group.getReviewer();

            long pending = decisionRepo.countByReviewGroupIdAndDecisionIsNull(group.getId());
            if (pending == 0) continue;

            boolean alreadyEscalated = reminderRepo.existsByCampaignIdAndReviewerAccountIdAndReminderType(
                    campaign.getId(), reviewer.getId(), ReminderType.ESCALATION);
            if (alreadyEscalated) continue;

            log.info("Escalating campaign '{}' (id={}) — reviewer '{}' has {} pending decisions",
                    campaign.getName(), campaign.getId(), reviewer.getUsername(), pending);

            reminderRepo.save(new CampaignReminder(campaign, reviewer, ReminderType.ESCALATION));
            notificationService.notifyEscalation(campaign, reviewer, pending);
        }
    }

    /**
     * Auto-revoke all undecided memberships for a campaign on expiry.
     * Creates REVOKE decisions with SYSTEM actor and removes LDAP members.
     * Guarded by the global auto-revoke-enabled kill switch.
     */
    void executeAutoRevokeOnExpiry(AccessReviewCampaign campaign, AuthPrincipal systemPrincipal) {
        log.info("Executing auto-revoke on expiry for campaign '{}' (id={})", campaign.getName(), campaign.getId());

        Account systemAccount = accountRepo.findById(systemPrincipal.id()).orElse(null);

        for (AccessReviewGroup group : campaign.getReviewGroups()) {
            List<AccessReviewDecision> undecided = decisionRepo.findByReviewGroupId(group.getId()).stream()
                    .filter(d -> d.getDecision() == null)
                    .toList();

            for (AccessReviewDecision decision : undecided) {
                try {
                    decision.setDecision(ReviewDecision.REVOKE);
                    decision.setDecidedBy(systemAccount);
                    decision.setDecidedAt(OffsetDateTime.now());
                    decision.setComment("Auto-revoked on campaign expiry");

                    ldapGroupService.removeMember(
                            campaign.getDirectory(),
                            group.getGroupDn(),
                            group.getMemberAttribute(),
                            decision.getMemberDn());

                    decision.setRevokedAt(OffsetDateTime.now());
                    decisionRepo.save(decision);

                    auditService.record(systemPrincipal, campaign.getDirectory().getId(),
                            AuditAction.REVIEW_AUTO_REVOKED,
                            decision.getMemberDn(),
                            Map.of("groupDn", group.getGroupDn(),
                                    "campaignId", campaign.getId().toString(),
                                    "reason", "auto-revoke-on-expiry"));

                    log.info("Auto-revoked member {} from group {} (campaign {})",
                            decision.getMemberDn(), group.getGroupDn(), campaign.getId());
                } catch (Exception e) {
                    log.error("Failed to auto-revoke member {} from group {}: {}",
                            decision.getMemberDn(), group.getGroupDn(), e.getMessage());
                }
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
        return new AuthPrincipal(PrincipalType.SUPERADMIN, UUID.randomUUID(), "system");
    }
}
