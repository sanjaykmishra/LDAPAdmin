package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AccountRole;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.service.alerting.AlertChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminAccountCreatedChecker implements AlertChecker {

    private final AccountRepository accountRepo;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.ADMIN_ACCOUNT_CREATED;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        // This check is not directory-scoped — admin accounts are global
        Instant cutoff = Instant.now().minusSeconds(30 * 60); // last 30 minutes

        List<AlertCandidate> candidates = new ArrayList<>();

        for (AccountRole role : List.of(AccountRole.SUPERADMIN, AccountRole.ADMIN)) {
            List<Account> accounts = accountRepo.findAllByRole(role);
            for (Account account : accounts) {
                if (account.getCreatedAt() != null && account.getCreatedAt().isAfter(cutoff)) {
                    candidates.add(new AlertCandidate(
                            "New " + role.name() + " account created",
                            "Account '" + account.getUsername() + "' created at " + account.getCreatedAt(),
                            "admin-created-" + account.getId()));
                }
            }
        }
        return candidates;
    }
}
