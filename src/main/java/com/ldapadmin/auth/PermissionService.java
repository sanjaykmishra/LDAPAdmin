package com.ldapadmin.auth;

import com.ldapadmin.entity.AdminBranchRestriction;
import com.ldapadmin.entity.AdminRealmRole;
import com.ldapadmin.entity.enums.BaseRole;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.repository.AdminBranchRestrictionRepository;
import com.ldapadmin.repository.AdminFeaturePermissionRepository;
import com.ldapadmin.repository.AdminRealmRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Enforces the four-dimensional permission model (§3.2) for admins.
 *
 * <h3>Dimensions</h3>
 * <ol>
 *   <li><b>Realm access</b> — admin must have a row in
 *       {@code admin_realm_roles} for the target realm.</li>
 *   <li><b>Base role</b> — {@code ADMIN} grants all default capabilities;
 *       {@code READ_ONLY} grants only the read/export subset.</li>
 *   <li><b>Branch restriction</b> — if any rows exist in
 *       {@code admin_branch_restrictions} for (admin, realm), the target
 *       entry's DN must be a descendant of one of those branch DNs.</li>
 *   <li><b>Feature override</b> — a row in {@code admin_feature_permissions}
 *       explicitly enables or disables a feature, overriding the base-role
 *       default.</li>
 * </ol>
 *
 * <p>Superadmins bypass all checks and are always granted access.</p>
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
            FeatureKey.REPORTS_RUN,
            FeatureKey.REPORTS_EXPORT
    );

    private final AdminRealmRoleRepository         realmRoleRepo;
    private final AdminBranchRestrictionRepository branchRepo;
    private final AdminFeaturePermissionRepository featurePermissionRepo;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Verifies that {@code principal} has access to {@code realmId}
     * (dimensions 1 + 2) and returns the resolved role.
     *
     * @throws AccessDeniedException if no role is assigned
     */
    public AdminRealmRole requireRealmAccess(AuthPrincipal principal, UUID realmId) {
        if (principal.isSuperadmin()) {
            return null; // superadmin — no role row required
        }
        return realmRoleRepo
                .findByAdminAccountIdAndRealmId(principal.id(), realmId)
                .orElseThrow(() -> new AccessDeniedException(
                        "No access to realm [" + realmId + "]"));
    }

    /**
     * Verifies that the given {@code entryDn} falls within one of the admin's
     * allowed branches for the realm (dimension 3).
     *
     * <p>If no branch restrictions are configured for the (admin, realm) pair
     * the admin has unrestricted branch access.</p>
     *
     * @param entryDn DN of the LDAP entry being accessed
     * @throws AccessDeniedException if the entry is outside all allowed branches
     */
    public void requireBranchAccess(AuthPrincipal principal, UUID realmId, String entryDn) {
        if (principal.isSuperadmin()) return;

        List<AdminBranchRestriction> restrictions =
                branchRepo.findAllByAdminAccountIdAndRealmId(principal.id(), realmId);

        if (restrictions.isEmpty()) return; // unrestricted

        boolean allowed = restrictions.stream()
                .anyMatch(r -> isDnUnderBranch(entryDn, r.getBranchDn()));

        if (!allowed) {
            throw new AccessDeniedException(
                    "Entry [" + entryDn + "] is not within any allowed branch for this admin");
        }
    }

    /**
     * Checks realm access (dim 1+2) and feature permission (dim 4) in one call.
     *
     * <p>Branch access (dim 3) is checked separately via
     * {@link #requireBranchAccess} because the entry DN is only known at
     * operation time, not at the controller layer.</p>
     *
     * @throws AccessDeniedException if any dimension denies access
     */
    public void requireFeature(AuthPrincipal principal, UUID realmId, FeatureKey feature) {
        if (principal.isSuperadmin()) return;

        AdminRealmRole role = requireRealmAccess(principal, realmId);

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

        // Fall back to base-role defaults (dim 2)
        if (role.getBaseRole() == BaseRole.READ_ONLY
                && !READONLY_DEFAULT_FEATURES.contains(feature)) {
            throw new AccessDeniedException(
                    "READ_ONLY role does not grant feature [" + feature.getDbValue() + "]");
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code entryDn} is at or below {@code branchDn}.
     *
     * <p>LDAP DN matching is case-insensitive.  An entry DN is "under" a branch
     * when the branch DN is a suffix of the entry DN preceded by a comma, or
     * when they are equal (the branch DN itself is the entry).</p>
     */
    private boolean isDnUnderBranch(String entryDn, String branchDn) {
        String entry  = entryDn.toLowerCase(Locale.ROOT);
        String branch = branchDn.toLowerCase(Locale.ROOT);
        return entry.equals(branch) || entry.endsWith("," + branch);
    }
}
