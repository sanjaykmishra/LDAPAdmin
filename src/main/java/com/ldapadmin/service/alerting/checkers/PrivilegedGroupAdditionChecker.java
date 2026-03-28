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
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PrivilegedGroupAdditionChecker implements AlertChecker {

    private final AuditQueryService auditQueryService;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.PRIVILEGED_GROUP_ADDITION;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        Object groupsParam = rule.getParams().get("groups");
        if (!(groupsParam instanceof List<?> groupList) || groupList.isEmpty()) {
            return List.of(); // no privileged groups configured
        }
        Set<String> privilegedGroups = Set.copyOf((List<String>) groupList);

        // Look at recent GROUP_MEMBER_ADD events (last 30 minutes to cover evaluation gaps)
        OffsetDateTime since = OffsetDateTime.now().minusMinutes(30);
        var events = auditQueryService.query(dc.getId(), null, AuditAction.GROUP_MEMBER_ADD,
                null, since, null, 0, 100);

        List<AlertCandidate> candidates = new ArrayList<>();
        for (var evt : events.getContent()) {
            String targetDn = evt.targetDn();
            if (targetDn != null && privilegedGroups.stream().anyMatch(
                    pg -> targetDn.toLowerCase().contains(pg.toLowerCase()))) {
                candidates.add(new AlertCandidate(
                        "User added to privileged group",
                        "Group: " + targetDn + ", Actor: " + evt.actorUsername(),
                        "priv-group-" + evt.id()));
            }
        }
        return candidates;
    }
}
