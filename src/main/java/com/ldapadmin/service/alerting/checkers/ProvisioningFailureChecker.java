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

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProvisioningFailureChecker implements AlertChecker {

    private final PendingApprovalRepository approvalRepo;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.PROVISIONING_FAILURE;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        List<PendingApproval> approvals =
                approvalRepo.findAllByDirectoryIdAndStatus(dc.getId(), ApprovalStatus.APPROVED);

        List<AlertCandidate> candidates = new ArrayList<>();
        for (PendingApproval pa : approvals) {
            if (pa.getProvisionError() != null && !pa.getProvisionError().isBlank()) {
                candidates.add(new AlertCandidate(
                        "Provisioning failure: " + pa.getRequestType(),
                        "Error: " + pa.getProvisionError(),
                        "provision-fail-" + pa.getId()));
            }
        }
        return candidates;
    }
}
