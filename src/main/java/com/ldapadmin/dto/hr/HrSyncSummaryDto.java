package com.ldapadmin.dto.hr;

public record HrSyncSummaryDto(
        long totalEmployees,
        long activeEmployees,
        long terminatedEmployees,
        long matchedCount,
        long unmatchedCount,
        long orphanedCount
) {}
