package com.ldapadmin.dto.alert;

import com.ldapadmin.entity.enums.AlertSeverity;

import java.util.Map;

public record UpdateAlertRuleRequest(
        Boolean enabled,
        AlertSeverity severity,
        Map<String, Object> params,
        Boolean notifyInApp,
        Boolean notifyEmail,
        String emailRecipients,
        Integer cooldownHours
) {}
