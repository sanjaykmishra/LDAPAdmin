package com.ldapadmin.dto.admin;

import com.ldapadmin.entity.AdminRealmRole;
import com.ldapadmin.entity.enums.BaseRole;

import java.util.UUID;

public record RealmRoleResponse(
        UUID id,
        UUID realmId,
        String realmName,
        BaseRole baseRole) {

    public static RealmRoleResponse from(AdminRealmRole r) {
        return new RealmRoleResponse(
                r.getId(),
                r.getRealm().getId(),
                r.getRealm().getName(),
                r.getBaseRole());
    }
}
