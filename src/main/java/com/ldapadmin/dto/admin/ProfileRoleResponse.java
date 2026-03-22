package com.ldapadmin.dto.admin;

import com.ldapadmin.entity.AdminProfileRole;
import com.ldapadmin.entity.enums.BaseRole;

import java.util.UUID;

public record ProfileRoleResponse(
        UUID id,
        UUID profileId,
        String profileName,
        UUID directoryId,
        BaseRole baseRole) {

    public static ProfileRoleResponse from(AdminProfileRole r) {
        return new ProfileRoleResponse(
                r.getId(),
                r.getProfile().getId(),
                r.getProfile().getName(),
                r.getProfile().getDirectory().getId(),
                r.getBaseRole());
    }
}
