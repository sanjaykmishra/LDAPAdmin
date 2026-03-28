package com.ldapadmin.service.alerting;

import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AlertRuleType;

import java.util.List;

/**
 * Interface for alert rule evaluators. Each implementation checks one
 * specific condition and returns candidates for alerting.
 */
public interface AlertChecker {

    AlertRuleType ruleType();

    /**
     * Evaluate the rule against the given directory.
     *
     * @param dc   the directory to check (may be null for non-directory-scoped rules)
     * @param rule the alert rule with params and thresholds
     * @return candidates that should fire as new alert instances
     */
    List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule);

    record AlertCandidate(String title, String detail, String contextKey) {}
}
