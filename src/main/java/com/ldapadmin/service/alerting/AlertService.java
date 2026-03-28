package com.ldapadmin.service.alerting;

import com.ldapadmin.dto.alert.*;
import com.ldapadmin.entity.AlertInstance;
import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.*;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AlertInstanceRepository;
import com.ldapadmin.repository.AlertRuleRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertInstanceRepository instanceRepo;
    private final AlertRuleRepository ruleRepo;
    private final DirectoryConnectionRepository dirRepo;

    // ── Instances ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AlertInstanceResponse> listInstances(UUID directoryId, AlertStatus status,
                                                      AlertSeverity severity, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        Page<AlertInstance> instances = instanceRepo.findFiltered(
                directoryId, status, severity, pageable);

        return instances.map(i -> {
            String dirName = resolveDirectoryName(i.getDirectoryId());
            return AlertInstanceResponse.from(i, dirName);
        });
    }

    @Transactional(readOnly = true)
    public AlertSummaryResponse getSummary() {
        return new AlertSummaryResponse(
                instanceRepo.countByStatus(AlertStatus.OPEN),
                instanceRepo.countByStatus(AlertStatus.ACKNOWLEDGED),
                instanceRepo.countByStatusAndSeverity(AlertStatus.OPEN, AlertSeverity.CRITICAL),
                instanceRepo.countByStatusAndSeverity(AlertStatus.OPEN, AlertSeverity.HIGH),
                instanceRepo.countByStatusAndSeverity(AlertStatus.OPEN, AlertSeverity.MEDIUM),
                instanceRepo.countByStatusAndSeverity(AlertStatus.OPEN, AlertSeverity.LOW));
    }

    @Transactional
    public void acknowledge(UUID instanceId, UUID accountId) {
        AlertInstance instance = requireInstance(instanceId);
        instance.setStatus(AlertStatus.ACKNOWLEDGED);
        instance.setAcknowledgedBy(accountId);
        instance.setAcknowledgedAt(OffsetDateTime.now());
        instanceRepo.save(instance);
    }

    @Transactional
    public void dismiss(UUID instanceId) {
        AlertInstance instance = requireInstance(instanceId);
        instance.setStatus(AlertStatus.DISMISSED);
        instance.setResolvedAt(OffsetDateTime.now());
        instanceRepo.save(instance);
    }

    @Transactional
    public void resolve(UUID instanceId) {
        AlertInstance instance = requireInstance(instanceId);
        instance.setStatus(AlertStatus.RESOLVED);
        instance.setResolvedAt(OffsetDateTime.now());
        instanceRepo.save(instance);
    }

    // ── Rules ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> listRules(UUID directoryId) {
        List<AlertRule> rules = directoryId != null
                ? ruleRepo.findAllByDirectoryIdOrderByRuleTypeAsc(directoryId)
                : ruleRepo.findAllOrderedByDirectoryAndType();

        return rules.stream().map(r -> {
            String dirName = r.getDirectory() != null ? r.getDirectory().getDisplayName() : null;
            return AlertRuleResponse.from(r, dirName);
        }).toList();
    }

    @Transactional
    public AlertRuleResponse updateRule(UUID ruleId, UpdateAlertRuleRequest req) {
        AlertRule rule = ruleRepo.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("AlertRule", ruleId));

        if (req.enabled() != null) rule.setEnabled(req.enabled());
        if (req.severity() != null) rule.setSeverity(req.severity());
        if (req.params() != null) rule.setParams(req.params());
        if (req.notifyInApp() != null) rule.setNotifyInApp(req.notifyInApp());
        if (req.notifyEmail() != null) rule.setNotifyEmail(req.notifyEmail());
        if (req.emailRecipients() != null) rule.setEmailRecipients(req.emailRecipients());
        if (req.cooldownHours() != null) rule.setCooldownHours(req.cooldownHours());

        rule = ruleRepo.save(rule);
        String dirName = rule.getDirectory() != null ? rule.getDirectory().getDisplayName() : null;
        return AlertRuleResponse.from(rule, dirName);
    }

    @Transactional
    public List<AlertRuleResponse> initializeDefaults(UUID directoryId) {
        DirectoryConnection dc = dirRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));

        List<AlertRule> created = new ArrayList<>();
        for (var def : DEFAULT_RULES) {
            if (ruleRepo.findByDirectoryIdAndRuleType(directoryId, def.type).isPresent()) {
                continue; // already exists
            }
            AlertRule rule = new AlertRule();
            rule.setDirectory(dc);
            rule.setRuleType(def.type);
            rule.setEnabled(def.enabled);
            rule.setSeverity(def.severity);
            rule.setParams(def.params);
            rule.setCooldownHours(def.cooldownHours);
            created.add(ruleRepo.save(rule));
        }

        return created.stream().map(r -> AlertRuleResponse.from(r, dc.getDisplayName())).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AlertInstance requireInstance(UUID id) {
        return instanceRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AlertInstance", id));
    }

    private String resolveDirectoryName(UUID directoryId) {
        if (directoryId == null) return null;
        return dirRepo.findById(directoryId).map(DirectoryConnection::getDisplayName).orElse(null);
    }

    // ── Default rule definitions ─────────────────────────────────────────────

    private record RuleDef(AlertRuleType type, AlertSeverity severity, boolean enabled,
                           Map<String, Object> params, int cooldownHours) {}

    private static final List<RuleDef> DEFAULT_RULES = List.of(
            new RuleDef(AlertRuleType.SOD_VIOLATION_NEW, AlertSeverity.HIGH, true, Map.of(), 1),
            new RuleDef(AlertRuleType.SOD_VIOLATION_UNRESOLVED, AlertSeverity.MEDIUM, true, Map.of("days", 14), 24),
            new RuleDef(AlertRuleType.SOD_EXEMPTION_EXPIRING, AlertSeverity.MEDIUM, false, Map.of("days", 7), 24),
            new RuleDef(AlertRuleType.CAMPAIGN_DEADLINE_APPROACHING, AlertSeverity.HIGH, true, Map.of("days", 3, "minCompletionPct", 50), 24),
            new RuleDef(AlertRuleType.CAMPAIGN_OVERDUE, AlertSeverity.CRITICAL, true, Map.of(), 24),
            new RuleDef(AlertRuleType.REVIEWER_INACTIVE, AlertSeverity.MEDIUM, false, Map.of("days", 5), 48),
            new RuleDef(AlertRuleType.USER_NOT_REVIEWED, AlertSeverity.LOW, false, Map.of("days", 90), 168),
            new RuleDef(AlertRuleType.PRIVILEGED_GROUP_ADDITION, AlertSeverity.CRITICAL, false, Map.of("groups", List.of()), 1),
            new RuleDef(AlertRuleType.ADMIN_ACCOUNT_CREATED, AlertSeverity.HIGH, false, Map.of(), 1),
            new RuleDef(AlertRuleType.BULK_GROUP_ADDITION, AlertSeverity.HIGH, false, Map.of("threshold", 10, "windowHours", 1), 4),
            new RuleDef(AlertRuleType.DISABLED_ACCOUNT_IN_GROUPS, AlertSeverity.MEDIUM, false, Map.of(), 24),
            new RuleDef(AlertRuleType.DORMANT_ACCOUNT, AlertSeverity.LOW, false, Map.of("days", 90), 168),
            new RuleDef(AlertRuleType.APPROVAL_STALE, AlertSeverity.MEDIUM, true, Map.of("days", 7), 48),
            new RuleDef(AlertRuleType.PROVISIONING_FAILURE, AlertSeverity.HIGH, true, Map.of(), 1),
            new RuleDef(AlertRuleType.DIRECTORY_UNREACHABLE, AlertSeverity.CRITICAL, true, Map.of(), 1),
            new RuleDef(AlertRuleType.CHANGELOG_GAP, AlertSeverity.HIGH, true, Map.of("hours", 6), 4),
            new RuleDef(AlertRuleType.HIGH_CHANGE_VOLUME, AlertSeverity.HIGH, false, Map.of("threshold", 100, "windowHours", 1), 4),
            new RuleDef(AlertRuleType.INTEGRITY_VIOLATION, AlertSeverity.HIGH, false, Map.of(), 24),
            new RuleDef(AlertRuleType.SCHEDULED_REPORT_FAILURE, AlertSeverity.MEDIUM, true, Map.of(), 24),
            new RuleDef(AlertRuleType.AUDITOR_LINK_EXPIRING, AlertSeverity.MEDIUM, false, Map.of("days", 7), 24)
    );
}
