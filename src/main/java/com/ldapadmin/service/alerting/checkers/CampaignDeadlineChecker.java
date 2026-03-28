package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.repository.AccessReviewCampaignRepository;
import com.ldapadmin.repository.AccessReviewDecisionRepository;
import com.ldapadmin.service.alerting.AlertChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CampaignDeadlineChecker implements AlertChecker {

    private final AccessReviewCampaignRepository campaignRepo;
    private final AccessReviewDecisionRepository decisionRepo;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.CAMPAIGN_DEADLINE_APPROACHING;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        int days = getIntParam(rule, "days", 3);
        int minPct = getIntParam(rule, "minCompletionPct", 50);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime window = now.plusDays(days);

        List<AccessReviewCampaign> campaigns =
                campaignRepo.findByDirectoryIdAndStatus(dc.getId(), CampaignStatus.ACTIVE);

        List<AlertCandidate> candidates = new ArrayList<>();
        for (AccessReviewCampaign c : campaigns) {
            if (c.getDeadline() == null || c.getDeadline().isAfter(window) || c.getDeadline().isBefore(now)) {
                continue;
            }
            long total = decisionRepo.countTotalByCampaignId(c.getId());
            long pending = decisionRepo.countPendingByCampaignId(c.getId());
            double pct = total > 0 ? ((total - pending) * 100.0 / total) : 100;

            if (pct < minPct) {
                candidates.add(new AlertCandidate(
                        "Campaign '" + c.getName() + "' deadline approaching — " + Math.round(pct) + "% complete",
                        "Deadline: " + c.getDeadline().toLocalDate() + ". " +
                                pending + " of " + total + " decisions still pending.",
                        "campaign-deadline-" + c.getId()));
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
