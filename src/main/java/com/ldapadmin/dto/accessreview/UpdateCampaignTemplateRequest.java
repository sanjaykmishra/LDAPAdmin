package com.ldapadmin.dto.accessreview;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UpdateCampaignTemplateRequest(
        @NotBlank String name,
        String description,
        @NotNull @Min(1) Integer deadlineDays,
        Integer recurrenceMonths,
        boolean autoRevoke,
        boolean autoRevokeOnExpiry,
        @NotEmpty @Valid List<CreateCampaignTemplateRequest.GroupConfig> groups
) {}
