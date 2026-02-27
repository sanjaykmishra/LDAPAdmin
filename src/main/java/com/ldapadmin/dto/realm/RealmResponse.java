package com.ldapadmin.dto.realm;

import com.ldapadmin.entity.Realm;
import com.ldapadmin.entity.RealmAuxiliaryObjectclass;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Read DTO for a realm. */
public record RealmResponse(
        UUID id,
        UUID directoryId,
        String name,
        String userBaseDn,
        String groupBaseDn,
        String primaryUserObjectclass,
        int displayOrder,
        List<AuxEntry> auxiliaryObjectclasses,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public record AuxEntry(UUID id, String objectclassName, int displayOrder) {
        public static AuxEntry from(RealmAuxiliaryObjectclass aux) {
            return new AuxEntry(aux.getId(), aux.getObjectclassName(), aux.getDisplayOrder());
        }
    }

    public static RealmResponse from(Realm r) {
        return new RealmResponse(
                r.getId(),
                r.getDirectory().getId(),
                r.getName(),
                r.getUserBaseDn(),
                r.getGroupBaseDn(),
                r.getPrimaryUserObjectclass(),
                r.getDisplayOrder(),
                r.getAuxiliaryObjectclasses().stream().map(AuxEntry::from).toList(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
