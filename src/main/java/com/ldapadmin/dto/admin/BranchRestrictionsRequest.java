package com.ldapadmin.dto.admin;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Replaces all branch restrictions for an admin on a specific directory.
 * An empty list removes all restrictions (granting full branch access).
 */
public record BranchRestrictionsRequest(
        @NotNull UUID directoryId,
        List<String> branchDns) {
}
