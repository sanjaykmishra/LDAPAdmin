package com.ldapadmin.dto.admin;

import com.ldapadmin.entity.enums.BaseRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Assigns or replaces the admin's base role on a specific realm. */
public record RealmRoleRequest(
        @NotNull UUID realmId,
        @NotNull BaseRole baseRole) {
}
