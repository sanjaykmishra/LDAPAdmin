package com.ldapadmin.dto.accessreview;

import com.ldapadmin.entity.enums.CampaignStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CampaignHistoryDto(
        UUID id,
        CampaignStatus oldStatus,
        CampaignStatus newStatus,
        String changedByUsername,
        OffsetDateTime changedAt,
        String note
) {}
