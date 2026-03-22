package com.ldapadmin.dto.admin;

import com.ldapadmin.entity.AdminFeaturePermission;
import com.ldapadmin.entity.AdminProfileRole;
import com.ldapadmin.entity.enums.FeatureKey;

import java.util.List;

/**
 * Full permission summary for an admin account.
 */
public record AdminPermissionsResponse(
        List<ProfileRoleResponse> profileRoles,
        List<FeatureOverride> featurePermissions) {

    public record FeatureOverride(FeatureKey featureKey, boolean enabled) {
        public static FeatureOverride from(AdminFeaturePermission p) {
            return new FeatureOverride(p.getFeatureKey(), p.isEnabled());
        }
    }

    public static AdminPermissionsResponse from(
            List<AdminProfileRole> roles,
            List<AdminFeaturePermission> features) {

        List<ProfileRoleResponse> roleResponses =
                roles.stream().map(ProfileRoleResponse::from).toList();

        List<FeatureOverride> featureOverrides =
                features.stream().map(FeatureOverride::from).toList();

        return new AdminPermissionsResponse(roleResponses, featureOverrides);
    }
}
