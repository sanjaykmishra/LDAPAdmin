package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.AuditorLink;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.repository.AuditorLinkRepository;
import com.ldapadmin.service.alerting.AlertChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuditorLinkExpiringChecker implements AlertChecker {

    private final AuditorLinkRepository auditorLinkRepo;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.AUDITOR_LINK_EXPIRING;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        int days = getIntParam(rule, "days", 7);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime horizon = now.plusDays(days);

        List<AuditorLink> links = auditorLinkRepo.findByDirectoryIdOrderByCreatedAtDesc(dc.getId());

        List<AlertCandidate> candidates = new ArrayList<>();
        for (AuditorLink link : links) {
            if (!link.isRevoked()
                    && link.getExpiresAt() != null
                    && link.getExpiresAt().isAfter(now)
                    && link.getExpiresAt().isBefore(horizon)) {
                candidates.add(new AlertCandidate(
                        "Auditor link expiring within " + days + " days",
                        "Link '" + link.getLabel() + "' expires on " +
                                link.getExpiresAt().toLocalDate(),
                        "link-expiring-" + link.getId()));
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
