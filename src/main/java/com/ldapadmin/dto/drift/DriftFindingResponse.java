package com.ldapadmin.dto.drift;

import com.ldapadmin.entity.enums.DriftFindingSeverity;
import com.ldapadmin.entity.enums.DriftFindingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DriftFindingResponse(
        UUID id,
        String userDn,
        String userDisplay,
        String peerGroupValue,
        int peerGroupSize,
        String groupDn,
        String groupName,
        double peerMembershipPct,
        DriftFindingSeverity severity,
        DriftFindingStatus status,
        String ruleName,
        String acknowledgedByUsername,
        OffsetDateTime acknowledgedAt,
        String exemptionReason,
        OffsetDateTime detectedAt
) {}
