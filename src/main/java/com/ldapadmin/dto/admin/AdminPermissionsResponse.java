package com.ldapadmin.dto.admin;

import com.ldapadmin.entity.AdminBranchRestriction;
import com.ldapadmin.entity.AdminDirectoryRole;
import com.ldapadmin.entity.AdminFeaturePermission;
import com.ldapadmin.entity.enums.FeatureKey;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Full permission summary for an admin account across all four dimensions.
 */
public record AdminPermissionsResponse(
        List<DirectoryRoleResponse> directoryRoles,
        Map<UUID, List<String>> branchRestrictions,
        List<FeatureOverride> featurePermissions) {

    public record FeatureOverride(FeatureKey featureKey, boolean enabled) {
        public static FeatureOverride from(AdminFeaturePermission p) {
            return new FeatureOverride(p.getFeatureKey(), p.isEnabled());
        }
    }

    public static AdminPermissionsResponse from(
            List<AdminDirectoryRole> roles,
            List<AdminBranchRestriction> branches,
            List<AdminFeaturePermission> features) {

        List<DirectoryRoleResponse> roleResponses =
                roles.stream().map(DirectoryRoleResponse::from).toList();

        Map<UUID, List<String>> branchMap = branches.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getDirectory().getId(),
                        Collectors.mapping(AdminBranchRestriction::getBranchDn, Collectors.toList())));

        List<FeatureOverride> featureOverrides =
                features.stream().map(FeatureOverride::from).toList();

        return new AdminPermissionsResponse(roleResponses, branchMap, featureOverrides);
    }
}
