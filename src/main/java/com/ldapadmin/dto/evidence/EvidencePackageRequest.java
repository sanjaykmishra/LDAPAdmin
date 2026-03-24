package com.ldapadmin.dto.evidence;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for generating an evidence package ZIP.
 */
public record EvidencePackageRequest(
        @NotNull List<String> campaignIds,
        boolean includeSod,
        boolean includeEntitlements
) {}
