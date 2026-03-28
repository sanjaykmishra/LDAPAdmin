package com.ldapadmin.dto.alert;

import com.ldapadmin.entity.AlertInstance;
import com.ldapadmin.entity.enums.AlertSeverity;
import com.ldapadmin.entity.enums.AlertStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AlertInstanceResponse(
        UUID id,
        String ruleType,
        String directoryName,
        UUID directoryId,
        AlertSeverity severity,
        String title,
        String detail,
        String contextKey,
        AlertStatus status,
        UUID acknowledgedBy,
        OffsetDateTime acknowledgedAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt
) {
    public static AlertInstanceResponse from(AlertInstance i, String directoryName) {
        return new AlertInstanceResponse(
                i.getId(),
                i.getRule().getRuleType().name(),
                directoryName,
                i.getDirectoryId(),
                i.getSeverity(),
                i.getTitle(),
                i.getDetail(),
                i.getContextKey(),
                i.getStatus(),
                i.getAcknowledgedBy(),
                i.getAcknowledgedAt(),
                i.getResolvedAt(),
                i.getCreatedAt());
    }
}
