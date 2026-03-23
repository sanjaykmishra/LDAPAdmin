package com.ldapadmin.auth;

import com.ldapadmin.entity.AdminProfileRole;
import com.ldapadmin.entity.enums.BaseRole;
import com.ldapadmin.entity.enums.FeatureKey;

import com.ldapadmin.repository.AdminFeaturePermissionRepository;
import com.ldapadmin.repository.AdminProfileRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Locale;
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
            FeatureKey.REPORTS_RUN,
            FeatureKey.DIRECTORY_BROWSE,
            FeatureKey.SCHEMA_READ,
            FeatureKey.USER_READ,
            FeatureKey.GROUP_READ,
            FeatureKey.APPROVAL_MANAGE
    );

    private final AdminProfileRoleRepository        profileRoleRepo;
    private final AdminFeaturePermissionRepository   featurePermissionRepo;
    private final ObjectProvider<RequestScopedPermissionCache> cacheProvider;

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

        // Dim 4: explicit override takes priority (cached per request)
        var cache = cacheProvider.getIfAvailable();
        var override = cache != null
                ? cache.getFeatureOverride(principal.id(), feature, featurePermissionRepo)
                : featurePermissionRepo.findByAdminAccountIdAndFeatureKey(principal.id(), feature);

        if (override.isPresent()) {
            if (!override.get().isEnabled()) {
                throw new AccessDeniedException(
                        "Feature [" + feature.getDbValue() + "] is disabled for this admin");
            }
            return; // explicitly enabled
        }

        // Fall back to base-role defaults (dim 2) — use the most permissive role across profiles
        boolean hasAdminRole = profileRoleRepo
                .existsByAdminAccountIdAndBaseRole(principal.id(), BaseRole.ADMIN);

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
        return profileRoleRepo.findDistinctDirectoryIdsByAdminAccountId(principal.id());
    }

    // ── DN-level scoping (Wave 4.7 Option A) ───────────────────────────────

    /**
     * Returns the set of OU DNs the admin is authorized to operate in for
     * the given directory (derived from profile {@code targetOuDn} fields).
     * Returns empty set for superadmins (meaning unrestricted).
     */
    public Set<String> getAuthorizedOuDns(AuthPrincipal principal, UUID directoryId) {
        if (principal.isSuperadmin()) return Set.of();
        return profileRoleRepo
                .findAllByAdminAccountIdAndProfileDirectoryId(principal.id(), directoryId)
                .stream()
                .map(r -> r.getProfile().getTargetOuDn())
                .collect(Collectors.toSet());
    }

    /**
     * Verifies that the given DN falls within one of the admin's authorized OUs
     * in the specified directory. A DN is in-scope if it is equal to or a
     * descendant of any authorized OU.
     *
     * @throws AccessDeniedException if the DN is outside all authorized OUs
     */
    public void requireDnWithinScope(AuthPrincipal principal, UUID directoryId, String dn) {
        if (principal.isSuperadmin()) return;
        if (dn == null || dn.isBlank()) return; // null DN handled by caller

        Set<String> allowedOus = getAuthorizedOuDns(principal, directoryId);
        if (allowedOus.isEmpty()) {
            throw new AccessDeniedException("No profile access in directory [" + directoryId + "]");
        }

        String normalizedDn = dn.toLowerCase(Locale.ROOT).trim();
        boolean inScope = allowedOus.stream().anyMatch(ou -> {
            String normalizedOu = ou.toLowerCase(Locale.ROOT).trim();
            return normalizedDn.equals(normalizedOu)
                    || normalizedDn.endsWith("," + normalizedOu);
        });

        if (!inScope) {
            throw new AccessDeniedException("DN is outside authorized OUs for this admin");
        }
    }

    /**
     * Validates that a search baseDn falls within the admin's authorized OUs.
     * If baseDn is null, returns {@code null} (the caller should use the
     * directory baseDn, which will be validated by the service layer).
     *
     * @throws AccessDeniedException if baseDn is outside all authorized OUs
     */
    public void requireBaseDnWithinScope(AuthPrincipal principal, UUID directoryId, String baseDn) {
        if (principal.isSuperadmin()) return;
        if (baseDn == null || baseDn.isBlank()) return; // null baseDn = use directory default

        Set<String> allowedOus = getAuthorizedOuDns(principal, directoryId);
        if (allowedOus.isEmpty()) {
            throw new AccessDeniedException("No profile access in directory [" + directoryId + "]");
        }

        String normalizedBase = baseDn.toLowerCase(Locale.ROOT).trim();
        // baseDn is valid if it equals or is a descendant of an allowed OU,
        // OR if an allowed OU is a descendant of baseDn (broader search that
        // will return results within the allowed scope)
        boolean inScope = allowedOus.stream().anyMatch(ou -> {
            String normalizedOu = ou.toLowerCase(Locale.ROOT).trim();
            return normalizedBase.equals(normalizedOu)
                    || normalizedBase.endsWith("," + normalizedOu)
                    || normalizedOu.endsWith("," + normalizedBase);
        });

        if (!inScope) {
            throw new AccessDeniedException("Search baseDn is outside authorized OUs for this admin");
        }
    }

}
