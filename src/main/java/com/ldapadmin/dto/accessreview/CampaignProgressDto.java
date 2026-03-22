package com.ldapadmin.dto.accessreview;

public record CampaignProgressDto(
        long total,
        long confirmed,
        long revoked,
        long pending,
        double percentComplete
) {}
