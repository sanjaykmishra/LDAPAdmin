package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.AccessReviewGroup;
import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.repository.AccessReviewCampaignRepository;
import com.ldapadmin.repository.AccessReviewDecisionRepository;
import com.ldapadmin.service.alerting.AlertChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReviewerInactiveChecker implements AlertChecker {

    private final AccessReviewCampaignRepository campaignRepo;
    private final AccessReviewDecisionRepository decisionRepo;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.REVIEWER_INACTIVE;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        int days = getIntParam(rule, "days", 5);
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(days);

        List<AccessReviewCampaign> activeCampaigns =
                campaignRepo.findByDirectoryIdAndStatus(dc.getId(), CampaignStatus.ACTIVE);

        List<AlertCandidate> candidates = new ArrayList<>();
        for (AccessReviewCampaign campaign : activeCampaigns) {
            // Only consider campaigns that have been active longer than the threshold
            if (campaign.getCreatedAt() == null || campaign.getCreatedAt().isAfter(cutoff)) {
                continue;
            }
            for (AccessReviewGroup rg : campaign.getReviewGroups()) {
                long pending = decisionRepo.countByReviewGroupIdAndDecisionIsNull(rg.getId());
                if (pending > 0) {
                    long inactiveDays = Duration.between(campaign.getCreatedAt(), OffsetDateTime.now()).toDays();
                    candidates.add(new AlertCandidate(
                            "Reviewer inactive for " + inactiveDays + " days with " + pending + " pending decisions",
                            "Campaign '" + campaign.getName() + "', group '" + rg.getGroupName() +
                                    "', reviewer: " + rg.getReviewer().getUsername(),
                            "reviewer-inactive-" + rg.getId()));
                }
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
