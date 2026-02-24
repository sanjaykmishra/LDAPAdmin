package com.ldapadmin.dto.admin;

import com.ldapadmin.entity.AdminDirectoryRole;
import com.ldapadmin.entity.enums.BaseRole;

import java.util.UUID;

public record DirectoryRoleResponse(
        UUID id,
        UUID directoryId,
        String directoryDisplayName,
        BaseRole baseRole) {

    public static DirectoryRoleResponse from(AdminDirectoryRole r) {
        return new DirectoryRoleResponse(
                r.getId(),
                r.getDirectory().getId(),
                r.getDirectory().getDisplayName(),
                r.getBaseRole());
    }
}
