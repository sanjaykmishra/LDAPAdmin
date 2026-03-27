package com.ldapadmin.service;

import com.ldapadmin.dto.notification.NotificationDto;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.Notification;
import com.ldapadmin.entity.enums.AccountRole;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.AdminFeaturePermissionRepository;
import com.ldapadmin.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * In-app notification service. All {@code send} methods are async and
 * fire-and-forget — failures are logged but never propagate to callers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final AccountRepository accountRepo;
    private final AdminFeaturePermissionRepository featurePermRepo;

    // ── Send ──────────────────────────────────────────────────────────────────

    /** Send a notification to a specific account. */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(UUID accountId, String type, String title, String body,
                     String link, UUID directoryId) {
        try {
            notificationRepo.save(Notification.builder()
                    .accountId(accountId)
                    .type(type)
                    .title(title)
                    .body(body)
                    .link(link)
                    .directoryId(directoryId)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to send notification [type={}, account={}]: {}", type, accountId, e.getMessage());
        }
    }

    /**
     * Send a notification to all accounts that hold a specific feature
     * permission (explicitly enabled or via ADMIN base role). Also includes
     * all superadmins.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendToFeatureHolders(UUID directoryId, FeatureKey feature,
                                      String type, String title, String body, String link) {
        try {
            // Superadmins always get everything
            List<Account> superadmins = accountRepo.findAllByRoleAndActiveTrue(AccountRole.SUPERADMIN);
            for (Account sa : superadmins) {
                notificationRepo.save(Notification.builder()
                        .accountId(sa.getId()).type(type).title(title)
                        .body(body).link(link).directoryId(directoryId).build());
            }

            // Admins with explicit feature override enabled
            List<Account> admins = accountRepo.findAllByRoleAndActiveTrue(AccountRole.ADMIN);
            for (Account admin : admins) {
                boolean hasFeature = featurePermRepo
                        .existsByAdminAccountIdAndFeatureKeyAndEnabledTrue(admin.getId(), feature);
                if (hasFeature) {
                    notificationRepo.save(Notification.builder()
                            .accountId(admin.getId()).type(type).title(title)
                            .body(body).link(link).directoryId(directoryId).build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send notification to feature holders [type={}, feature={}]: {}",
                    type, feature, e.getMessage());
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NotificationDto> getUnread(UUID accountId) {
        return notificationRepo.findByAccountIdAndReadFalseOrderByCreatedAtDesc(accountId)
                .stream().map(NotificationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> getAll(UUID accountId, int page, int size) {
        return notificationRepo.findByAccountIdOrderByCreatedAtDesc(
                accountId, PageRequest.of(page, size)).map(NotificationDto::from);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID accountId) {
        return notificationRepo.countByAccountIdAndReadFalse(accountId);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public void markRead(UUID notificationId, UUID accountId) {
        notificationRepo.findById(notificationId).ifPresent(n -> {
            if (n.getAccountId().equals(accountId)) {
                n.setRead(true);
                notificationRepo.save(n);
            }
        });
    }

    @Transactional
    public void markAllRead(UUID accountId) {
        notificationRepo.markAllReadByAccountId(accountId);
    }
}
