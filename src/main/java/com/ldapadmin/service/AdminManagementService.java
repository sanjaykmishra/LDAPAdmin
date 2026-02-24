package com.ldapadmin.service;

import com.ldapadmin.dto.admin.AdminAccountRequest;
import com.ldapadmin.dto.admin.AdminAccountResponse;
import com.ldapadmin.dto.admin.AdminPermissionsResponse;
import com.ldapadmin.dto.admin.BranchRestrictionsRequest;
import com.ldapadmin.dto.admin.DirectoryRoleRequest;
import com.ldapadmin.dto.admin.DirectoryRoleResponse;
import com.ldapadmin.dto.admin.FeaturePermissionRequest;
import com.ldapadmin.entity.AdminAccount;
import com.ldapadmin.entity.AdminBranchRestriction;
import com.ldapadmin.entity.AdminDirectoryRole;
import com.ldapadmin.entity.AdminFeaturePermission;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.Tenant;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AdminAccountRepository;
import com.ldapadmin.repository.AdminBranchRestrictionRepository;
import com.ldapadmin.repository.AdminDirectoryRoleRepository;
import com.ldapadmin.repository.AdminFeaturePermissionRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminManagementService {

    private final AdminAccountRepository          adminRepo;
    private final TenantRepository                tenantRepo;
    private final DirectoryConnectionRepository   dirRepo;
    private final AdminDirectoryRoleRepository    roleRepo;
    private final AdminBranchRestrictionRepository branchRepo;
    private final AdminFeaturePermissionRepository featureRepo;

    // ── Admin account CRUD ────────────────────────────────────────────────────

    public List<AdminAccountResponse> listAdmins(UUID tenantId) {
        requireTenant(tenantId);
        return adminRepo.findAllByTenantId(tenantId, Pageable.unpaged())
                .getContent()
                .stream()
                .map(AdminAccountResponse::from)
                .toList();
    }

    public AdminAccountResponse getAdmin(UUID tenantId, UUID adminId) {
        return AdminAccountResponse.from(requireAdmin(tenantId, adminId));
    }

    @Transactional
    public AdminAccountResponse createAdmin(UUID tenantId, AdminAccountRequest req) {
        Tenant tenant = requireTenant(tenantId);
        if (adminRepo.existsByTenantIdAndUsername(tenantId, req.username())) {
            throw new ConflictException(
                    "Admin [" + req.username() + "] already exists in tenant " + tenantId);
        }
        AdminAccount a = new AdminAccount();
        a.setTenant(tenant);
        a.setUsername(req.username());
        a.setDisplayName(req.displayName());
        a.setEmail(req.email());
        a.setActive(req.active());
        return AdminAccountResponse.from(adminRepo.save(a));
    }

    @Transactional
    public AdminAccountResponse updateAdmin(UUID tenantId, UUID adminId, AdminAccountRequest req) {
        AdminAccount a = requireAdmin(tenantId, adminId);
        // Reject slug conflicts with a different admin
        if (!a.getUsername().equals(req.username())
                && adminRepo.existsByTenantIdAndUsername(tenantId, req.username())) {
            throw new ConflictException(
                    "Admin [" + req.username() + "] already exists in tenant " + tenantId);
        }
        a.setUsername(req.username());
        a.setDisplayName(req.displayName());
        a.setEmail(req.email());
        a.setActive(req.active());
        return AdminAccountResponse.from(adminRepo.save(a));
    }

    @Transactional
    public void deleteAdmin(UUID tenantId, UUID adminId) {
        AdminAccount a = requireAdmin(tenantId, adminId);
        adminRepo.delete(a);
    }

    // ── Permission management — summary ───────────────────────────────────────

    public AdminPermissionsResponse getPermissions(UUID tenantId, UUID adminId) {
        requireAdmin(tenantId, adminId);
        return AdminPermissionsResponse.from(
                roleRepo.findAllByAdminAccountId(adminId),
                branchRepo.findAllByAdminAccountId(adminId),
                featureRepo.findAllByAdminAccountId(adminId));
    }

    // ── Dimension 1+2: directory roles ────────────────────────────────────────

    @Transactional
    public DirectoryRoleResponse assignDirectoryRole(UUID tenantId, UUID adminId,
                                                     DirectoryRoleRequest req) {
        requireAdmin(tenantId, adminId);
        DirectoryConnection dir = requireDirectory(tenantId, req.directoryId());

        AdminDirectoryRole role = roleRepo
                .findByAdminAccountIdAndDirectoryId(adminId, req.directoryId())
                .orElseGet(AdminDirectoryRole::new);

        AdminAccount adminRef = new AdminAccount();
        adminRef.setId(adminId);      // lightweight reference — already validated above
        if (role.getId() == null) {
            role.setAdminAccount(adminRepo.getReferenceById(adminId));
            role.setDirectory(dir);
        }
        role.setBaseRole(req.baseRole());
        return DirectoryRoleResponse.from(roleRepo.save(role));
    }

    @Transactional
    public void removeDirectoryRole(UUID tenantId, UUID adminId, UUID directoryId) {
        requireAdmin(tenantId, adminId);
        roleRepo.deleteByAdminAccountIdAndDirectoryId(adminId, directoryId);
    }

    // ── Dimension 3: branch restrictions ─────────────────────────────────────

    @Transactional
    public void setBranchRestrictions(UUID tenantId, UUID adminId,
                                      BranchRestrictionsRequest req) {
        requireAdmin(tenantId, adminId);
        requireDirectory(tenantId, req.directoryId());

        branchRepo.deleteAllByAdminAccountIdAndDirectoryId(adminId, req.directoryId());

        if (req.branchDns() != null) {
            DirectoryConnection dirRef = dirRepo.getReferenceById(req.directoryId());
            AdminAccount adminRef = adminRepo.getReferenceById(adminId);
            req.branchDns().forEach(dn -> {
                AdminBranchRestriction br = new AdminBranchRestriction();
                br.setAdminAccount(adminRef);
                br.setDirectory(dirRef);
                br.setBranchDn(dn);
                branchRepo.save(br);
            });
        }
    }

    // ── Dimension 4: feature permissions ─────────────────────────────────────

    @Transactional
    public void setFeaturePermissions(UUID tenantId, UUID adminId,
                                      List<FeaturePermissionRequest> permissions) {
        requireAdmin(tenantId, adminId);
        AdminAccount adminRef = adminRepo.getReferenceById(adminId);

        permissions.forEach(req -> {
            AdminFeaturePermission fp = featureRepo
                    .findByAdminAccountIdAndFeatureKey(adminId, req.featureKey())
                    .orElseGet(AdminFeaturePermission::new);

            if (fp.getId() == null) {
                fp.setAdminAccount(adminRef);
                fp.setFeatureKey(req.featureKey());
            }
            fp.setEnabled(req.enabled());
            featureRepo.save(fp);
        });
    }

    @Transactional
    public void clearFeaturePermission(UUID tenantId, UUID adminId,
                                       com.ldapadmin.entity.enums.FeatureKey featureKey) {
        requireAdmin(tenantId, adminId);
        featureRepo.deleteByAdminAccountIdAndFeatureKey(adminId, featureKey);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepo.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
    }

    private AdminAccount requireAdmin(UUID tenantId, UUID adminId) {
        return adminRepo.findByIdAndTenantId(adminId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminAccount", adminId));
    }

    private DirectoryConnection requireDirectory(UUID tenantId, UUID dirId) {
        return dirRepo.findByIdAndTenantId(dirId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", dirId));
    }
}
