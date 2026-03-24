package com.ldapadmin.dto.sod;

public record SodScanResultDto(
        int policiesScanned,
        int violationsFound,
        int newViolations,
        int resolvedViolations
) {}
