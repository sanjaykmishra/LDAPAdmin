package com.ldapadmin.service;

import com.ldapadmin.dto.admin.AdminAccountRequest;
import com.ldapadmin.dto.admin.AdminAccountResponse;
import com.ldapadmin.dto.admin.BranchRestrictionsRequest;
import com.ldapadmin.dto.admin.DirectoryRoleRequest;
import com.ldapadmin.dto.admin.DirectoryRoleResponse;
import com.ldapadmin.dto.admin.FeaturePermissionRequest;
import com.ldapadmin.entity.AdminAccount;
import com.ldapadmin.entity.AdminDirectoryRole;
import com.ldapadmin.entity.AdminFeaturePermission;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.Tenant;
import com.ldapadmin.entity.enums.BaseRole;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AdminAccountRepository;
import com.ldapadmin.repository.AdminBranchRestrictionRepository;
import com.ldapadmin.repository.AdminDirectoryRoleRepository;
import com.ldapadmin.repository.AdminFeaturePermissionRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminManagementServiceTest {

    @Mock private AdminAccountRepository           adminRepo;
    @Mock private TenantRepository                 tenantRepo;
    @Mock private DirectoryConnectionRepository    dirRepo;
    @Mock private AdminDirectoryRoleRepository     roleRepo;
    @Mock private AdminBranchRestrictionRepository branchRepo;
    @Mock private AdminFeaturePermissionRepository featureRepo;

    private AdminManagementService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID adminId  = UUID.randomUUID();
    private final UUID dirId    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdminManagementService(
                adminRepo, tenantRepo, dirRepo, roleRepo, branchRepo, featureRepo);
    }

    // ── listAdmins ────────────────────────────────────────────────────────────

    @Test
    void listAdmins_tenantNotFound_throwsResourceNotFound() {
        when(tenantRepo.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listAdmins(tenantId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listAdmins_returnsMappedList() {
        when(tenantRepo.findById(tenantId)).thenReturn(Optional.of(tenant()));
        AdminAccount a = adminAccount("alice");
        when(adminRepo.findAllByTenantId(eq(tenantId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a)));

        List<AdminAccountResponse> result = service.listAdmins(tenantId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).username()).isEqualTo("alice");
    }

    // ── createAdmin ───────────────────────────────────────────────────────────

    @Test
    void createAdmin_tenantNotFound_throwsResourceNotFound() {
        when(tenantRepo.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAdmin(tenantId,
                new AdminAccountRequest("bob", "Bob", "bob@e.com", true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createAdmin_duplicateUsername_throwsConflict() {
        when(tenantRepo.findById(tenantId)).thenReturn(Optional.of(tenant()));
        when(adminRepo.existsByTenantIdAndUsername(tenantId, "bob")).thenReturn(true);

        assertThatThrownBy(() -> service.createAdmin(tenantId,
                new AdminAccountRequest("bob", "Bob", "bob@e.com", true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createAdmin_success_savesAndReturns() {
        Tenant t = tenant();
        when(tenantRepo.findById(tenantId)).thenReturn(Optional.of(t));
        when(adminRepo.existsByTenantIdAndUsername(tenantId, "alice")).thenReturn(false);

        AdminAccount saved = adminAccount("alice");
        when(adminRepo.save(any())).thenReturn(saved);

        AdminAccountResponse resp = service.createAdmin(tenantId,
                new AdminAccountRequest("alice", "Alice", "a@e.com", true));

        assertThat(resp.username()).isEqualTo("alice");
        ArgumentCaptor<AdminAccount> captor = ArgumentCaptor.forClass(AdminAccount.class);
        verify(adminRepo).save(captor.capture());
        assertThat(captor.getValue().getTenant()).isSameAs(t);
    }

    // ── updateAdmin ───────────────────────────────────────────────────────────

    @Test
    void updateAdmin_adminNotFound_throwsResourceNotFound() {
        when(adminRepo.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAdmin(tenantId, adminId,
                new AdminAccountRequest("alice", "Alice", "a@e.com", true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateAdmin_usernameConflictWithDifferentAdmin_throwsConflict() {
        AdminAccount existing = adminAccount("alice");
        when(adminRepo.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(existing));
        when(adminRepo.existsByTenantIdAndUsername(tenantId, "bob")).thenReturn(true);

        assertThatThrownBy(() -> service.updateAdmin(tenantId, adminId,
                new AdminAccountRequest("bob", "Bob", "b@e.com", true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateAdmin_sameUsername_noConflictCheck() {
        AdminAccount existing = adminAccount("alice");
        when(adminRepo.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(existing));
        when(adminRepo.save(any())).thenReturn(existing);

        // same username → no existence check needed
        service.updateAdmin(tenantId, adminId,
                new AdminAccountRequest("alice", "Alice Renamed", "a@e.com", true));

        verify(adminRepo, never()).existsByTenantIdAndUsername(any(), eq("alice"));
    }

    // ── deleteAdmin ───────────────────────────────────────────────────────────

    @Test
    void deleteAdmin_success_callsDelete() {
        AdminAccount a = adminAccount("alice");
        when(adminRepo.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(a));

        service.deleteAdmin(tenantId, adminId);

        verify(adminRepo).delete(a);
    }

    // ── assignDirectoryRole ───────────────────────────────────────────────────

    @Test
    void assignDirectoryRole_directoryNotFound_throwsResourceNotFound() {
        when(adminRepo.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(adminAccount("alice")));
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignDirectoryRole(tenantId, adminId,
                new DirectoryRoleRequest(dirId, BaseRole.ADMIN)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void assignDirectoryRole_newRole_savesRole() {
        when(adminRepo.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(adminAccount("alice")));
        when(adminRepo.getReferenceById(adminId)).thenReturn(adminAccount("alice"));
        DirectoryConnection dir = directory();
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));
        when(roleRepo.findByAdminAccountIdAndDirectoryId(adminId, dirId)).thenReturn(Optional.empty());

        AdminDirectoryRole saved = new AdminDirectoryRole();
        saved.setBaseRole(BaseRole.ADMIN);
        saved.setDirectory(dir);       // needed by DirectoryRoleResponse.from()
        when(roleRepo.save(any())).thenReturn(saved);

        DirectoryRoleResponse resp = service.assignDirectoryRole(tenantId, adminId,
                new DirectoryRoleRequest(dirId, BaseRole.ADMIN));

        assertThat(resp.baseRole()).isEqualTo(BaseRole.ADMIN);
    }

    @Test
    void assignDirectoryRole_existingRole_updatesBaseRole() {
        when(adminRepo.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(adminAccount("alice")));
        DirectoryConnection dir = directory();
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(dir));

        AdminDirectoryRole existing = new AdminDirectoryRole();
        existing.setBaseRole(BaseRole.READ_ONLY);
        existing.setDirectory(dir);    // needed by DirectoryRoleResponse.from()
        when(roleRepo.findByAdminAccountIdAndDirectoryId(adminId, dirId))
                .thenReturn(Optional.of(existing));
        when(roleRepo.save(any())).thenReturn(existing);

        service.assignDirectoryRole(tenantId, adminId, new DirectoryRoleRequest(dirId, BaseRole.ADMIN));

        assertThat(existing.getBaseRole()).isEqualTo(BaseRole.ADMIN);
    }

    // ── removeDirectoryRole ───────────────────────────────────────────────────

    @Test
    void removeDirectoryRole_delegatesToRepo() {
        when(adminRepo.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(adminAccount("alice")));

        service.removeDirectoryRole(tenantId, adminId, dirId);

        verify(roleRepo).deleteByAdminAccountIdAndDirectoryId(adminId, dirId);
    }

    // ── setBranchRestrictions ─────────────────────────────────────────────────

    @Test
    void setBranchRestrictions_clearsThenSetsNewOnes() {
        when(adminRepo.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(adminAccount("alice")));
        when(adminRepo.getReferenceById(adminId)).thenReturn(adminAccount("alice"));
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(directory()));
        when(dirRepo.getReferenceById(dirId)).thenReturn(directory());

        service.setBranchRestrictions(tenantId, adminId,
                new BranchRestrictionsRequest(dirId,
                        List.of("ou=Users,dc=example,dc=com", "ou=Groups,dc=example,dc=com")));

        verify(branchRepo).deleteAllByAdminAccountIdAndDirectoryId(adminId, dirId);
        verify(branchRepo, times(2)).save(any());
    }

    @Test
    void setBranchRestrictions_nullBranchDns_onlyClears() {
        when(adminRepo.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(adminAccount("alice")));
        when(dirRepo.findByIdAndTenantId(dirId, tenantId)).thenReturn(Optional.of(directory()));

        service.setBranchRestrictions(tenantId, adminId,
                new BranchRestrictionsRequest(dirId, null));

        verify(branchRepo).deleteAllByAdminAccountIdAndDirectoryId(adminId, dirId);
        verify(branchRepo, never()).save(any());
    }

    // ── setFeaturePermissions ─────────────────────────────────────────────────

    @Test
    void setFeaturePermissions_createsNewPermissions() {
        when(adminRepo.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(adminAccount("alice")));
        when(adminRepo.getReferenceById(adminId)).thenReturn(adminAccount("alice"));
        when(featureRepo.findByAdminAccountIdAndFeatureKey(adminId, FeatureKey.USER_CREATE))
                .thenReturn(Optional.empty());
        when(featureRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setFeaturePermissions(tenantId, adminId,
                List.of(new FeaturePermissionRequest(FeatureKey.USER_CREATE, true)));

        ArgumentCaptor<AdminFeaturePermission> captor =
                ArgumentCaptor.forClass(AdminFeaturePermission.class);
        verify(featureRepo).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isTrue();
        assertThat(captor.getValue().getFeatureKey()).isEqualTo(FeatureKey.USER_CREATE);
    }

    @Test
    void setFeaturePermissions_updatesExistingPermission() {
        when(adminRepo.findByIdAndTenantId(adminId, tenantId)).thenReturn(Optional.of(adminAccount("alice")));
        when(adminRepo.getReferenceById(adminId)).thenReturn(adminAccount("alice"));

        AdminFeaturePermission existing = new AdminFeaturePermission();
        existing.setFeatureKey(FeatureKey.USER_DELETE);
        existing.setEnabled(true);
        // give it a non-null id so the "new" branch is skipped
        existing.setId(UUID.randomUUID());
        when(featureRepo.findByAdminAccountIdAndFeatureKey(adminId, FeatureKey.USER_DELETE))
                .thenReturn(Optional.of(existing));
        when(featureRepo.save(any())).thenReturn(existing);

        service.setFeaturePermissions(tenantId, adminId,
                List.of(new FeaturePermissionRequest(FeatureKey.USER_DELETE, false)));

        assertThat(existing.isEnabled()).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Tenant tenant() {
        Tenant t = new Tenant();
        t.setId(tenantId);
        t.setName("Acme");
        t.setSlug("acme");
        t.setEnabled(true);
        return t;
    }

    private AdminAccount adminAccount(String username) {
        AdminAccount a = new AdminAccount();
        a.setId(adminId);
        a.setTenant(tenant());         // needed by AdminAccountResponse.from()
        a.setUsername(username);
        a.setActive(true);
        return a;
    }

    private DirectoryConnection directory() {
        DirectoryConnection dc = new DirectoryConnection();
        dc.setId(dirId);
        dc.setDisplayName("test-dir");
        return dc;
    }
}
