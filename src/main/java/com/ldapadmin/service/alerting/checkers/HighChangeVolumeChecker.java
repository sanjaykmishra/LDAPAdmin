package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.service.AuditQueryService;
import com.ldapadmin.service.alerting.AlertChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class HighChangeVolumeChecker implements AlertChecker {

    private final AuditQueryService auditQueryService;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.HIGH_CHANGE_VOLUME;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        int windowHours = getIntParam(rule, "windowHours", 1);
        int threshold = getIntParam(rule, "threshold", 100);
        OffsetDateTime since = OffsetDateTime.now().minusHours(windowHours);

        var events = auditQueryService.query(dc.getId(), null, null,
                null, since, null, 0, 1);

        long total = events.getTotalElements();
        if (total > threshold) {
            return List.of(new AlertCandidate(
                    "High change volume: " + total + " events in last " + windowHours + "h",
                    "Directory '" + dc.getDisplayName() + "' had " + total +
                            " audit events (threshold: " + threshold + ")",
                    "high-volume-" + dc.getId()));
        }
        return List.of();
    }

    private int getIntParam(AlertRule rule, String key, int defaultValue) {
        Object val = rule.getParams().get(key);
        if (val instanceof Number n) return n.intValue();
        return defaultValue;
    }
}
