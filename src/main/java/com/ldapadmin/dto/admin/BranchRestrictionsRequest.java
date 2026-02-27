package com.ldapadmin.dto.admin;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Replaces all branch restrictions for an admin on a specific realm.
 * An empty list removes all restrictions (granting full realm access).
 */
public record BranchRestrictionsRequest(
        @NotNull UUID realmId,
        List<String> branchDns) {
}
