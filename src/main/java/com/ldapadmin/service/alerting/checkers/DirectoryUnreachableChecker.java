package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.ldap.LdapConnectionFactory;
import com.ldapadmin.service.alerting.AlertChecker;
import com.unboundid.ldap.sdk.LDAPConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DirectoryUnreachableChecker implements AlertChecker {

    private final LdapConnectionFactory connectionFactory;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.DIRECTORY_UNREACHABLE;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        try {
            LDAPConnection conn = connectionFactory.openUnboundConnection(dc);
            conn.close();
            return List.of(); // reachable
        } catch (Exception e) {
            return List.of(new AlertCandidate(
                    "Directory '" + dc.getDisplayName() + "' is unreachable",
                    "Host: " + dc.getHost() + ":" + dc.getPort() + ". Error: " + e.getMessage(),
                    "dir-unreachable-" + dc.getId()));
        }
    }
}
