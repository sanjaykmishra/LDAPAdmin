package com.ldapadmin.controller.superadmin;

import com.ldapadmin.dto.admin.AdminAccountRequest;
import com.ldapadmin.dto.admin.AdminAccountResponse;
import com.ldapadmin.dto.admin.AdminPermissionsResponse;
import com.ldapadmin.dto.admin.BranchRestrictionsRequest;
import com.ldapadmin.dto.admin.DirectoryRoleRequest;
import com.ldapadmin.dto.admin.DirectoryRoleResponse;
import com.ldapadmin.dto.admin.FeaturePermissionRequest;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.service.AdminManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin account management and four-dimensional permission assignment.
 *
 * <pre>
 *   GET    /api/superadmin/tenants/{tid}/admins                                    — list
 *   POST   /api/superadmin/tenants/{tid}/admins                                    — create
 *   GET    /api/superadmin/tenants/{tid}/admins/{id}                               — get
 *   PUT    /api/superadmin/tenants/{tid}/admins/{id}                               — update
 *   DELETE /api/superadmin/tenants/{tid}/admins/{id}                               — delete
 *   GET    /api/superadmin/tenants/{tid}/admins/{id}/permissions                   — all dims
 *   PUT    /api/superadmin/tenants/{tid}/admins/{id}/permissions/directory-roles   — dim 1+2
 *   DELETE /api/superadmin/tenants/{tid}/admins/{id}/permissions/directory-roles/{dirId}
 *   PUT    /api/superadmin/tenants/{tid}/admins/{id}/permissions/branch-restrictions — dim 3
 *   PUT    /api/superadmin/tenants/{tid}/admins/{id}/permissions/features          — dim 4
 *   DELETE /api/superadmin/tenants/{tid}/admins/{id}/permissions/features/{key}   — clear override
 * </pre>
 */
@RestController
@RequestMapping("/api/superadmin/tenants/{tenantId}/admins")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class AdminManagementController {

    private final AdminManagementService service;

    // ── Account CRUD ──────────────────────────────────────────────────────────

    @GetMapping
    public List<AdminAccountResponse> list(@PathVariable UUID tenantId) {
        return service.listAdmins(tenantId);
    }

    @PostMapping
    public ResponseEntity<AdminAccountResponse> create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody AdminAccountRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createAdmin(tenantId, req));
    }

    @GetMapping("/{adminId}")
    public AdminAccountResponse get(@PathVariable UUID tenantId,
                                    @PathVariable UUID adminId) {
        return service.getAdmin(tenantId, adminId);
    }

    @PutMapping("/{adminId}")
    public AdminAccountResponse update(@PathVariable UUID tenantId,
                                       @PathVariable UUID adminId,
                                       @Valid @RequestBody AdminAccountRequest req) {
        return service.updateAdmin(tenantId, adminId, req);
    }

    @DeleteMapping("/{adminId}")
    public ResponseEntity<Void> delete(@PathVariable UUID tenantId,
                                       @PathVariable UUID adminId) {
        service.deleteAdmin(tenantId, adminId);
        return ResponseEntity.noContent().build();
    }

    // ── Permission summary ────────────────────────────────────────────────────

    @GetMapping("/{adminId}/permissions")
    public AdminPermissionsResponse getPermissions(@PathVariable UUID tenantId,
                                                   @PathVariable UUID adminId) {
        return service.getPermissions(tenantId, adminId);
    }

    // ── Dimension 1+2: directory roles ────────────────────────────────────────

    @PutMapping("/{adminId}/permissions/directory-roles")
    public DirectoryRoleResponse assignDirectoryRole(
            @PathVariable UUID tenantId,
            @PathVariable UUID adminId,
            @Valid @RequestBody DirectoryRoleRequest req) {
        return service.assignDirectoryRole(tenantId, adminId, req);
    }

    @DeleteMapping("/{adminId}/permissions/directory-roles/{directoryId}")
    public ResponseEntity<Void> removeDirectoryRole(@PathVariable UUID tenantId,
                                                    @PathVariable UUID adminId,
                                                    @PathVariable UUID directoryId) {
        service.removeDirectoryRole(tenantId, adminId, directoryId);
        return ResponseEntity.noContent().build();
    }

    // ── Dimension 3: branch restrictions ─────────────────────────────────────

    @PutMapping("/{adminId}/permissions/branch-restrictions")
    public ResponseEntity<Void> setBranchRestrictions(
            @PathVariable UUID tenantId,
            @PathVariable UUID adminId,
            @Valid @RequestBody BranchRestrictionsRequest req) {
        service.setBranchRestrictions(tenantId, adminId, req);
        return ResponseEntity.noContent().build();
    }

    // ── Dimension 4: feature permissions ─────────────────────────────────────

    @PutMapping("/{adminId}/permissions/features")
    public ResponseEntity<Void> setFeaturePermissions(
            @PathVariable UUID tenantId,
            @PathVariable UUID adminId,
            @RequestBody List<@Valid FeaturePermissionRequest> permissions) {
        service.setFeaturePermissions(tenantId, adminId, permissions);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{adminId}/permissions/features/{featureKey}")
    public ResponseEntity<Void> clearFeaturePermission(
            @PathVariable UUID tenantId,
            @PathVariable UUID adminId,
            @PathVariable FeatureKey featureKey) {
        service.clearFeaturePermission(tenantId, adminId, featureKey);
        return ResponseEntity.noContent().build();
    }
}
