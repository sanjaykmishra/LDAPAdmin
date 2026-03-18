package com.ldapadmin.dto.realm;

import com.ldapadmin.entity.Realm;
import com.ldapadmin.entity.RealmObjectclass;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Read DTO for a realm. */
public record RealmResponse(
        UUID id,
        UUID directoryId,
        String directoryName,
        String name,
        String userBaseDn,
        String groupBaseDn,
        int displayOrder,
        List<UserFormEntry> userForms,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public record UserFormEntry(UUID id, String formName, String objectClassName) {}

    public static RealmResponse from(Realm r, List<RealmObjectclass> realmOcs) {
        List<UserFormEntry> forms = realmOcs.stream()
                .filter(oc -> oc.getUserForm() != null)
                .map(oc -> new UserFormEntry(
                        oc.getUserForm().getId(),
                        oc.getUserForm().getFormName(),
                        oc.getUserForm().getObjectClassName()))
                .toList();

        return new RealmResponse(
                r.getId(),
                r.getDirectory().getId(),
                r.getDirectory().getDisplayName(),
                r.getName(),
                r.getUserBaseDn(),
                r.getGroupBaseDn(),
                r.getDisplayOrder(),
                forms,
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
