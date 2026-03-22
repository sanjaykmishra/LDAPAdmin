package com.ldapadmin.dto.accessreview;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateCampaignRequest(
        @NotBlank String name,
        String description,
        OffsetDateTime startsAt,
        @NotNull OffsetDateTime deadline,
        boolean autoRevoke,
        boolean autoRevokeOnExpiry,
        @NotEmpty @Valid List<GroupAssignment> groups
) {
    public record GroupAssignment(
            @NotBlank String groupDn,
            String memberAttribute,
            @NotNull UUID reviewerAccountId
    ) {}
}
