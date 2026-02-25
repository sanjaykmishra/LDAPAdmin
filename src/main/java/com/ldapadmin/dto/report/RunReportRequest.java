package com.ldapadmin.dto.report;

import com.ldapadmin.entity.enums.OutputFormat;
import com.ldapadmin.entity.enums.ReportType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request body for on-demand report execution.
 *
 * <p>The response is a CSV file download ({@link OutputFormat#CSV} only;
 * {@link OutputFormat#PDF} is not yet supported).</p>
 */
public record RunReportRequest(
        @NotNull ReportType reportType,
        Map<String, Object> reportParams,
        OutputFormat outputFormat) {}
