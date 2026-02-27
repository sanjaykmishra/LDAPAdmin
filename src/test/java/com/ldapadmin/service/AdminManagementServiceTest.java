package com.ldapadmin.service;

import com.ldapadmin.dto.admin.AdminAccountRequest;
import com.ldapadmin.dto.admin.AdminAccountResponse;
import com.ldapadmin.dto.admin.BranchRestrictionsRequest;
import com.ldapadmin.dto.admin.FeaturePermissionRequest;
import com.ldapadmin.dto.admin.RealmRoleRequest;
import com.ldapadmin.dto.admin.RealmRoleResponse;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.AdminFeaturePermission;
import com.ldapadmin.entity.AdminRealmRole;
import com.ldapadmin.entity.Realm;
import com.ldapadmin.entity.enums.AccountRole;
import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.entity.enums.BaseRole;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.AdminBranchRestrictionRepository;
import com.ldapadmin.repository.AdminFeaturePermissionRepository;
import com.ldapadmin.repository.AdminRealmRoleRepository;
import com.ldapadmin.repository.RealmRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminManagementServiceTest {

    @Mock private AccountRepository               accountRepo;
    @Mock private RealmRepository                 realmRepo;
    @Mock private AdminRealmRoleRepository        realmRoleRepo;
    @Mock private AdminBranchRestrictionRepository branchRepo;
    @Mock private AdminFeaturePermissionRepository featureRepo;
    @Mock private PasswordEncoder                 passwordEncoder;

    private AdminManagementService service;

    private final UUID adminId = UUID.randomUUID();
    private final UUID realmId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AdminManagementService(
                accountRepo, realmRepo, realmRoleRepo, branchRepo, featureRepo, passwordEncoder);
    }

    // ── listAdmins ────────────────────────────────────────────────────────────

    @Test
    void listAdmins_returnsMappedList() {
        Account a = adminAccount("alice");
        when(accountRepo.findAllByRole(AccountRole.ADMIN)).thenReturn(List.of(a));

        List<AdminAccountResponse> result = service.listAdmins();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).username()).isEqualTo("alice");
    }

    // ── getAdmin ──────────────────────────────────────────────────────────────

    @Test
    void getAdmin_notFound_throwsResourceNotFound() {
        when(accountRepo.findById(adminId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAdmin(adminId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── createAdmin ───────────────────────────────────────────────────────────

    @Test
    void createAdmin_duplicateUsername_throwsConflict() {
        when(accountRepo.existsByUsername("bob")).thenReturn(true);

        assertThatThrownBy(() -> service.createAdmin(
                new AdminAccountRequest("bob", "Bob", "bob@e.com", true)))
                .isInstanceOf(ConflictException.class);

        verify(accountRepo, never()).save(any());
    }

    @Test
    void createAdmin_success_savesAndReturns() {
        when(accountRepo.existsByUsername("alice")).thenReturn(false);
        Account saved = adminAccount("alice");
        when(accountRepo.save(any())).thenReturn(saved);

        AdminAccountResponse resp = service.createAdmin(
                new AdminAccountRequest("alice", "Alice", "a@e.com", true));

        assertThat(resp.username()).isEqualTo("alice");
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepo).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(AccountRole.ADMIN);
        assertThat(captor.getValue().getAuthType()).isEqualTo(AccountType.LOCAL);
    }

    // ── updateAdmin ───────────────────────────────────────────────────────────

    @Test
    void updateAdmin_notFound_throwsResourceNotFound() {
        when(accountRepo.findById(adminId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAdmin(adminId,
                new AdminAccountRequest("alice", "Alice", "a@e.com", true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateAdmin_usernameConflictWithDifferentAdmin_throwsConflict() {
        Account existing = adminAccount("alice");
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(existing));
        when(accountRepo.existsByUsername("bob")).thenReturn(true);

        assertThatThrownBy(() -> service.updateAdmin(adminId,
                new AdminAccountRequest("bob", "Bob", "b@e.com", true)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateAdmin_sameUsername_noConflictCheck() {
        Account existing = adminAccount("alice");
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(existing));
        when(accountRepo.save(any())).thenReturn(existing);

        service.updateAdmin(adminId,
                new AdminAccountRequest("alice", "Alice Renamed", "a@e.com", true));

        verify(accountRepo, never()).existsByUsername("alice");
    }

    // ── deleteAdmin ───────────────────────────────────────────────────────────

    @Test
    void deleteAdmin_success_callsDelete() {
        Account a = adminAccount("alice");
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(a));

        service.deleteAdmin(adminId);

        verify(accountRepo).delete(a);
    }

    // ── assignRealmRole ───────────────────────────────────────────────────────

    @Test
    void assignRealmRole_realmNotFound_throwsResourceNotFound() {
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount("alice")));
        when(realmRepo.findById(realmId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignRealmRole(adminId,
                new RealmRoleRequest(realmId, BaseRole.ADMIN)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void assignRealmRole_newRole_savesRole() {
        Account admin = adminAccount("alice");
        Realm realm = realm();
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(admin));
        when(realmRepo.findById(realmId)).thenReturn(Optional.of(realm));
        when(realmRoleRepo.findByAdminAccountIdAndRealmId(adminId, realmId))
                .thenReturn(Optional.empty());

        AdminRealmRole saved = new AdminRealmRole();
        saved.setBaseRole(BaseRole.ADMIN);
        saved.setRealm(realm);
        when(realmRoleRepo.save(any())).thenReturn(saved);

        RealmRoleResponse resp = service.assignRealmRole(adminId,
                new RealmRoleRequest(realmId, BaseRole.ADMIN));

        assertThat(resp.baseRole()).isEqualTo(BaseRole.ADMIN);
    }

    @Test
    void assignRealmRole_existingRole_updatesBaseRole() {
        Account admin = adminAccount("alice");
        Realm realm = realm();
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(admin));
        when(realmRepo.findById(realmId)).thenReturn(Optional.of(realm));

        AdminRealmRole existing = new AdminRealmRole();
        existing.setBaseRole(BaseRole.READ_ONLY);
        existing.setRealm(realm);
        when(realmRoleRepo.findByAdminAccountIdAndRealmId(adminId, realmId))
                .thenReturn(Optional.of(existing));
        when(realmRoleRepo.save(any())).thenReturn(existing);

        service.assignRealmRole(adminId, new RealmRoleRequest(realmId, BaseRole.ADMIN));

        assertThat(existing.getBaseRole()).isEqualTo(BaseRole.ADMIN);
    }

    // ── removeRealmRole ───────────────────────────────────────────────────────

    @Test
    void removeRealmRole_delegatesToRepo() {
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount("alice")));

        service.removeRealmRole(adminId, realmId);

        verify(realmRoleRepo).deleteByAdminAccountIdAndRealmId(adminId, realmId);
    }

    // ── setBranchRestrictions ─────────────────────────────────────────────────

    @Test
    void setBranchRestrictions_clearsThenSetsNewOnes() {
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount("alice")));
        when(realmRepo.findById(realmId)).thenReturn(Optional.of(realm()));

        service.setBranchRestrictions(adminId, new BranchRestrictionsRequest(realmId,
                List.of("ou=Users,dc=example,dc=com", "ou=Groups,dc=example,dc=com")));

        verify(branchRepo).deleteAllByAdminAccountIdAndRealmId(adminId, realmId);
        verify(branchRepo, times(2)).save(any());
    }

    @Test
    void setBranchRestrictions_nullBranchDns_onlyClears() {
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount("alice")));
        when(realmRepo.findById(realmId)).thenReturn(Optional.of(realm()));

        service.setBranchRestrictions(adminId, new BranchRestrictionsRequest(realmId, null));

        verify(branchRepo).deleteAllByAdminAccountIdAndRealmId(adminId, realmId);
        verify(branchRepo, never()).save(any());
    }

    // ── setFeaturePermissions ─────────────────────────────────────────────────

    @Test
    void setFeaturePermissions_createsNewPermissions() {
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount("alice")));
        when(featureRepo.findByAdminAccountIdAndFeatureKey(adminId, FeatureKey.USER_CREATE))
                .thenReturn(Optional.empty());
        when(featureRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setFeaturePermissions(adminId,
                List.of(new FeaturePermissionRequest(FeatureKey.USER_CREATE, true)));

        ArgumentCaptor<AdminFeaturePermission> captor =
                ArgumentCaptor.forClass(AdminFeaturePermission.class);
        verify(featureRepo).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isTrue();
        assertThat(captor.getValue().getFeatureKey()).isEqualTo(FeatureKey.USER_CREATE);
    }

    @Test
    void setFeaturePermissions_updatesExistingPermission() {
        when(accountRepo.findById(adminId)).thenReturn(Optional.of(adminAccount("alice")));

        AdminFeaturePermission existing = new AdminFeaturePermission();
        existing.setId(UUID.randomUUID());
        existing.setFeatureKey(FeatureKey.USER_DELETE);
        existing.setEnabled(true);
        when(featureRepo.findByAdminAccountIdAndFeatureKey(adminId, FeatureKey.USER_DELETE))
                .thenReturn(Optional.of(existing));
        when(featureRepo.save(any())).thenReturn(existing);

        service.setFeaturePermissions(adminId,
                List.of(new FeaturePermissionRequest(FeatureKey.USER_DELETE, false)));

        assertThat(existing.isEnabled()).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Account adminAccount(String username) {
        Account a = new Account();
        a.setId(adminId);
        a.setUsername(username);
        a.setRole(AccountRole.ADMIN);
        a.setAuthType(AccountType.LOCAL);
        a.setActive(true);
        return a;
    }

    private Realm realm() {
        Realm r = new Realm();
        r.setId(realmId);
        r.setName("test-realm");
        return r;
    }
}
