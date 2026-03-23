package com.ldapadmin.auth;

import com.ldapadmin.entity.AdminFeaturePermission;
import com.ldapadmin.entity.AdminProfileRole;
import com.ldapadmin.entity.enums.BaseRole;
import com.ldapadmin.entity.enums.FeatureKey;

import com.ldapadmin.repository.AdminFeaturePermissionRepository;
import com.ldapadmin.repository.AdminProfileRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock private AdminProfileRoleRepository       profileRoleRepo;
    @Mock private AdminFeaturePermissionRepository featurePermissionRepo;
    @SuppressWarnings("unchecked")
    @Mock private ObjectProvider<RequestScopedPermissionCache> cacheProvider;

    private PermissionService permissionService;

    private final UUID adminId   = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final UUID dirId     = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(profileRoleRepo, featurePermissionRepo, cacheProvider);
    }

    // ── Superadmin bypass ─────────────────────────────────────────────────────

    @Test
    void requireProfileAccess_superadmin_returnsNullWithoutHittingRepo() {
        assertThat(permissionService.requireProfileAccess(superadmin(), profileId)).isNull();
        verifyNoInteractions(profileRoleRepo);
    }

    @Test
    void requireDirectoryAccess_superadmin_neverHitsRepo() {
        permissionService.requireDirectoryAccess(superadmin(), dirId);
        verifyNoInteractions(profileRoleRepo);
    }

    @Test
    void requireFeature_superadmin_neverHitsAnyRepo() {
        permissionService.requireFeature(superadmin(), dirId, FeatureKey.USER_CREATE);
        verifyNoInteractions(profileRoleRepo, featurePermissionRepo);
    }

    // ── Dimension 1+2: profile access ─────────────────────────────────────────

    @Test
    void requireProfileAccess_noRoleRow_throwsAccessDenied() {
        when(profileRoleRepo.findByAdminAccountIdAndProfileId(adminId, profileId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.requireProfileAccess(admin(), profileId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireProfileAccess_roleExists_returnsRole() {
        AdminProfileRole role = roleFor(BaseRole.ADMIN);
        when(profileRoleRepo.findByAdminAccountIdAndProfileId(adminId, profileId))
                .thenReturn(Optional.of(role));

        assertThat(permissionService.requireProfileAccess(admin(), profileId)).isSameAs(role);
    }

    @Test
    void requireDirectoryAccess_noRoleInDirectory_throwsAccessDenied() {
        when(profileRoleRepo.existsByAdminAccountIdAndProfileDirectoryId(adminId, dirId))
                .thenReturn(false);

        assertThatThrownBy(() -> permissionService.requireDirectoryAccess(admin(), dirId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireDirectoryAccess_roleExistsInDirectory_succeeds() {
        when(profileRoleRepo.existsByAdminAccountIdAndProfileDirectoryId(adminId, dirId))
                .thenReturn(true);

        permissionService.requireDirectoryAccess(admin(), dirId);
    }

    // ── Dimension 3: feature overrides ─────────────────────────────────────

    @Test
    void requireFeature_adminRole_noOverride_writeFeatureGranted() {
        when(profileRoleRepo.existsByAdminAccountIdAndProfileDirectoryId(adminId, dirId)).thenReturn(true);
        when(featurePermissionRepo.findByAdminAccountIdAndFeatureKey(adminId, FeatureKey.USER_CREATE))
                .thenReturn(Optional.empty());
        when(profileRoleRepo.existsByAdminAccountIdAndBaseRole(adminId, BaseRole.ADMIN))
                .thenReturn(true);

        permissionService.requireFeature(admin(), dirId, FeatureKey.USER_CREATE);
    }

    @Test
    void requireFeature_readOnlyRole_writeFeature_denied() {
        when(profileRoleRepo.existsByAdminAccountIdAndProfileDirectoryId(adminId, dirId)).thenReturn(true);
        when(featurePermissionRepo.findByAdminAccountIdAndFeatureKey(adminId, FeatureKey.USER_DELETE))
                .thenReturn(Optional.empty());
        when(profileRoleRepo.existsByAdminAccountIdAndBaseRole(adminId, BaseRole.ADMIN))
                .thenReturn(false);

        assertThatThrownBy(() -> permissionService.requireFeature(admin(), dirId, FeatureKey.USER_DELETE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireFeature_readOnlyRole_defaultReadFeature_granted() {
        when(profileRoleRepo.existsByAdminAccountIdAndProfileDirectoryId(adminId, dirId)).thenReturn(true);
        when(featurePermissionRepo.findByAdminAccountIdAndFeatureKey(adminId, FeatureKey.BULK_EXPORT))
                .thenReturn(Optional.empty());
        when(profileRoleRepo.existsByAdminAccountIdAndBaseRole(adminId, BaseRole.ADMIN))
                .thenReturn(false);

        permissionService.requireFeature(admin(), dirId, FeatureKey.BULK_EXPORT);
    }

    @Test
    void requireFeature_explicitEnableOverride_grantsAccessEvenForReadOnly() {
        when(profileRoleRepo.existsByAdminAccountIdAndProfileDirectoryId(adminId, dirId)).thenReturn(true);
        when(featurePermissionRepo.findByAdminAccountIdAndFeatureKey(adminId, FeatureKey.USER_CREATE))
                .thenReturn(Optional.of(featureOverride(true)));

        permissionService.requireFeature(admin(), dirId, FeatureKey.USER_CREATE);
    }

    @Test
    void requireFeature_explicitDisableOverride_deniesEvenForAdmin() {
        when(profileRoleRepo.existsByAdminAccountIdAndProfileDirectoryId(adminId, dirId)).thenReturn(true);
        when(featurePermissionRepo.findByAdminAccountIdAndFeatureKey(adminId, FeatureKey.USER_DELETE))
                .thenReturn(Optional.of(featureOverride(false)));

        assertThatThrownBy(() -> permissionService.requireFeature(admin(), dirId, FeatureKey.USER_DELETE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireFeature_noDirectoryRole_throwsAccessDenied() {
        when(profileRoleRepo.existsByAdminAccountIdAndProfileDirectoryId(adminId, dirId))
                .thenReturn(false);

        assertThatThrownBy(() -> permissionService.requireFeature(admin(), dirId, FeatureKey.USER_CREATE))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuthPrincipal admin() {
        return new AuthPrincipal(PrincipalType.ADMIN, adminId, "alice");
    }

    private AuthPrincipal superadmin() {
        return new AuthPrincipal(PrincipalType.SUPERADMIN, UUID.randomUUID(), "root");
    }

    private AdminProfileRole roleFor(BaseRole baseRole) {
        AdminProfileRole r = new AdminProfileRole();
        r.setBaseRole(baseRole);
        return r;
    }

    private AdminFeaturePermission featureOverride(boolean enabled) {
        AdminFeaturePermission fp = new AdminFeaturePermission();
        fp.setEnabled(enabled);
        return fp;
    }
}
