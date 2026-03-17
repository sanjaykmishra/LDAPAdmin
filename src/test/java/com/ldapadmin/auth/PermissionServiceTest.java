package com.ldapadmin.auth;

import com.ldapadmin.entity.AdminFeaturePermission;
import com.ldapadmin.entity.AdminRealmRole;
import com.ldapadmin.entity.enums.BaseRole;
import com.ldapadmin.entity.enums.FeatureKey;

import com.ldapadmin.repository.AdminFeaturePermissionRepository;
import com.ldapadmin.repository.AdminRealmRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock private AdminRealmRoleRepository         realmRoleRepo;
    @Mock private AdminFeaturePermissionRepository featurePermissionRepo;

    private PermissionService permissionService;

    private final UUID adminId = UUID.randomUUID();
    private final UUID realmId = UUID.randomUUID();
    private final UUID dirId   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(realmRoleRepo, featurePermissionRepo);
    }

    // ── Superadmin bypass ─────────────────────────────────────────────────────

    @Test
    void requireRealmAccess_superadmin_returnsNullWithoutHittingRepo() {
        assertThat(permissionService.requireRealmAccess(superadmin(), realmId)).isNull();
        verifyNoInteractions(realmRoleRepo);
    }

    @Test
    void requireDirectoryAccess_superadmin_neverHitsRepo() {
        permissionService.requireDirectoryAccess(superadmin(), dirId);
        verifyNoInteractions(realmRoleRepo);
    }

    @Test
    void requireFeature_superadmin_neverHitsAnyRepo() {
        permissionService.requireFeature(superadmin(), dirId, FeatureKey.USER_CREATE);
        verifyNoInteractions(realmRoleRepo, featurePermissionRepo);
    }

    // ── Dimension 1+2: realm access ───────────────────────────────────────────

    @Test
    void requireRealmAccess_noRoleRow_throwsAccessDenied() {
        when(realmRoleRepo.findByAdminAccountIdAndRealmId(adminId, realmId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.requireRealmAccess(admin(), realmId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireRealmAccess_roleExists_returnsRole() {
        AdminRealmRole role = roleFor(BaseRole.ADMIN);
        when(realmRoleRepo.findByAdminAccountIdAndRealmId(adminId, realmId))
                .thenReturn(Optional.of(role));

        assertThat(permissionService.requireRealmAccess(admin(), realmId)).isSameAs(role);
    }

    @Test
    void requireDirectoryAccess_noRoleInDirectory_throwsAccessDenied() {
        when(realmRoleRepo.existsByAdminAccountIdAndRealmDirectoryId(adminId, dirId))
                .thenReturn(false);

        assertThatThrownBy(() -> permissionService.requireDirectoryAccess(admin(), dirId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireDirectoryAccess_roleExistsInDirectory_succeeds() {
        when(realmRoleRepo.existsByAdminAccountIdAndRealmDirectoryId(adminId, dirId))
                .thenReturn(true);

        // must not throw
        permissionService.requireDirectoryAccess(admin(), dirId);
    }

    // ── Dimension 3: feature overrides ─────────────────────────────────────

    @Test
    void requireFeature_adminRole_noOverride_writeFeatureGranted() {
        when(realmRoleRepo.existsByAdminAccountIdAndRealmDirectoryId(adminId, dirId)).thenReturn(true);
        when(featurePermissionRepo.findByAdminAccountIdAndFeatureKey(adminId, FeatureKey.USER_CREATE))
                .thenReturn(Optional.empty());
        when(realmRoleRepo.findAllByAdminAccountId(adminId))
                .thenReturn(List.of(roleFor(BaseRole.ADMIN)));

        permissionService.requireFeature(admin(), dirId, FeatureKey.USER_CREATE);
    }

    @Test
    void requireFeature_readOnlyRole_writeFeature_denied() {
        when(realmRoleRepo.existsByAdminAccountIdAndRealmDirectoryId(adminId, dirId)).thenReturn(true);
        when(featurePermissionRepo.findByAdminAccountIdAndFeatureKey(adminId, FeatureKey.USER_DELETE))
                .thenReturn(Optional.empty());
        when(realmRoleRepo.findAllByAdminAccountId(adminId))
                .thenReturn(List.of(roleFor(BaseRole.READ_ONLY)));

        assertThatThrownBy(() -> permissionService.requireFeature(admin(), dirId, FeatureKey.USER_DELETE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireFeature_readOnlyRole_defaultReadFeature_granted() {
        when(realmRoleRepo.existsByAdminAccountIdAndRealmDirectoryId(adminId, dirId)).thenReturn(true);
        when(featurePermissionRepo.findByAdminAccountIdAndFeatureKey(adminId, FeatureKey.BULK_EXPORT))
                .thenReturn(Optional.empty());
        when(realmRoleRepo.findAllByAdminAccountId(adminId))
                .thenReturn(List.of(roleFor(BaseRole.READ_ONLY)));

        // BULK_EXPORT is in READONLY_DEFAULT_FEATURES — must not throw
        permissionService.requireFeature(admin(), dirId, FeatureKey.BULK_EXPORT);
    }

    @Test
    void requireFeature_explicitEnableOverride_grantsAccessEvenForReadOnly() {
        when(realmRoleRepo.existsByAdminAccountIdAndRealmDirectoryId(adminId, dirId)).thenReturn(true);
        when(featurePermissionRepo.findByAdminAccountIdAndFeatureKey(adminId, FeatureKey.USER_CREATE))
                .thenReturn(Optional.of(featureOverride(true)));

        permissionService.requireFeature(admin(), dirId, FeatureKey.USER_CREATE);
    }

    @Test
    void requireFeature_explicitDisableOverride_deniesEvenForAdmin() {
        when(realmRoleRepo.existsByAdminAccountIdAndRealmDirectoryId(adminId, dirId)).thenReturn(true);
        when(featurePermissionRepo.findByAdminAccountIdAndFeatureKey(adminId, FeatureKey.USER_DELETE))
                .thenReturn(Optional.of(featureOverride(false)));

        assertThatThrownBy(() -> permissionService.requireFeature(admin(), dirId, FeatureKey.USER_DELETE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireFeature_noDirectoryRole_throwsAccessDenied() {
        when(realmRoleRepo.existsByAdminAccountIdAndRealmDirectoryId(adminId, dirId))
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

    private AdminRealmRole roleFor(BaseRole baseRole) {
        AdminRealmRole r = new AdminRealmRole();
        r.setBaseRole(baseRole);
        return r;
    }

    private AdminFeaturePermission featureOverride(boolean enabled) {
        AdminFeaturePermission fp = new AdminFeaturePermission();
        fp.setEnabled(enabled);
        return fp;
    }
}
