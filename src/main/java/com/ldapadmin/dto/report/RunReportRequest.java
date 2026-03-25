package com.ldapadmin.dto.report;

import com.ldapadmin.entity.enums.OutputFormat;
import com.ldapadmin.entity.enums.ReportType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request body for on-demand report execution.
 *
 * <p>Supports both {@link OutputFormat#CSV} and {@link OutputFormat#PDF} output.
 * If {@code outputFormat} is null, defaults to CSV.</p>
 */
public record RunReportRequest(
        @NotNull ReportType reportType,
        Map<String, Object> reportParams,
        OutputFormat outputFormat) {}
