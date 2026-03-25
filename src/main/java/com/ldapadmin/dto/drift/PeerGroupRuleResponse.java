package com.ldapadmin.dto.drift;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PeerGroupRuleResponse(
        UUID id,
        String name,
        String groupingAttribute,
        int normalThresholdPct,
        int anomalyThresholdPct,
        boolean enabled,
        String createdByUsername,
        OffsetDateTime createdAt
) {}
