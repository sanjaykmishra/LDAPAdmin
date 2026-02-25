package com.ldapadmin.dto.report;

import com.ldapadmin.entity.enums.DeliveryMethod;
import com.ldapadmin.entity.enums.OutputFormat;
import com.ldapadmin.entity.enums.ReportType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Read DTO for a scheduled report job definition.
 */
public record ScheduledReportJobDto(
        UUID id,
        UUID directoryId,
        String name,
        ReportType reportType,
        Map<String, Object> reportParams,
        String cronExpression,
        OutputFormat outputFormat,
        DeliveryMethod deliveryMethod,
        String deliveryRecipients,
        String s3KeyPrefix,
        boolean enabled,
        OffsetDateTime lastRunAt,
        String lastRunStatus,
        String lastRunMessage,
        UUID createdByAdminId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
