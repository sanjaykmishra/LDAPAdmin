package com.ldapadmin.dto.accessreview;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record CrossCampaignReportDto(
        OffsetDateTime from,
        OffsetDateTime to,
        int totalCampaigns,
        Map<String, Long> campaignsByStatus,
        long totalDecisions,
        long totalConfirmed,
        long totalRevoked,
        long totalPending,
        double overallRevocationRate,
        Double avgCompletionDays,
        List<CampaignMetricRow> campaigns,
        List<ReviewerMetricRow> reviewers
) {}
