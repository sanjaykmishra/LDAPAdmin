package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.entity.enums.HrEmployeeStatus;
import com.ldapadmin.entity.hr.HrConnection;
import com.ldapadmin.entity.hr.HrEmployee;
import com.ldapadmin.repository.hr.HrConnectionRepository;
import com.ldapadmin.repository.hr.HrEmployeeRepository;
import com.ldapadmin.service.alerting.AlertChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccountPostTerminationChecker implements AlertChecker {

    private final HrConnectionRepository hrConnectionRepo;
    private final HrEmployeeRepository hrEmployeeRepo;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.ACCOUNT_POST_TERMINATION;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        Optional<HrConnection> hrConn = hrConnectionRepo.findByDirectoryId(dc.getId());
        if (hrConn.isEmpty()) return List.of();

        List<HrEmployee> terminated =
                hrEmployeeRepo.findByHrConnectionIdAndStatusAndMatchedLdapDnIsNotNull(
                        hrConn.get().getId(), HrEmployeeStatus.TERMINATED);

        LocalDate today = LocalDate.now();
        List<AlertCandidate> candidates = new ArrayList<>();
        for (HrEmployee emp : terminated) {
            if (emp.getTerminationDate() != null && emp.getTerminationDate().isBefore(today)) {
                candidates.add(new AlertCandidate(
                        "LDAP account still active after termination",
                        "Employee '" + emp.getDisplayName() + "' (ID: " + emp.getEmployeeId() +
                                ") terminated on " + emp.getTerminationDate() +
                                " but LDAP DN still exists: " + emp.getMatchedLdapDn(),
                        "post-term-" + emp.getId()));
            }
        }
        return candidates;
    }
}
