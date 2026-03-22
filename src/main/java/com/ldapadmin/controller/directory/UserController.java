package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.DirectoryId;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.dto.ldap.BulkAttributeUpdateRequest;
import com.ldapadmin.dto.ldap.BulkAttributeUpdateResult;
import com.ldapadmin.dto.ldap.CreateEntryRequest;
import com.ldapadmin.dto.ldap.LdapEntryResponse;
import com.ldapadmin.dto.ldap.MoveUserRequest;
import com.ldapadmin.dto.ldap.ResetPasswordLdapRequest;
import com.ldapadmin.dto.ldap.UpdateEntryRequest;
import com.ldapadmin.entity.PendingApproval;
import com.ldapadmin.entity.Realm;
import com.ldapadmin.entity.enums.ApprovalRequestType;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.service.ApprovalWorkflowService;
import com.ldapadmin.service.LdapOperationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * LDAP user operations for a specific directory.
 *
 * <pre>
 *   GET    /api/directories/{directoryId}/users          — search
 *   POST   /api/directories/{directoryId}/users          — create
 *   GET    /api/directories/{directoryId}/users/entry    — get by DN (?dn=)
 *   PUT    /api/directories/{directoryId}/users/entry    — update by DN (?dn=)
 *   DELETE /api/directories/{directoryId}/users/entry    — delete by DN (?dn=)
 *   POST   /api/directories/{directoryId}/users/enable   — enable (?dn=)
 *   POST   /api/directories/{directoryId}/users/disable  — disable (?dn=)
 *   POST   /api/directories/{directoryId}/users/move     — move to new parent
 * </pre>
 *
 * <p>Distinguished names are passed as query parameters to avoid percent-encoding
 * issues with slashes, commas, and equals signs in URL path segments.</p>
 *
 * <p>Feature permission checks (dimensions 1, 2, 4) are enforced by the
 * {@link com.ldapadmin.auth.FeaturePermissionAspect} via {@link RequiresFeature}.
 * Branch access (dimension 3) and directory-access checks for read operations
 * are enforced inside {@link LdapOperationService}.</p>
 */
@RestController
@RequestMapping("/api/v1/directories/{directoryId}/users")
@RequiredArgsConstructor
public class UserController {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT      = 2000;

    private final LdapOperationService service;
    private final ApprovalWorkflowService approvalService;

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Searches for user entries. No specific feature key required — any
     * authenticated principal with directory access may search.
     *
     * @param filter     LDAP filter (optional; defaults to {@code (objectClass=*)})
     * @param baseDn     search base DN (optional; defaults to directory base DN)
     * @param limit      max results to return (1–2000, default 200)
     * @param attributes comma-separated list of attributes to include (default: all)
     */
    @GetMapping
    public List<LdapEntryResponse> search(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String baseDn,
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(required = false, defaultValue = "") String attributes) {

        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        String[] attrArray = attributes.isBlank() ? new String[0] : attributes.split(",");
        return service.searchUsers(directoryId, principal, filter, baseDn, safeLimit, attrArray);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    @RequiresFeature(FeatureKey.USER_CREATE)
    public ResponseEntity<?> create(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateEntryRequest req) {

        // Check if approval is required for the matching realm
        Optional<Realm> realm = approvalService.findRealmForDn(directoryId, req.dn());
        if (realm.isPresent() && approvalService.isApprovalRequired(realm.get().getId())) {
            PendingApproval pa = approvalService.submitForApproval(
                    directoryId, realm.get().getId(), principal,
                    ApprovalRequestType.USER_CREATE, req);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "message", "User creation submitted for approval",
                            "approvalId", pa.getId()));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createUser(directoryId, principal, req));
    }

    // ── Get by DN ─────────────────────────────────────────────────────────────

    @GetMapping("/entry")
    public LdapEntryResponse get(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn,
            @RequestParam(required = false, defaultValue = "") String attributes) {

        String[] attrArray = attributes.isBlank() ? new String[0] : attributes.split(",");
        return service.getUser(directoryId, principal, dn, attrArray);
    }

    // ── Update by DN ──────────────────────────────────────────────────────────

    @PutMapping("/entry")
    @RequiresFeature(FeatureKey.USER_EDIT)
    public LdapEntryResponse update(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn,
            @Valid @RequestBody UpdateEntryRequest req) {
        return service.updateUser(directoryId, principal, dn, req);
    }

    // ── Delete by DN ──────────────────────────────────────────────────────────

    @DeleteMapping("/entry")
    @RequiresFeature(FeatureKey.USER_DELETE)
    public ResponseEntity<Void> delete(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn) {
        service.deleteUser(directoryId, principal, dn);
        return ResponseEntity.noContent().build();
    }

    // ── Enable / Disable ──────────────────────────────────────────────────────

    @PostMapping("/enable")
    @RequiresFeature(FeatureKey.USER_ENABLE_DISABLE)
    public ResponseEntity<Void> enable(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn) {
        service.enableUser(directoryId, principal, dn);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/disable")
    @RequiresFeature(FeatureKey.USER_ENABLE_DISABLE)
    public ResponseEntity<Void> disable(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn) {
        service.disableUser(directoryId, principal, dn);
        return ResponseEntity.noContent().build();
    }

    // ── Reset Password ─────────────────────────────────────────────────────────

    @PostMapping("/reset-password")
    @RequiresFeature(FeatureKey.USER_RESET_PASSWORD)
    public ResponseEntity<Void> resetPassword(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn,
            @Valid @RequestBody ResetPasswordLdapRequest req) {
        service.resetPassword(directoryId, principal, dn, req.newPassword());
        return ResponseEntity.noContent().build();
    }

    // ── Bulk Attribute Update ───────────────────────────────────────────────────

    @PostMapping("/bulk-update")
    @RequiresFeature(FeatureKey.BULK_ATTRIBUTE_UPDATE)
    public BulkAttributeUpdateResult bulkUpdate(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody BulkAttributeUpdateRequest req) {
        return service.bulkUpdateAttributes(directoryId, principal, req);
    }

    // ── Move ──────────────────────────────────────────────────────────────────

    @PostMapping("/move")
    @RequiresFeature(FeatureKey.USER_MOVE)
    public ResponseEntity<?> move(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn,
            @Valid @RequestBody MoveUserRequest req) {

        Optional<Realm> realm = approvalService.findRealmForDn(directoryId, dn);
        if (realm.isPresent() && approvalService.isMoveApprovalRequired(realm.get().getId())) {
            PendingApproval pa = approvalService.submitForApproval(
                    directoryId, realm.get().getId(), principal,
                    ApprovalRequestType.USER_MOVE,
                    Map.of("dn", dn, "request", req));
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "message", "User move submitted for approval",
                            "approvalId", pa.getId()));
        }

        service.moveUser(directoryId, principal, dn, req);
        return ResponseEntity.noContent().build();
    }
}
