package com.ldapadmin.dto.accessreview;

import com.ldapadmin.entity.enums.CampaignStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CampaignSummaryDto(
        UUID id,
        String name,
        CampaignStatus status,
        OffsetDateTime startsAt,
        OffsetDateTime deadline,
        OffsetDateTime createdAt,
        String createdByUsername,
        CampaignProgressDto progress
) {}
