package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.service.alerting.AlertChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class IntegrityViolationChecker implements AlertChecker {

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.INTEGRITY_VIOLATION;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        // TODO: Integrity check is a heavy operation that runs on demand.
        //       Implement when the integrity verification service is available.
        return List.of();
    }
}
