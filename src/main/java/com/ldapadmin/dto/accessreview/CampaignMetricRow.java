package com.ldapadmin.dto.accessreview;

import com.ldapadmin.entity.enums.CampaignStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CampaignMetricRow(
        UUID id,
        String name,
        CampaignStatus status,
        OffsetDateTime activatedAt,
        OffsetDateTime completedAt,
        Long durationDays,
        long total,
        long confirmed,
        long revoked,
        long pending,
        double percentComplete
) {}
