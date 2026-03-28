package com.ldapadmin.service.alerting;

import com.ldapadmin.entity.AlertInstance;
import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AlertStatus;
import com.ldapadmin.repository.AlertInstanceRepository;
import com.ldapadmin.repository.AlertRuleRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.service.NotificationService;
import com.ldapadmin.entity.enums.FeatureKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrator that periodically evaluates all enabled alert rules,
 * creates alert instances for new findings, and sends notifications.
 */
@Service
@Slf4j
public class AlertMonitoringService {

    private final AlertRuleRepository ruleRepo;
    private final AlertInstanceRepository instanceRepo;
    private final DirectoryConnectionRepository dirRepo;
    private final NotificationService notificationService;
    private final Map<String, AlertChecker> checkerRegistry = new HashMap<>();

    public AlertMonitoringService(AlertRuleRepository ruleRepo,
                                   AlertInstanceRepository instanceRepo,
                                   DirectoryConnectionRepository dirRepo,
                                   NotificationService notificationService,
                                   List<AlertChecker> checkers) {
        this.ruleRepo = ruleRepo;
        this.instanceRepo = instanceRepo;
        this.dirRepo = dirRepo;
        this.notificationService = notificationService;

        for (AlertChecker checker : checkers) {
            checkerRegistry.put(checker.ruleType().name(), checker);
        }
        log.info("Alert monitoring initialized with {} checker(s): {}",
                checkerRegistry.size(), checkerRegistry.keySet());
    }

    @Scheduled(cron = "${ldapadmin.monitoring.cron:0 */15 * * * ?}")
    public void evaluate() {
        List<AlertRule> rules = ruleRepo.findAllByEnabledTrue();
        if (rules.isEmpty()) return;

        int fired = 0;
        for (AlertRule rule : rules) {
            try {
                fired += evaluateRule(rule);
            } catch (Exception e) {
                log.warn("Alert checker failed for rule {} ({}): {}",
                        rule.getId(), rule.getRuleType(), e.getMessage());
            }
        }
        if (fired > 0) {
            log.info("Alert evaluation complete: {} new alert(s) fired from {} rule(s)", fired, rules.size());
        }
    }

    @Transactional
    public int evaluateRule(AlertRule rule) {
        AlertChecker checker = checkerRegistry.get(rule.getRuleType().name());
        if (checker == null) {
            log.debug("No checker registered for rule type {}", rule.getRuleType());
            return 0;
        }

        DirectoryConnection dc = null;
        if (rule.getDirectory() != null) {
            dc = dirRepo.findById(rule.getDirectory().getId()).orElse(null);
            if (dc == null || !dc.isEnabled()) return 0;
        }

        List<AlertChecker.AlertCandidate> candidates = checker.evaluate(dc, rule);
        int fired = 0;

        for (AlertChecker.AlertCandidate candidate : candidates) {
            if (isDuplicate(rule, candidate)) continue;

            AlertInstance instance = AlertInstance.builder()
                    .rule(rule)
                    .directoryId(rule.getDirectory() != null ? rule.getDirectory().getId() : null)
                    .severity(rule.getSeverity())
                    .title(candidate.title())
                    .detail(candidate.detail())
                    .contextKey(candidate.contextKey())
                    .status(AlertStatus.OPEN)
                    .build();
            instanceRepo.save(instance);

            sendNotifications(rule, candidate);
            fired++;
        }
        return fired;
    }

    private boolean isDuplicate(AlertRule rule, AlertChecker.AlertCandidate candidate) {
        // Check for existing open/acknowledged alert with same context
        if (candidate.contextKey() != null) {
            boolean exists = instanceRepo.existsByRuleIdAndContextKeyAndStatusIn(
                    rule.getId(), candidate.contextKey(),
                    List.of(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED));
            if (exists) return true;

            // Check cooldown — don't re-alert within cooldown period even if resolved/dismissed
            AlertInstance latest = instanceRepo.findLatestByRuleIdAndContextKey(
                    rule.getId(), candidate.contextKey());
            if (latest != null) {
                OffsetDateTime cooldownEnd = latest.getCreatedAt().plusHours(rule.getCooldownHours());
                if (OffsetDateTime.now().isBefore(cooldownEnd)) return true;
            }
        }
        return false;
    }

    private void sendNotifications(AlertRule rule, AlertChecker.AlertCandidate candidate) {
        UUID directoryId = rule.getDirectory() != null ? rule.getDirectory().getId() : null;
        String link = "/superadmin/alerts";

        if (rule.isNotifyInApp()) {
            notificationService.sendToFeatureHolders(
                    directoryId,
                    FeatureKey.APPROVAL_MANAGE,  // all admins
                    "ALERT_" + rule.getRuleType().name(),
                    "[" + rule.getSeverity() + "] " + candidate.title(),
                    candidate.detail(),
                    link);
        }
    }
}
