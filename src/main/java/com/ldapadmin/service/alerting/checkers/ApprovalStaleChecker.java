package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.PendingApproval;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.entity.enums.ApprovalStatus;
import com.ldapadmin.repository.PendingApprovalRepository;
import com.ldapadmin.service.alerting.AlertChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApprovalStaleChecker implements AlertChecker {

    private final PendingApprovalRepository approvalRepo;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.APPROVAL_STALE;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        int days = getIntParam(rule, "days", 7);
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(days);

        List<PendingApproval> pending =
                approvalRepo.findAllByDirectoryIdAndStatus(dc.getId(), ApprovalStatus.PENDING);

        List<AlertCandidate> candidates = new ArrayList<>();
        for (PendingApproval pa : pending) {
            if (pa.getCreatedAt().isBefore(cutoff)) {
                long ageDays = Duration.between(pa.getCreatedAt(), OffsetDateTime.now()).toDays();
                candidates.add(new AlertCandidate(
                        "Approval request pending for " + ageDays + " days",
                        "Type: " + pa.getRequestType() + " (submitted " + pa.getCreatedAt().toLocalDate() + ")",
                        "approval-stale-" + pa.getId()));
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
