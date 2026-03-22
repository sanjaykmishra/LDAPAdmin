package com.ldapadmin.dto.accessreview;

import com.ldapadmin.entity.enums.CampaignStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CampaignDetailDto(
        UUID id,
        String name,
        String description,
        CampaignStatus status,
        OffsetDateTime startsAt,
        OffsetDateTime deadline,
        boolean autoRevoke,
        boolean autoRevokeOnExpiry,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        String createdByUsername,
        CampaignProgressDto progress,
        List<ReviewGroupDto> reviewGroups,
        List<CampaignHistoryDto> history
) {}
