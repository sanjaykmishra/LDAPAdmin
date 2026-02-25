package com.ldapadmin.dto.report;

import com.ldapadmin.entity.enums.DeliveryMethod;
import com.ldapadmin.entity.enums.OutputFormat;
import com.ldapadmin.entity.enums.ReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Write DTO for creating or replacing a scheduled report job.
 *
 * <p>Parameter requirements by report type:
 * <ul>
 *   <li>{@code USERS_IN_GROUP}  — {@code reportParams.groupDn} (required)</li>
 *   <li>{@code USERS_IN_BRANCH} — {@code reportParams.branchDn} (required)</li>
 *   <li>{@code RECENTLY_ADDED}, {@code RECENTLY_MODIFIED},
 *       {@code RECENTLY_DELETED} — {@code reportParams.lookbackDays} (default 30)</li>
 *   <li>{@code USERS_WITH_NO_GROUP}, {@code DISABLED_ACCOUNTS} — no params required</li>
 * </ul>
 */
public record CreateScheduledReportJobRequest(
        @NotBlank String name,
        @NotNull ReportType reportType,
        Map<String, Object> reportParams,
        @NotBlank String cronExpression,
        @NotNull OutputFormat outputFormat,
        @NotNull DeliveryMethod deliveryMethod,
        /** Comma-separated email addresses (required when deliveryMethod = EMAIL). */
        String deliveryRecipients,
        /** S3 key prefix (required when deliveryMethod = S3). */
        String s3KeyPrefix,
        boolean enabled) {}
