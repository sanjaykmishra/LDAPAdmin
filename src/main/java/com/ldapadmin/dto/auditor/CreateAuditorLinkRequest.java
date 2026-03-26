package com.ldapadmin.dto.auditor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Request body for creating a new auditor link.
 */
public record CreateAuditorLinkRequest(
        @Size(max = 255) String label,
        @NotNull List<UUID> campaignIds,
        boolean includeSod,
        boolean includeEntitlements,
        boolean includeAuditEvents,
        OffsetDateTime dataFrom,
        OffsetDateTime dataTo,
        @Min(1) @Max(365) int expiryDays
) {}
