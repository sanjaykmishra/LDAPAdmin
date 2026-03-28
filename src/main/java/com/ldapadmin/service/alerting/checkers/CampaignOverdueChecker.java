package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.AccessReviewCampaign;
import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.repository.AccessReviewCampaignRepository;
import com.ldapadmin.service.alerting.AlertChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CampaignOverdueChecker implements AlertChecker {

    private final AccessReviewCampaignRepository campaignRepo;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.CAMPAIGN_OVERDUE;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        OffsetDateTime now = OffsetDateTime.now();
        List<AccessReviewCampaign> campaigns =
                campaignRepo.findByDirectoryIdAndStatus(dc.getId(), CampaignStatus.ACTIVE);

        List<AlertCandidate> candidates = new ArrayList<>();
        for (AccessReviewCampaign c : campaigns) {
            if (c.getDeadline() != null && c.getDeadline().isBefore(now)) {
                long overdueDays = Duration.between(c.getDeadline(), now).toDays();
                candidates.add(new AlertCandidate(
                        "Campaign '" + c.getName() + "' is " + overdueDays + " day(s) overdue",
                        "Deadline was " + c.getDeadline().toLocalDate() + ". Campaign is still active.",
                        "campaign-overdue-" + c.getId()));
            }
        }
        return candidates;
    }
}
