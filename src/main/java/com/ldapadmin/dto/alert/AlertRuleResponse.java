package com.ldapadmin.dto.alert;

import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.enums.AlertSeverity;

import java.util.Map;
import java.util.UUID;

public record AlertRuleResponse(
        UUID id,
        UUID directoryId,
        String directoryName,
        String ruleType,
        boolean enabled,
        AlertSeverity severity,
        Map<String, Object> params,
        boolean notifyInApp,
        boolean notifyEmail,
        String emailRecipients,
        int cooldownHours
) {
    public static AlertRuleResponse from(AlertRule r, String directoryName) {
        return new AlertRuleResponse(
                r.getId(),
                r.getDirectory() != null ? r.getDirectory().getId() : null,
                directoryName,
                r.getRuleType().name(),
                r.isEnabled(),
                r.getSeverity(),
                r.getParams(),
                r.isNotifyInApp(),
                r.isNotifyEmail(),
                r.getEmailRecipients(),
                r.getCooldownHours());
    }
}
