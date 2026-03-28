package com.ldapadmin.service.alerting.checkers;

import com.ldapadmin.entity.AlertRule;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.ScheduledReportJob;
import com.ldapadmin.entity.enums.AlertRuleType;
import com.ldapadmin.repository.ScheduledReportJobRepository;
import com.ldapadmin.service.alerting.AlertChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ScheduledReportFailureChecker implements AlertChecker {

    private final ScheduledReportJobRepository jobRepo;

    @Override
    public AlertRuleType ruleType() {
        return AlertRuleType.SCHEDULED_REPORT_FAILURE;
    }

    @Override
    public List<AlertCandidate> evaluate(DirectoryConnection dc, AlertRule rule) {
        if (dc == null) return List.of();

        List<ScheduledReportJob> jobs = jobRepo.findAllByDirectoryId(dc.getId());

        List<AlertCandidate> candidates = new ArrayList<>();
        for (ScheduledReportJob job : jobs) {
            if (job.isEnabled() && "FAILURE".equals(job.getLastRunStatus())) {
                candidates.add(new AlertCandidate(
                        "Scheduled report '" + job.getName() + "' failed",
                        "Last run: " + job.getLastRunAt() + ". Error: " + job.getLastRunMessage(),
                        "report-fail-" + job.getId()));
            }
        }
        return candidates;
    }
}
