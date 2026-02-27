package com.ldapadmin.controller.superadmin;

import com.ldapadmin.dto.admin.AdminAccountRequest;
import com.ldapadmin.dto.admin.AdminAccountResponse;
import com.ldapadmin.dto.admin.AdminPermissionsResponse;
import com.ldapadmin.dto.admin.BranchRestrictionsRequest;
import com.ldapadmin.dto.admin.FeaturePermissionRequest;
import com.ldapadmin.dto.admin.RealmRoleRequest;
import com.ldapadmin.dto.admin.RealmRoleResponse;
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
 *   GET    /api/v1/superadmin/admins                                       — list
 *   POST   /api/v1/superadmin/admins                                       — create
 *   GET    /api/v1/superadmin/admins/{id}                                  — get
 *   PUT    /api/v1/superadmin/admins/{id}                                  — update
 *   DELETE /api/v1/superadmin/admins/{id}                                  — delete
 *   GET    /api/v1/superadmin/admins/{id}/permissions                      — all dims
 *   PUT    /api/v1/superadmin/admins/{id}/permissions/realm-roles          — dim 1+2
 *   DELETE /api/v1/superadmin/admins/{id}/permissions/realm-roles/{realmId}
 *   PUT    /api/v1/superadmin/admins/{id}/permissions/branch-restrictions  — dim 3
 *   PUT    /api/v1/superadmin/admins/{id}/permissions/features             — dim 4
 *   DELETE /api/v1/superadmin/admins/{id}/permissions/features/{key}       — clear override
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/superadmin/admins")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class AdminManagementController {

    private final AdminManagementService service;

    // ── Account CRUD ──────────────────────────────────────────────────────────

    @GetMapping
    public List<AdminAccountResponse> list() {
        return service.listAdmins();
    }

    @PostMapping
    public ResponseEntity<AdminAccountResponse> create(@Valid @RequestBody AdminAccountRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAdmin(req));
    }

    @GetMapping("/{adminId}")
    public AdminAccountResponse get(@PathVariable UUID adminId) {
        return service.getAdmin(adminId);
    }

    @PutMapping("/{adminId}")
    public AdminAccountResponse update(@PathVariable UUID adminId,
                                       @Valid @RequestBody AdminAccountRequest req) {
        return service.updateAdmin(adminId, req);
    }

    @DeleteMapping("/{adminId}")
    public ResponseEntity<Void> delete(@PathVariable UUID adminId) {
        service.deleteAdmin(adminId);
        return ResponseEntity.noContent().build();
    }

    // ── Permission summary ────────────────────────────────────────────────────

    @GetMapping("/{adminId}/permissions")
    public AdminPermissionsResponse getPermissions(@PathVariable UUID adminId) {
        return service.getPermissions(adminId);
    }

    // ── Dimension 1+2: realm roles ────────────────────────────────────────────

    @PutMapping("/{adminId}/permissions/realm-roles")
    public RealmRoleResponse assignRealmRole(
            @PathVariable UUID adminId,
            @Valid @RequestBody RealmRoleRequest req) {
        return service.assignRealmRole(adminId, req);
    }

    @DeleteMapping("/{adminId}/permissions/realm-roles/{realmId}")
    public ResponseEntity<Void> removeRealmRole(@PathVariable UUID adminId,
                                                @PathVariable UUID realmId) {
        service.removeRealmRole(adminId, realmId);
        return ResponseEntity.noContent().build();
    }

    // ── Dimension 3: branch restrictions ─────────────────────────────────────

    @PutMapping("/{adminId}/permissions/branch-restrictions")
    public ResponseEntity<Void> setBranchRestrictions(
            @PathVariable UUID adminId,
            @Valid @RequestBody BranchRestrictionsRequest req) {
        service.setBranchRestrictions(adminId, req);
        return ResponseEntity.noContent().build();
    }

    // ── Dimension 4: feature permissions ─────────────────────────────────────

    @PutMapping("/{adminId}/permissions/features")
    public ResponseEntity<Void> setFeaturePermissions(
            @PathVariable UUID adminId,
            @RequestBody List<@Valid FeaturePermissionRequest> permissions) {
        service.setFeaturePermissions(adminId, permissions);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{adminId}/permissions/features/{featureKey}")
    public ResponseEntity<Void> clearFeaturePermission(
            @PathVariable UUID adminId,
            @PathVariable FeatureKey featureKey) {
        service.clearFeaturePermission(adminId, featureKey);
        return ResponseEntity.noContent().build();
    }
}
