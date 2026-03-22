package com.ldapadmin.dto.accessreview;

import com.ldapadmin.entity.enums.ReviewDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BulkDecisionRequest(
        @NotEmpty @Valid List<BulkDecisionItem> items
) {
    public record BulkDecisionItem(
            @NotNull UUID decisionId,
            @NotNull ReviewDecision decision,
            String comment
    ) {}
}
