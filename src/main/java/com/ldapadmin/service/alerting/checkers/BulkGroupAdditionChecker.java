package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.service.AuditQueryService;
import com.ldapadmin.service.alerting.AlertChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BulkGroupAdditionChecker implements AlertChecker {

    private final AuditQueryService auditQueryService;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.BULK_GROUP_ADDITION;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        int windowHours = getIntParam(rule, "windowHours", 1);
        int threshold = getIntParam(rule, "threshold", 10);
        OffsetDateTime since = OffsetDateTime.now().minusHours(windowHours);

        var events = auditQueryService.query(dc.getId(), null, AuditAction.GROUP_MEMBER_ADD,
                null, since, null, 0, 200);

        // Group events by targetDn
        Map<String, Integer> countByGroup = new HashMap<>();
        for (var evt : events.getContent()) {
            String targetDn = evt.targetDn();
            if (targetDn != null) {
                countByGroup.merge(targetDn, 1, Integer::sum);
            }
        }

        List<AlertCandidate> candidates = new ArrayList<>();
        for (var entry : countByGroup.entrySet()) {
            if (entry.getValue() >= threshold) {
                String groupDn = entry.getKey();
                int hash = Math.abs(groupDn.hashCode());
                candidates.add(new AlertCandidate(
                        "Bulk group addition: " + entry.getValue() + " members added in " + windowHours + "h",
                        "Group: " + groupDn + " (" + entry.getValue() + " additions)",
                        "bulk-add-" + hash));
            }
        }
        return candidates;
    }

    private int getIntParam(AlertRule rule, String key, int defaultValue) {
        Object val = rule.getParams().get(key);
        if (val instanceof Number n) return n.intValue();
        return defaultValue;
    }
}
