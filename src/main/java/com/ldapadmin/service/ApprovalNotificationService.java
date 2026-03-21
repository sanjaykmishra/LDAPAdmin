package com.ldapadmin.service;

import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.ApplicationSettings;
import com.ldapadmin.entity.PendingApproval;
import com.ldapadmin.entity.Realm;
import com.ldapadmin.entity.enums.ApprovalStatus;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.PendingApprovalRepository;
import com.ldapadmin.repository.RealmRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Sends email notifications for approval workflow events via SMTP.
 * Uses the SMTP config from {@link ApplicationSettings} with raw socket/HTTP.
 * If SMTP is not configured, notifications are logged instead.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApprovalNotificationService {

    private final ApplicationSettingsService appSettingsService;
    private final EncryptionService encryptionService;
    private final RealmApproverService realmApproverService;
    private final RealmRepository realmRepo;
    private final AccountRepository accountRepo;
    private final PendingApprovalRepository approvalRepo;

    @Async
    public void notifyApproversOfNewRequest(PendingApproval approval) {
        String realmName = realmRepo.findById(approval.getRealmId())
                .map(Realm::getName).orElse("Unknown");
        String requesterName = accountRepo.findById(approval.getRequestedBy())
                .map(Account::getUsername).orElse("Unknown");

        List<Account> approvers = realmApproverService.getApprovers(approval.getRealmId());

        String subject = "[LDAPAdmin] New approval request pending — " + realmName;
        String body = String.format(
                "A new %s request has been submitted by %s in realm '%s' and is awaiting your approval.\n\n"
                + "Request type: %s\nSubmitted: %s\n\n"
                + "Please log in to LDAPAdmin to review and approve or reject this request.",
                approval.getRequestType().name(), requesterName, realmName,
                approval.getRequestType().name(), approval.getCreatedAt());

        for (Account approver : approvers) {
            if (approver.getEmail() != null && !approver.getEmail().isBlank()) {
                sendEmail(approver.getEmail(), subject, body);
            }
        }
    }

    @Async
    public void notifyRequesterApproved(PendingApproval approval) {
        String realmName = realmRepo.findById(approval.getRealmId())
                .map(Realm::getName).orElse("Unknown");
        String reviewerName = approval.getReviewedBy() != null
                ? accountRepo.findById(approval.getReviewedBy())
                        .map(Account::getUsername).orElse("Unknown")
                : "Unknown";

        Account requester = accountRepo.findById(approval.getRequestedBy()).orElse(null);
        if (requester == null || requester.getEmail() == null) return;

        sendEmail(requester.getEmail(),
                "[LDAPAdmin] Your request was approved — " + realmName,
                String.format("Your %s request in realm '%s' has been approved.\n\nReviewed by: %s",
                        approval.getRequestType().name(), realmName, reviewerName));
    }

    @Async
    public void notifyRequesterRejected(PendingApproval approval) {
        String realmName = realmRepo.findById(approval.getRealmId())
                .map(Realm::getName).orElse("Unknown");
        String reviewerName = approval.getReviewedBy() != null
                ? accountRepo.findById(approval.getReviewedBy())
                        .map(Account::getUsername).orElse("Unknown")
                : "Unknown";
        String reason = approval.getRejectReason() != null ? approval.getRejectReason() : "No reason provided";

        Account requester = accountRepo.findById(approval.getRequestedBy()).orElse(null);
        if (requester == null || requester.getEmail() == null) return;

        sendEmail(requester.getEmail(),
                "[LDAPAdmin] Your request was rejected — " + realmName,
                String.format("Your %s request in realm '%s' has been rejected.\n\nReason: %s\nReviewed by: %s",
                        approval.getRequestType().name(), realmName, reason, reviewerName));
    }

    @Scheduled(cron = "${ldapadmin.approval.reminder-cron:0 0 9 * * *}")
    public void sendPendingReminders() {
        List<Realm> allRealms = realmRepo.findAll();
        for (Realm realm : allRealms) {
            long pendingCount = approvalRepo.countByRealmIdAndStatus(
                    realm.getId(), ApprovalStatus.PENDING);
            if (pendingCount == 0) continue;

            List<Account> approvers = realmApproverService.getApprovers(realm.getId());
            for (Account approver : approvers) {
                if (approver.getEmail() != null && !approver.getEmail().isBlank()) {
                    sendEmail(approver.getEmail(),
                            String.format("[LDAPAdmin] Reminder: %d pending approval(s) — %s",
                                    pendingCount, realm.getName()),
                            String.format("There are %d pending approval request(s) in realm '%s' awaiting your review.\n\n"
                                    + "Please log in to LDAPAdmin to review them.",
                                    pendingCount, realm.getName()));
                }
            }
        }
    }

    /**
     * Sends an email using the SMTP config from application settings.
     * Falls back to logging if SMTP is not configured.
     */
    private void sendEmail(String to, String subject, String body) {
        ApplicationSettings settings = appSettingsService.getEntity();
        if (settings.getSmtpHost() == null || settings.getSmtpHost().isBlank()
                || settings.getSmtpSenderAddress() == null || settings.getSmtpSenderAddress().isBlank()) {
            log.info("SMTP not configured — notification logged: to={}, subject={}", to, subject);
            return;
        }

        // Use SMTP submission via simple socket connection
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

        var socket = new java.net.Socket(host, port);
        socket.setSoTimeout(10000);
        var in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
        var out = new java.io.PrintWriter(socket.getOutputStream(), true);

        try {
            readLine(in); // greeting
            out.println("EHLO ldapadmin");
            readMultiLine(in);

            // AUTH if credentials configured
            if (settings.getSmtpUsername() != null && settings.getSmtpPasswordEncrypted() != null) {
                String password = encryptionService.decrypt(settings.getSmtpPasswordEncrypted());
                String auth = Base64.getEncoder().encodeToString(
                        ("\0" + settings.getSmtpUsername() + "\0" + password).getBytes(StandardCharsets.UTF_8));
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
            if (c == '-') readMultiLine(in); // multi-line response continues
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
