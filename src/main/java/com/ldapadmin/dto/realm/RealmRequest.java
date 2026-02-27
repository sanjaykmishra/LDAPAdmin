package com.ldapadmin.dto.realm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create / update request for a realm.
 */
public record RealmRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank String userBaseDn,
        @NotBlank String groupBaseDn,
        @NotBlank String primaryUserObjectclass,
        int displayOrder,
        List<AuxEntry> auxiliaryObjectclasses) {

    /** An auxiliary objectClass entry within a realm. */
    public record AuxEntry(
            @NotBlank String objectclassName,
            int displayOrder) {
    }
}
