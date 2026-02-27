package com.ldapadmin.auth;

import com.ldapadmin.entity.AdminBranchRestriction;
import com.ldapadmin.entity.AdminFeaturePermission;
import com.ldapadmin.entity.AdminRealmRole;
import com.ldapadmin.entity.enums.BaseRole;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.repository.AdminBranchRestrictionRepository;
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
    @Mock private AdminBranchRestrictionRepository branchRepo;
    @Mock private AdminFeaturePermissionRepository featurePermissionRepo;

    private PermissionService permissionService;

    private final UUID adminId = UUID.randomUUID();
    private final UUID realmId = UUID.randomUUID();
    private final UUID dirId   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(realmRoleRepo, branchRepo, featurePermissionRepo);
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
    void requireBranchAccess_superadmin_neverHitsRepo() {
        permissionService.requireBranchAccess(superadmin(), realmId, "cn=X,dc=example,dc=com");
        verifyNoInteractions(branchRepo);
    }

    @Test
    void requireFeature_superadmin_neverHitsAnyRepo() {
        permissionService.requireFeature(superadmin(), dirId, FeatureKey.USER_CREATE);
        verifyNoInteractions(realmRoleRepo, branchRepo, featurePermissionRepo);
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

    // ── Dimension 3: branch restrictions ─────────────────────────────────────

    @Test
    void requireBranchAccess_noRestrictions_unrestricted() {
        when(branchRepo.findAllByAdminAccountIdAndRealmId(adminId, realmId))
                .thenReturn(List.of());

        // any DN is allowed when there are no restrictions
        permissionService.requireBranchAccess(admin(), realmId, "cn=Restricted,ou=System,dc=corp,dc=com");
    }

    @Test
    void requireBranchAccess_entryUnderAllowedBranch_allowed() {
        when(branchRepo.findAllByAdminAccountIdAndRealmId(adminId, realmId))
                .thenReturn(List.of(branch("ou=Users,dc=example,dc=com")));

        permissionService.requireBranchAccess(admin(), realmId, "cn=Alice,ou=Users,dc=example,dc=com");
    }

    @Test
    void requireBranchAccess_entryExactlyBranchDn_allowed() {
        String branchDn = "ou=Users,dc=example,dc=com";
        when(branchRepo.findAllByAdminAccountIdAndRealmId(adminId, realmId))
                .thenReturn(List.of(branch(branchDn)));

        permissionService.requireBranchAccess(admin(), realmId, branchDn);
    }

    @Test
    void requireBranchAccess_entryOutsideAllBranches_throwsAccessDenied() {
        when(branchRepo.findAllByAdminAccountIdAndRealmId(adminId, realmId))
                .thenReturn(List.of(branch("ou=Users,dc=example,dc=com")));

        assertThatThrownBy(() -> permissionService.requireBranchAccess(
                admin(), realmId, "cn=Admin,ou=System,dc=example,dc=com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireBranchAccess_caseInsensitiveMatch() {
        when(branchRepo.findAllByAdminAccountIdAndRealmId(adminId, realmId))
                .thenReturn(List.of(branch("OU=Users,DC=EXAMPLE,DC=COM")));

        // lower-case entry should still match
        permissionService.requireBranchAccess(admin(), realmId, "cn=alice,ou=users,dc=example,dc=com");
    }

    @Test
    void requireBranchAccess_partialSuffixNotMatched() {
        // "ou=usersextra" must NOT match "ou=users" branch
        when(branchRepo.findAllByAdminAccountIdAndRealmId(adminId, realmId))
                .thenReturn(List.of(branch("ou=users,dc=com")));

        assertThatThrownBy(() -> permissionService.requireBranchAccess(
                admin(), realmId, "cn=alice,ou=usersextra,dc=com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireBranchAccess_allowedByOneOfMultipleBranches() {
        when(branchRepo.findAllByAdminAccountIdAndRealmId(adminId, realmId))
                .thenReturn(List.of(
                        branch("ou=Users,dc=example,dc=com"),
                        branch("ou=Groups,dc=example,dc=com")));

        // entry falls under the second branch
        permissionService.requireBranchAccess(admin(), realmId,
                "cn=Staff,ou=Groups,dc=example,dc=com");
    }

    // ── Dimension 4: feature overrides ───────────────────────────────────────

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

    private AdminBranchRestriction branch(String branchDn) {
        AdminBranchRestriction r = new AdminBranchRestriction();
        r.setBranchDn(branchDn);
        return r;
    }

    private AdminFeaturePermission featureOverride(boolean enabled) {
        AdminFeaturePermission fp = new AdminFeaturePermission();
        fp.setEnabled(enabled);
        return fp;
    }
}
