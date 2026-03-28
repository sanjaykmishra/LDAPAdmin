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
public class ChangelogGapChecker implements AlertChecker {

    private final AuditQueryService auditQueryService;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.CHANGELOG_GAP;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        int hours = getIntParam(rule, "hours", 6);
        OffsetDateTime since = OffsetDateTime.now().minusHours(hours);

        var events = auditQueryService.query(dc.getId(), null, null, null, since, null, 0, 1);

        if (events.getTotalElements() == 0) {
            return List.of(new AlertCandidate(
                    "No audit events for '" + dc.getDisplayName() + "' in last " + hours + " hours",
                    "Last event may indicate a changelog polling issue or connectivity problem.",
                    "changelog-gap-" + dc.getId()));
        }
        return List.of();
    }

    private int getIntParam(AlertRule rule, String key, int defaultValue) {
        Object val = rule.getParams().get(key);
        if (val instanceof Number n) return n.intValue();
        return defaultValue;
    }
}
