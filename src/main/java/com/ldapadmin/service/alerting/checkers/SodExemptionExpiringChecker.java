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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SodExemptionExpiringChecker implements AlertChecker {

    private final SodViolationRepository violationRepo;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.SOD_EXEMPTION_EXPIRING;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        int days = getIntParam(rule, "days", 7);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime horizon = now.plusDays(days);

        List<SodViolation> exempted =
                violationRepo.findByDirectoryIdAndStatus(dc.getId(), SodViolationStatus.EXEMPTED);

        List<AlertCandidate> candidates = new ArrayList<>();
        for (SodViolation v : exempted) {
            if (v.getExemptionExpiresAt() != null
                    && v.getExemptionExpiresAt().isAfter(now)
                    && v.getExemptionExpiresAt().isBefore(horizon)) {
                candidates.add(new AlertCandidate(
                        "SoD exemption expiring within " + days + " days",
                        "Policy '" + v.getPolicy().getName() + "' — " + v.getUserDn() +
                                " (expires " + v.getExemptionExpiresAt().toLocalDate() + ")",
                        "sod-exempt-" + v.getId()));
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
