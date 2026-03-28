package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.repository.AccessReviewDecisionRepository;
import com.ldapadmin.service.alerting.AlertChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UserNotReviewedChecker implements AlertChecker {

    private final AccessReviewDecisionRepository decisionRepo;
    private final LdapUserService userService;

    private static final String USER_OBJECTCLASS_FILTER = "(objectClass=person)";

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.USER_NOT_REVIEWED;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        int days = getIntParam(rule, "days", 90);
        OffsetDateTime since = OffsetDateTime.now().minusDays(days);

        long reviewedCount = decisionRepo.countDistinctReviewedUsersSince(dc.getId(), since);

        long totalUsers;
        try {
            totalUsers = userService.searchUsers(dc, USER_OBJECTCLASS_FILTER, null, Integer.MAX_VALUE, "1.1").size();
        } catch (Exception e) {
            return List.of(); // cannot determine user count
        }

        long gap = totalUsers - reviewedCount;
        if (gap > 0) {
            return List.of(new AlertCandidate(
                    gap + " users not reviewed in the last " + days + " days",
                    "Total users: " + totalUsers + ", reviewed: " + reviewedCount +
                            " (gap: " + gap + ")",
                    "users-not-reviewed-" + dc.getId()));
        }
        return List.of();
    }

    private int getIntParam(AlertRule rule, String key, int defaultValue) {
        Object val = rule.getParams().get(key);
        if (val instanceof Number n) return n.intValue();
        return defaultValue;
    }
}
