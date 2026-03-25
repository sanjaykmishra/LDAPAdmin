package com.ldapadmin.dto.drift;

import java.time.OffsetDateTime;

public record DriftSummaryResponse(
        long openHigh,
        long openMedium,
        long openLow,
        long openTotal,
        OffsetDateTime lastAnalysisAt
) {}
