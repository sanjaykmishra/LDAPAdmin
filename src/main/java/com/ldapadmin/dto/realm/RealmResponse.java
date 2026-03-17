package com.ldapadmin.dto.realm;

import com.ldapadmin.entity.Realm;
import com.ldapadmin.entity.RealmAuxiliaryObjectclass;
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
        String primaryUserObjectclass,
        int displayOrder,
        UUID userFormId,
        List<AuxEntry> auxiliaryObjectclasses,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public record AuxEntry(UUID id, String objectclassName, int displayOrder) {
        public static AuxEntry from(RealmAuxiliaryObjectclass aux) {
            return new AuxEntry(aux.getId(), aux.getObjectclassName(), aux.getDisplayOrder());
        }
    }

    public static RealmResponse from(Realm r, List<RealmObjectclass> realmOcs) {
        UUID formId = realmOcs.stream()
                .filter(oc -> oc.getUserForm() != null)
                .map(oc -> oc.getUserForm().getId())
                .findFirst()
                .orElse(null);

        return new RealmResponse(
                r.getId(),
                r.getDirectory().getId(),
                r.getDirectory().getDisplayName(),
                r.getName(),
                r.getUserBaseDn(),
                r.getGroupBaseDn(),
                r.getPrimaryUserObjectclass(),
                r.getDisplayOrder(),
                formId,
                r.getAuxiliaryObjectclasses().stream().map(AuxEntry::from).toList(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
