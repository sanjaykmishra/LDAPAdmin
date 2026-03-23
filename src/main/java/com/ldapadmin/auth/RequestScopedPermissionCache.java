package com.ldapadmin.auth;

import com.ldapadmin.entity.AdminFeaturePermission;
import com.ldapadmin.entity.AdminProfileRole;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.repository.AdminFeaturePermissionRepository;
import com.ldapadmin.repository.AdminProfileRoleRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-HTTP-request cache for permission data. Avoids repeated DB queries when
 * multiple permission checks occur in a single request (e.g. feature check +
 * DN scope check both need the admin's profile roles).
 */
@Component
@RequestScope
public class RequestScopedPermissionCache {

    private List<AdminProfileRole> roles;
    private UUID cachedAdminId;
    private final Map<FeatureKey, Optional<AdminFeaturePermission>> featureOverrides = new HashMap<>();

    public List<AdminProfileRole> getRoles(UUID adminId, AdminProfileRoleRepository repo) {
        if (roles == null || !adminId.equals(cachedAdminId)) {
            roles = repo.findAllByAdminAccountId(adminId);
            cachedAdminId = adminId;
        }
        return roles;
    }

    public Optional<AdminFeaturePermission> getFeatureOverride(
            UUID adminId, FeatureKey key, AdminFeaturePermissionRepository repo) {
        return featureOverrides.computeIfAbsent(key,
                k -> repo.findByAdminAccountIdAndFeatureKey(adminId, k));
    }
}
