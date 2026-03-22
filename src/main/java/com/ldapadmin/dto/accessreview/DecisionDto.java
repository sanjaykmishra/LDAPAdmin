package com.ldapadmin.dto.accessreview;

import com.ldapadmin.entity.enums.ReviewDecision;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DecisionDto(
        UUID id,
        String memberDn,
        String memberDisplay,
        ReviewDecision decision,
        String comment,
        String decidedByUsername,
        OffsetDateTime decidedAt,
        OffsetDateTime revokedAt
) {}
