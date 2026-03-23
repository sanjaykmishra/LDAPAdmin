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
import com.ldapadmin.entity.ProvisioningProfile;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * LDAP user operations for a specific directory.
 */
@RestController
@RequestMapping("/api/v1/directories/{directoryId}/users")
@RequiredArgsConstructor
public class UserController {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT      = 2000;

    private final LdapOperationService service;
    private final ApprovalWorkflowService approvalService;
    private final com.ldapadmin.service.ProvisioningProfileService profileService;
    private final com.ldapadmin.service.PasswordPolicyService passwordPolicyService;
    private final com.ldapadmin.service.ApprovalNotificationService notificationService;

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

    @PostMapping
    @RequiresFeature(FeatureKey.USER_CREATE)
    public ResponseEntity<?> create(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateEntryRequest req) {

        // Check if approval is required for the matching profile
        Optional<ProvisioningProfile> profile = approvalService.findProfileForDn(directoryId, req.dn());

        // Apply computed/default/fixed attribute values from the profile
        if (profile.isPresent()) {
            Map<String, List<String>> attrs = new LinkedHashMap<>(req.attributes());
            profileService.applyDefaults(profile.get().getId(), attrs);
            req = new CreateEntryRequest(req.dn(), attrs);
        }

        if (profile.isPresent() && approvalService.isApprovalRequired(profile.get().getId())) {
            PendingApproval pa = approvalService.submitForApproval(
                    directoryId, profile.get().getId(), principal,
                    ApprovalRequestType.USER_CREATE, req);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "message", "User creation submitted for approval",
                            "approvalId", pa.getId()));
        }

        LdapEntryResponse result = service.createUser(directoryId, principal, req);

        // Email password to user if profile is configured for it
        if (profile.isPresent() && profile.get().isEmailPasswordToUser()) {
            Map<String, List<String>> attrs = req.attributes();
            List<String> mailValues = attrs.get("mail");
            List<String> pwdValues  = attrs.get("userPassword");
            if (mailValues != null && !mailValues.isEmpty()
                    && pwdValues != null && !pwdValues.isEmpty()) {
                String displayName = attrs.containsKey("cn") && !attrs.get("cn").isEmpty()
                        ? attrs.get("cn").get(0) : "User";
                notificationService.sendPasswordEmail(
                        mailValues.get(0), displayName, pwdValues.get(0));
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/entry")
    public LdapEntryResponse get(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn,
            @RequestParam(required = false, defaultValue = "") String attributes) {

        String[] attrArray = attributes.isBlank() ? new String[0] : attributes.split(",");
        return service.getUser(directoryId, principal, dn, attrArray);
    }

    @PutMapping("/entry")
    @RequiresFeature(FeatureKey.USER_EDIT)
    public LdapEntryResponse update(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn,
            @Valid @RequestBody UpdateEntryRequest req) {
        return service.updateUser(directoryId, principal, dn, req);
    }

    @DeleteMapping("/entry")
    @RequiresFeature(FeatureKey.USER_DELETE)
    public ResponseEntity<Void> delete(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn) {
        service.deleteUser(directoryId, principal, dn);
        return ResponseEntity.noContent().build();
    }

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

    @PostMapping("/bulk-update")
    @RequiresFeature(FeatureKey.BULK_ATTRIBUTE_UPDATE)
    public BulkAttributeUpdateResult bulkUpdate(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody BulkAttributeUpdateRequest req) {
        return service.bulkUpdateAttributes(directoryId, principal, req);
    }

    @PostMapping("/move")
    @RequiresFeature(FeatureKey.USER_MOVE)
    public ResponseEntity<?> move(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn,
            @Valid @RequestBody MoveUserRequest req) {

        Optional<ProvisioningProfile> profile = approvalService.findProfileForDn(directoryId, dn);
        if (profile.isPresent() && approvalService.isApprovalRequired(profile.get().getId())) {
            PendingApproval pa = approvalService.submitForApproval(
                    directoryId, profile.get().getId(), principal,
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

    @GetMapping("/password-status")
    public Map<String, Object> passwordStatus(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam String dn) {
        return passwordPolicyService.getPasswordStatus(directoryId, principal, dn);
    }
}
