package com.ldapadmin.service;

import com.ldapadmin.dto.admin.AdminAccountRequest;
import com.ldapadmin.dto.admin.AdminAccountResponse;
import com.ldapadmin.dto.admin.AdminPermissionsResponse;

import com.ldapadmin.dto.admin.FeaturePermissionRequest;
import com.ldapadmin.dto.admin.RealmRoleRequest;
import com.ldapadmin.dto.admin.RealmRoleResponse;
import com.ldapadmin.entity.Account;

import com.ldapadmin.entity.AdminFeaturePermission;
import com.ldapadmin.entity.AdminRealmRole;
import com.ldapadmin.entity.Realm;
import com.ldapadmin.entity.enums.AccountRole;
import com.ldapadmin.entity.enums.AccountType;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AccountRepository;

import com.ldapadmin.repository.AdminFeaturePermissionRepository;
import com.ldapadmin.repository.AdminRealmRoleRepository;
import com.ldapadmin.repository.RealmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminManagementService {

    private final AccountRepository               accountRepo;
    private final RealmRepository                 realmRepo;
    private final AdminRealmRoleRepository        realmRoleRepo;

    private final AdminFeaturePermissionRepository featureRepo;
    private final PasswordEncoder                 passwordEncoder;

    // ── Admin account CRUD ────────────────────────────────────────────────────

    public List<AdminAccountResponse> listAdmins() {
        return accountRepo.findAll().stream()
                .map(AdminAccountResponse::from)
                .toList();
    }

    public AdminAccountResponse getAdmin(UUID adminId) {
        return AdminAccountResponse.from(requireAccount(adminId));
    }

    @Transactional
    public AdminAccountResponse createAdmin(AdminAccountRequest req) {
        if (accountRepo.existsByUsername(req.username())) {
            throw new ConflictException("Account [" + req.username() + "] already exists");
        }
        Account a = new Account();
        a.setUsername(req.username());
        a.setDisplayName(req.displayName());
        a.setEmail(req.email());
        a.setRole(req.role());
        a.setAuthType(req.authType());
        a.setActive(req.active());
        if (req.authType() == AccountType.LOCAL && req.password() != null && !req.password().isBlank()) {
            a.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        if (req.authType() == AccountType.LDAP) {
            a.setLdapDn(req.ldapDn());
        }
        // OIDC accounts need no password or ldapDn — username is matched against IdP claim
        return AdminAccountResponse.from(accountRepo.save(a));
    }

    @Transactional
    public AdminAccountResponse updateAdmin(UUID adminId, AdminAccountRequest req) {
        Account a = requireAccount(adminId);
        if (!a.getUsername().equals(req.username()) && accountRepo.existsByUsername(req.username())) {
            throw new ConflictException("Account [" + req.username() + "] already exists");
        }
        a.setUsername(req.username());
        a.setDisplayName(req.displayName());
        a.setEmail(req.email());
        a.setRole(req.role());
        a.setAuthType(req.authType());
        a.setActive(req.active());
        if (req.authType() == AccountType.LOCAL && req.password() != null && !req.password().isBlank()) {
            a.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        if (req.authType() == AccountType.LDAP) {
            a.setLdapDn(req.ldapDn());
        } else {
            a.setLdapDn(null);
        }
        // Clear password hash when switching away from LOCAL
        if (req.authType() != AccountType.LOCAL) {
            a.setPasswordHash(null);
        }
        return AdminAccountResponse.from(accountRepo.save(a));
    }

    @Transactional
    public void resetAdminPassword(UUID adminId, String newPassword) {
        Account a = requireAccount(adminId);
        if (a.getAuthType() != AccountType.LOCAL) {
            throw new IllegalArgumentException("Password reset is only supported for LOCAL accounts");
        }
        a.setPasswordHash(passwordEncoder.encode(newPassword));
        accountRepo.save(a);
    }

    @Transactional
    public void deleteAdmin(UUID adminId) {
        accountRepo.delete(requireAccount(adminId));
    }

    // ── Permission management — summary ───────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminPermissionsResponse getPermissions(UUID adminId) {
        requireAccount(adminId);
        return AdminPermissionsResponse.from(
                realmRoleRepo.findAllByAdminAccountId(adminId),
                featureRepo.findAllByAdminAccountId(adminId));
    }

    // ── Dimension 1+2: realm roles ────────────────────────────────────────────

    @Transactional
    public RealmRoleResponse assignRealmRole(UUID adminId, RealmRoleRequest req) {
        Account admin = requireAccount(adminId);
        Realm   realm = requireRealm(req.realmId());

        AdminRealmRole role = realmRoleRepo
                .findByAdminAccountIdAndRealmId(adminId, req.realmId())
                .orElseGet(AdminRealmRole::new);

        if (role.getId() == null) {
            role.setAdminAccount(admin);
            role.setRealm(realm);
        }
        role.setBaseRole(req.baseRole());
        return RealmRoleResponse.from(realmRoleRepo.save(role));
    }

    @Transactional
    public void removeRealmRole(UUID adminId, UUID realmId) {
        requireAccount(adminId);
        realmRoleRepo.deleteByAdminAccountIdAndRealmId(adminId, realmId);
    }

    // ── Dimension 4: feature permissions ─────────────────────────────────────

    @Transactional
    public void setFeaturePermissions(UUID adminId, List<FeaturePermissionRequest> permissions) {
        Account admin = requireAccount(adminId);

        permissions.forEach(req -> {
            AdminFeaturePermission fp = featureRepo
                    .findByAdminAccountIdAndFeatureKey(adminId, req.featureKey())
                    .orElseGet(AdminFeaturePermission::new);

            if (fp.getId() == null) {
                fp.setAdminAccount(admin);
                fp.setFeatureKey(req.featureKey());
            }
            fp.setEnabled(req.enabled());
            featureRepo.save(fp);
        });
    }

    @Transactional
    public void clearFeaturePermission(UUID adminId, FeatureKey featureKey) {
        requireAccount(adminId);
        featureRepo.deleteByAdminAccountIdAndFeatureKey(adminId, featureKey);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Account requireAccount(UUID adminId) {
        return accountRepo.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", adminId));
    }

    private Realm requireRealm(UUID realmId) {
        return realmRepo.findById(realmId)
                .orElseThrow(() -> new ResourceNotFoundException("Realm", realmId));
    }
}
