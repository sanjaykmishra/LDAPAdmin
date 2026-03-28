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

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SodViolationNewChecker implements AlertChecker {

    private final SodViolationRepository violationRepo;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.SOD_VIOLATION_NEW;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        List<SodViolation> openViolations =
                violationRepo.findByDirectoryIdAndStatus(dc.getId(), SodViolationStatus.OPEN);

        List<AlertCandidate> candidates = new ArrayList<>();
        for (SodViolation v : openViolations) {
            candidates.add(new AlertCandidate(
                    "SoD violation: " + v.getUserDn(),
                    "Policy '" + v.getPolicy().getName() + "' violated by " + v.getUserDn(),
                    "sod-" + v.getId()));
        }
        return candidates;
    }
}
