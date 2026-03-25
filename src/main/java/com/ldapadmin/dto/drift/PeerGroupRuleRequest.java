package com.ldapadmin.dto.drift;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PeerGroupRuleRequest(
        @NotBlank String name,
        @NotBlank String groupingAttribute,
        @Min(1) @Max(100) int normalThresholdPct,
        @Min(1) @Max(100) int anomalyThresholdPct,
        boolean enabled
) {}
