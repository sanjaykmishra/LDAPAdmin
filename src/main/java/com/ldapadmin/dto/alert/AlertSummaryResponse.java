package com.ldapadmin.dto.alert;

public record AlertSummaryResponse(
        long openCount,
        long acknowledgedCount,
        long criticalCount,
        long highCount,
        long mediumCount,
        long lowCount
) {}
