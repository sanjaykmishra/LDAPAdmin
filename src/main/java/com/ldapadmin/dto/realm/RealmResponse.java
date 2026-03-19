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
        List<UserTemplateEntry> userTemplates,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public record UserTemplateEntry(UUID id, String templateName, List<String> objectClassNames) {}

    public static RealmResponse from(Realm r, List<RealmObjectclass> realmOcs) {
        List<UserTemplateEntry> templates = realmOcs.stream()
                .filter(oc -> oc.getUserTemplate() != null)
                .map(oc -> new UserTemplateEntry(
                        oc.getUserTemplate().getId(),
                        oc.getUserTemplate().getTemplateName(),
                        List.copyOf(oc.getUserTemplate().getObjectClassNames())))
                .toList();

        return new RealmResponse(
                r.getId(),
                r.getDirectory().getId(),
                r.getDirectory().getDisplayName(),
                r.getName(),
                r.getUserBaseDn(),
                r.getGroupBaseDn(),
                templates,
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
