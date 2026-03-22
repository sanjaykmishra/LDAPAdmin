package com.ldapadmin.dto.accessreview;

import java.util.UUID;

public record ReviewGroupDto(
        UUID id,
        String groupDn,
        String groupName,
        String memberAttribute,
        String reviewerUsername,
        UUID reviewerId,
        long total,
        long confirmed,
        long revoked,
        long pending
) {}
