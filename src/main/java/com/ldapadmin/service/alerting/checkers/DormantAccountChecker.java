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
public class DormantAccountChecker implements AlertChecker {

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.DORMANT_ACCOUNT;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        // TODO: Implement directory-type-specific LDAP queries to detect dormant accounts.
        //       Requires reading lastLogon/lastLogonTimestamp (AD) or similar attributes
        //       which vary by directory type.
        return List.of();
    }
}
