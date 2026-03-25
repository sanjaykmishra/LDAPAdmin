package com.ldapadmin.dto.accessreview;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CampaignTemplateResponse(
        UUID id,
        String name,
        String description,
        UUID directoryId,
        CampaignTemplateConfigDto config,
        String createdByUsername,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record CampaignTemplateConfigDto(
            Integer deadlineDays,
            Integer recurrenceMonths,
            boolean autoRevoke,
            boolean autoRevokeOnExpiry,
            List<GroupConfigDto> groups
    ) {}

    public record GroupConfigDto(
            String groupDn,
            String memberAttribute,
            UUID reviewerAccountId,
            String reviewerUsername
    ) {}
}
