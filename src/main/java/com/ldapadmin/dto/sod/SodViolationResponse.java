package com.ldapadmin.dto.sod;

import com.ldapadmin.entity.enums.SodViolationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SodViolationResponse(
        UUID id,
        UUID policyId,
        String policyName,
        String userDn,
        String userDisplayName,
        SodViolationStatus status,
        OffsetDateTime detectedAt,
        OffsetDateTime resolvedAt,
        String exemptedByUsername,
        String exemptionReason
) {}
