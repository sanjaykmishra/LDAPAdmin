package com.ldapadmin.dto.accessreview;

import java.util.UUID;

public record ReviewerMetricRow(
        UUID reviewerId,
        String username,
        long totalDecisions,
        long confirmed,
        long revoked,
        double revocationRate,
        Double avgResponseHours
) {}
