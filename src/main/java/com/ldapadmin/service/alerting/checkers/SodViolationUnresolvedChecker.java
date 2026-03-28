package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.SodViolation;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.entity.enums.SodViolationStatus;
import com.ldapadmin.repository.SodViolationRepository;
import com.ldapadmin.service.alerting.AlertChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SodViolationUnresolvedChecker implements AlertChecker {

    private final SodViolationRepository violationRepo;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.SOD_VIOLATION_UNRESOLVED;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        int days = getIntParam(rule, "days", 14);
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(days);

        List<SodViolation> openViolations =
                violationRepo.findByDirectoryIdAndStatus(dc.getId(), SodViolationStatus.OPEN);

        List<AlertCandidate> candidates = new ArrayList<>();
        for (SodViolation v : openViolations) {
            if (v.getDetectedAt() != null && v.getDetectedAt().isBefore(cutoff)) {
                long ageDays = Duration.between(v.getDetectedAt(), OffsetDateTime.now()).toDays();
                candidates.add(new AlertCandidate(
                        "SoD violation unresolved for " + ageDays + " days",
                        "Policy '" + v.getPolicy().getName() + "' — " + v.getUserDn() +
                                " (open since " + v.getDetectedAt().toLocalDate() + ")",
                        "sod-unresolved-" + v.getId()));
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
