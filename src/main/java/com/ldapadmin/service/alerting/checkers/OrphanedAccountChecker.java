package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.entity.hr.HrConnection;
import com.ldapadmin.entity.hr.HrEmployee;
import com.ldapadmin.repository.hr.HrConnectionRepository;
import com.ldapadmin.repository.hr.HrEmployeeRepository;
import com.ldapadmin.service.alerting.AlertChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrphanedAccountChecker implements AlertChecker {

    private final HrConnectionRepository hrConnectionRepo;
    private final HrEmployeeRepository hrEmployeeRepo;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.ORPHANED_ACCOUNT;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        Optional<HrConnection> hrConn = hrConnectionRepo.findByDirectoryId(dc.getId());
        if (hrConn.isEmpty()) return List.of();

        List<HrEmployee> orphans =
                hrEmployeeRepo.findByHrConnectionIdAndMatchedLdapDnIsNull(hrConn.get().getId());

        List<AlertCandidate> candidates = new ArrayList<>();
        for (HrEmployee emp : orphans) {
            candidates.add(new AlertCandidate(
                    "Orphaned HR employee — no matching LDAP account",
                    "Employee '" + emp.getDisplayName() + "' (ID: " + emp.getEmployeeId() +
                            ") has no matched LDAP DN",
                    "orphan-" + emp.getId()));
        }
        return candidates;
    }
}
