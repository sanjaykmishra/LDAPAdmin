package com.ldapadmin.dto.evidence;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request body for generating an evidence package ZIP.
 */
public record EvidencePackageRequest(
        @NotNull List<UUID> campaignIds,
        boolean includeSod,
        boolean includeEntitlements,
        boolean includeAuditEvents
) {}
