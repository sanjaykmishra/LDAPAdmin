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
public class DisabledAccountInGroupsChecker implements AlertChecker {

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.DISABLED_ACCOUNT_IN_GROUPS;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        // TODO: Implement directory-type-specific LDAP queries to find disabled users
        //       that still have group memberships. Requires different filters for AD
        //       (userAccountControl bit) vs OpenLDAP (nsAccountLock=true).
        return List.of();
    }
}
