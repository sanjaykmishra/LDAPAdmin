package com.ldapadmin.dto.sod;

import com.ldapadmin.entity.enums.SodAction;
import com.ldapadmin.entity.enums.SodSeverity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SodPolicyResponse(
        UUID id,
        String name,
        String description,
        UUID directoryId,
        String groupADn,
        String groupBDn,
        String groupAName,
        String groupBName,
        SodSeverity severity,
        SodAction action,
        boolean enabled,
        long openViolationCount,
        String createdByUsername,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
