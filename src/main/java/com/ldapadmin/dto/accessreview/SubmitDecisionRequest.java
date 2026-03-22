package com.ldapadmin.dto.accessreview;

import com.ldapadmin.entity.enums.ReviewDecision;
import jakarta.validation.constraints.NotNull;

public record SubmitDecisionRequest(
        @NotNull ReviewDecision decision,
        String comment
) {}
