package com.ldapadmin.dto.realm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Create / update request for a realm.
 */
public record RealmRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank String userBaseDn,
        @NotBlank String groupBaseDn,
        List<UUID> userTemplateIds) {
}
