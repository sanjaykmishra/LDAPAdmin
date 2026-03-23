package com.ldapadmin.auth;

import com.ldapadmin.entity.AdminProfileRole;
import com.ldapadmin.entity.enums.BaseRole;
import com.ldapadmin.entity.enums.FeatureKey;

import com.ldapadmin.repository.AdminFeaturePermissionRepository;
import com.ldapadmin.repository.AdminProfileRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enforces the permission model for admins.
 *
 * <h3>Dimensions</h3>
 * <ol>
 *   <li><b>Profile access</b> — admin must have a row in
 *       {@code admin_profile_roles} for the target profile.</li>
 *   <li><b>Base role</b> — {@code ADMIN} grants all default capabilities;
 *       {@code READ_ONLY} grants only the read/export subset.</li>
 *   <li><b>Feature override</b> — a row in {@code admin_feature_permissions}
 *       explicitly enables or disables a feature, overriding the base-role
 *       default.</li>
 * </ol>
 *
 * <p>Superadmins bypass all checks and are always granted access.</p>
 *
 * <h3>Directory vs profile</h3>
 * <p>A directory can host multiple profiles. The controller layer passes a
 * {@code directoryId} (the LDAP connection identifier). Directory-level
 * access is granted when the admin has a role in <em>any</em> profile that
 * belongs to that directory.</p>
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    /**
     * Features available to {@link BaseRole#READ_ONLY} admins by default.
     * Everything else requires {@link BaseRole#ADMIN}.
     */
    private static final Set<FeatureKey> READONLY_DEFAULT_FEATURES = Set.of(
            FeatureKey.BULK_EXPORT,
            FeatureKey.REPORTS_RUN
    );

    private final AdminProfileRoleRepository        profileRoleRepo;
    private final AdminFeaturePermissionRepository   featurePermissionRepo;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Verifies that {@code principal} has access to {@code profileId}
     * (dimensions 1 + 2) and returns the resolved role.
     *
     * @throws AccessDeniedException if no role is assigned
     */
    public AdminProfileRole requireProfileAccess(AuthPrincipal principal, UUID profileId) {
        if (principal.isSuperadmin()) {
            return null; // superadmin — no role row required
        }
        return profileRoleRepo
                .findByAdminAccountIdAndProfileId(principal.id(), profileId)
                .orElseThrow(() -> new AccessDeniedException(
                        "No access to profile [" + profileId + "]"));
    }

    /**
     * Verifies that {@code principal} has access to at least one profile in
     * the given directory (dimensions 1 + 2).
     *
     * @throws AccessDeniedException if the admin has no profile roles for this directory
     */
    public void requireDirectoryAccess(AuthPrincipal principal, UUID directoryId) {
        if (principal.isSuperadmin()) return;

        boolean hasAccess = profileRoleRepo
                .existsByAdminAccountIdAndProfileDirectoryId(principal.id(), directoryId);
        if (!hasAccess) {
            throw new AccessDeniedException("No access to directory [" + directoryId + "]");
        }
    }

    /**
     * Checks directory access (dim 1+2) and feature permission (dim 3) in one call.
     * Called from {@link FeaturePermissionAspect} with the {@code directoryId}
     * extracted from the controller method parameter.
     *
     * @throws AccessDeniedException if any dimension denies access
     */
    public void requireFeature(AuthPrincipal principal, UUID directoryId, FeatureKey feature) {
        if (principal.isSuperadmin()) return;

        requireDirectoryAccess(principal, directoryId);

        // Dim 4: explicit override takes priority
        var override = featurePermissionRepo
                .findByAdminAccountIdAndFeatureKey(principal.id(), feature);

        if (override.isPresent()) {
            if (!override.get().isEnabled()) {
                throw new AccessDeniedException(
                        "Feature [" + feature.getDbValue() + "] is disabled for this admin");
            }
            return; // explicitly enabled
        }

        // Fall back to base-role defaults (dim 2) — use the most permissive role across profiles
        List<AdminProfileRole> roles = profileRoleRepo.findAllByAdminAccountId(principal.id());
        boolean hasAdminRole = roles.stream()
                .anyMatch(r -> r.getBaseRole() == BaseRole.ADMIN);

        if (!hasAdminRole && !READONLY_DEFAULT_FEATURES.contains(feature)) {
            throw new AccessDeniedException(
                    "READ_ONLY role does not grant feature [" + feature.getDbValue() + "]");
        }
    }

    /**
     * Returns the set of directory IDs the admin has access to (via profile roles).
     * Returns empty set for superadmins (meaning unrestricted).
     */
    public Set<UUID> getAuthorizedDirectoryIds(AuthPrincipal principal) {
        if (principal.isSuperadmin()) {
            return Set.of();
        }
        return profileRoleRepo.findAllByAdminAccountId(principal.id()).stream()
                .map(r -> r.getProfile().getDirectory().getId())
                .collect(Collectors.toSet());
    }

}
