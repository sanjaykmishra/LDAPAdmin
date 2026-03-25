package com.ldapadmin.service;

import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.AccessReviewGroup;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.ApplicationSettings;
import com.ldapadmin.entity.enums.ReviewDecision;
import com.ldapadmin.repository.AccessReviewDecisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccessReviewNotificationService {

    private final ApplicationSettingsService appSettingsService;
    private final EncryptionService encryptionService;
    private final AccessReviewDecisionRepository decisionRepo;

    @Async
    public void notifyReviewersAssigned(AccessReviewCampaign campaign) {
        Set<Account> notified = new HashSet<>();
        for (AccessReviewGroup group : campaign.getReviewGroups()) {
            Account reviewer = group.getReviewer();
            if (notified.contains(reviewer)) continue;
            notified.add(reviewer);

            if (reviewer.getEmail() != null && !reviewer.getEmail().isBlank()) {
                sendEmail(reviewer.getEmail(),
                        "[LDAPAdmin] Access Review — You have been assigned as a reviewer",
                        String.format(
                                "You have been assigned as a reviewer for the access review campaign '%s'.\n\n"
                                + "Deadline: %s\n\n"
                                + "Please log in to LDAPAdmin to review the group memberships assigned to you.",
                                campaign.getName(), campaign.getDeadline()));
            }
        }
    }

    /**
     * Sends deadline-approaching notifications only to the specified reviewers
     * (those with pending work who haven't been recently notified).
     */
    @Async
    public void notifyDeadlineApproaching(AccessReviewCampaign campaign, Set<Account> reviewersToNotify) {
        long total = decisionRepo.countTotalByCampaignId(campaign.getId());
        long pending = decisionRepo.countPendingByCampaignId(campaign.getId());
        long decided = total - pending;

        for (Account reviewer : reviewersToNotify) {
            if (reviewer.getEmail() != null && !reviewer.getEmail().isBlank()) {
                sendEmail(reviewer.getEmail(),
                        "[LDAPAdmin] Access Review Deadline Approaching — " + campaign.getName(),
                        String.format(
                                "The access review campaign '%s' deadline is approaching: %s\n\n"
                                + "Progress: %d of %d items decided (%d remaining).\n\n"
                                + "Please log in to LDAPAdmin to complete your reviews.",
                                campaign.getName(), campaign.getDeadline(), decided, total, pending));
            }
        }

        // Also notify creator
        Account creator = campaign.getCreatedBy();
        if (creator.getEmail() != null && !creator.getEmail().isBlank() && !reviewersToNotify.contains(creator)) {
            sendEmail(creator.getEmail(),
                    "[LDAPAdmin] Access Review Deadline Approaching — " + campaign.getName(),
                    String.format(
                            "The access review campaign '%s' deadline is approaching: %s\n\n"
                            + "Progress: %d of %d items decided (%d remaining).",
                            campaign.getName(), campaign.getDeadline(), decided, total, pending));
        }
    }

    @Async
    public void notifyCampaignClosed(AccessReviewCampaign campaign) {
        long confirmed = decisionRepo.countByCampaignIdAndDecision(campaign.getId(), ReviewDecision.CONFIRM);
        long revoked = decisionRepo.countByCampaignIdAndDecision(campaign.getId(), ReviewDecision.REVOKE);

        Account creator = campaign.getCreatedBy();
        if (creator.getEmail() != null && !creator.getEmail().isBlank()) {
            sendEmail(creator.getEmail(),
                    "[LDAPAdmin] Access Review Campaign Closed — " + campaign.getName(),
                    String.format(
                            "The access review campaign '%s' has been closed.\n\n"
                            + "Results: %d confirmed, %d revoked.\n\n"
                            + "You can export the full report from LDAPAdmin.",
                            campaign.getName(), confirmed, revoked));
        }
    }

    @Async
    public void notifyEscalation(AccessReviewCampaign campaign, Account reviewer, long pendingCount) {
        Account creator = campaign.getCreatedBy();
        if (creator.getEmail() == null || creator.getEmail().isBlank()) {
            log.info("Campaign creator has no email — escalation logged: campaign={}, reviewer={}",
                    campaign.getName(), reviewer.getUsername());
            return;
        }

        sendEmail(creator.getEmail(),
                "[LDAPAdmin] ESCALATION — Reviewer has not responded for " + campaign.getName(),
                String.format(
                        "ESCALATION NOTICE\n\n"
                        + "Reviewer '%s' has not completed their access review for campaign '%s'.\n\n"
                        + "Pending decisions: %d\n"
                        + "Campaign deadline: %s\n\n"
                        + "Please follow up with the reviewer or take action in LDAPAdmin.",
                        reviewer.getUsername(), campaign.getName(), pendingCount, campaign.getDeadline()));
    }

    @Async
    public void notifyCampaignExpired(AccessReviewCampaign campaign) {
        Account creator = campaign.getCreatedBy();
        if (creator.getEmail() != null && !creator.getEmail().isBlank()) {
            sendEmail(creator.getEmail(),
                    "[LDAPAdmin] Access Review Campaign Expired — " + campaign.getName(),
                    String.format(
                            "The access review campaign '%s' has expired.\n"
                            + "The deadline %s has passed without the campaign being closed.\n\n"
                            + "Please log in to LDAPAdmin to review the campaign status.",
                            campaign.getName(), campaign.getDeadline()));
        }

        // Notify reviewers too
        Set<Account> notified = new HashSet<>();
        notified.add(creator);
        for (AccessReviewGroup group : campaign.getReviewGroups()) {
            Account reviewer = group.getReviewer();
            if (notified.contains(reviewer)) continue;
            notified.add(reviewer);

            if (reviewer.getEmail() != null && !reviewer.getEmail().isBlank()) {
                sendEmail(reviewer.getEmail(),
                        "[LDAPAdmin] Access Review Campaign Expired — " + campaign.getName(),
                        String.format(
                                "The access review campaign '%s' has expired (deadline: %s).\n\n"
                                + "No further decisions can be submitted.",
                                campaign.getName(), campaign.getDeadline()));
            }
        }
    }

    @Async
    public void notifyRecurringFollowUpCreated(AccessReviewCampaign followUp, AccessReviewCampaign source) {
        Account creator = source.getCreatedBy();
        if (creator.getEmail() == null || creator.getEmail().isBlank()) {
            log.info("Campaign creator has no email — follow-up notification logged: campaign={}",
                    followUp.getName());
            return;
        }

        sendEmail(creator.getEmail(),
                "[LDAPAdmin] Recurring Access Review Scheduled — " + followUp.getName(),
                String.format(
                        "A recurring follow-up access review campaign '%s' has been automatically created.\n\n"
                        + "Scheduled to auto-activate: %s\n"
                        + "Deadline: %s\n\n"
                        + "The campaign will activate automatically on the scheduled date "
                        + "and reviewers will be notified at that time.",
                        followUp.getName(), followUp.getStartsAt(), followUp.getDeadline()));
    }

    private void sendEmail(String to, String subject, String body) {
        ApplicationSettings settings = appSettingsService.getEntity();
        if (settings.getSmtpHost() == null || settings.getSmtpHost().isBlank()
                || settings.getSmtpSenderAddress() == null || settings.getSmtpSenderAddress().isBlank()) {
            log.info("SMTP not configured — notification logged: to={}, subject={}", to, subject);
            return;
        }

        try {
            sendSmtpEmail(settings, to, subject, body);
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }

    private void sendSmtpEmail(ApplicationSettings settings, String to, String subject, String body) throws Exception {
        String host = settings.getSmtpHost();
        int port = settings.getSmtpPort() != null ? settings.getSmtpPort() : 587;
        String from = settings.getSmtpSenderAddress();

        java.net.Socket socket = new java.net.Socket(host, port);
        socket.setSoTimeout(10000);

        try {
            var in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
            var out = new java.io.PrintWriter(socket.getOutputStream(), true);

            readLine(in);
            out.println("EHLO ldapadmin");
            readMultiLine(in);

            // Upgrade to TLS via STARTTLS if port suggests it (587, 25)
            if (port != 465) {
                out.println("STARTTLS");
                String tlsResponse = readLine(in);
                if (tlsResponse != null && tlsResponse.startsWith("220")) {
                    var sslFactory = (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
                    socket = sslFactory.createSocket(socket, host, port, true);
                    in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                    out = new java.io.PrintWriter(socket.getOutputStream(), true);

                    // Re-issue EHLO after TLS upgrade
                    out.println("EHLO ldapadmin");
                    readMultiLine(in);
                }
                // If STARTTLS not supported, continue unencrypted (best-effort)
            }

            if (settings.getSmtpUsername() != null && settings.getSmtpPasswordEncrypted() != null) {
                String password = encryptionService.decrypt(settings.getSmtpPasswordEncrypted());
                String auth = java.util.Base64.getEncoder().encodeToString(
                        ("\0" + settings.getSmtpUsername() + "\0" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.println("AUTH PLAIN " + auth);
                readLine(in);
            }

            out.println("MAIL FROM:<" + from + ">");
            readLine(in);
            out.println("RCPT TO:<" + to + ">");
            readLine(in);
            out.println("DATA");
            readLine(in);
            out.println("From: " + from);
            out.println("To: " + to);
            out.println("Subject: " + subject);
            out.println("Content-Type: text/plain; charset=UTF-8");
            out.println();
            out.println(body);
            out.println(".");
            readLine(in);
            out.println("QUIT");
        } finally {
            socket.close();
        }
    }

    private String readLine(java.io.BufferedReader in) throws Exception {
        String line = in.readLine();
        if (line != null && line.length() >= 3) {
            char c = line.charAt(3);
            if (c == '-') readMultiLine(in);
        }
        return line;
    }

    private void readMultiLine(java.io.BufferedReader in) throws Exception {
        String line;
        do {
            line = in.readLine();
        } while (line != null && line.length() >= 4 && line.charAt(3) == '-');
    }
}
