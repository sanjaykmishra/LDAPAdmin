package com.ldapadmin.controller.directory;

import com.ldapadmin.dto.profile.*;
import com.ldapadmin.service.PasswordGeneratorService;
import com.ldapadmin.service.ProvisioningProfileService;
import com.ldapadmin.entity.ProvisioningProfile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Provisioning profile management and sub-resource endpoints.
 *
 * <pre>
 *   GET    /api/v1/directories/{dirId}/profiles                          — list
 *   POST   /api/v1/directories/{dirId}/profiles                          — create
 *   GET    /api/v1/directories/{dirId}/profiles/{profileId}              — get
 *   PUT    /api/v1/directories/{dirId}/profiles/{profileId}              — update
 *   DELETE /api/v1/directories/{dirId}/profiles/{profileId}              — delete
 *   POST   /api/v1/directories/{dirId}/profiles/{profileId}/clone        — clone
 *
 *   GET    /api/v1/profiles/{profileId}/lifecycle                         — get lifecycle policy
 *   PUT    /api/v1/profiles/{profileId}/lifecycle                         — set lifecycle policy
 *   DELETE /api/v1/profiles/{profileId}/lifecycle                         — remove lifecycle policy
 *
 *   GET    /api/v1/profiles/{profileId}/approval                          — get approval config
 *   PUT    /api/v1/profiles/{profileId}/approval                          — set approval config
 *   GET    /api/v1/profiles/{profileId}/approvers                         — list approvers
 *   PUT    /api/v1/profiles/{profileId}/approvers                         — set approvers
 * </pre>
 */
@RestController
@RequiredArgsConstructor
public class ProvisioningProfileController {

    private final ProvisioningProfileService service;
    private final PasswordGeneratorService passwordGenerator;

    // ── Profile CRUD (directory-scoped) ───────────────────────────────────────

    @GetMapping("/api/v1/directories/{directoryId}/profiles")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public List<ProfileResponse> list(@PathVariable UUID directoryId) {
        return service.list(directoryId);
    }

    @PostMapping("/api/v1/directories/{directoryId}/profiles")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ProfileResponse> create(
            @PathVariable UUID directoryId,
            @Valid @RequestBody CreateProfileRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(directoryId, req));
    }

    @GetMapping("/api/v1/directories/{directoryId}/profiles/{profileId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public ProfileResponse get(@PathVariable UUID directoryId,
                                @PathVariable UUID profileId) {
        return service.get(directoryId, profileId);
    }

    @PutMapping("/api/v1/directories/{directoryId}/profiles/{profileId}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ProfileResponse update(@PathVariable UUID directoryId,
                                   @PathVariable UUID profileId,
                                   @Valid @RequestBody UpdateProfileRequest req) {
        return service.update(directoryId, profileId, req);
    }

    @DeleteMapping("/api/v1/directories/{directoryId}/profiles/{profileId}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID directoryId,
                                        @PathVariable UUID profileId) {
        service.delete(directoryId, profileId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/directories/{directoryId}/profiles/{profileId}/clone")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ProfileResponse> clone(
            @PathVariable UUID directoryId,
            @PathVariable UUID profileId,
            @RequestBody Map<String, String> body) {
        String newName = body.get("name");
        if (newName == null || newName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.clone(directoryId, profileId, newName));
    }

    // ── Password Generation ──────────────────────────────────────────────────

    @PostMapping("/api/v1/profiles/{profileId}/generate-password")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    public Map<String, String> generatePassword(@PathVariable UUID profileId) {
        return Map.of("password", service.generatePassword(profileId));
    }

    // ── Lifecycle Policy ──────────────────────────────────────────────────────

    @GetMapping("/api/v1/profiles/{profileId}/lifecycle")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public LifecyclePolicyResponse getLifecyclePolicy(@PathVariable UUID profileId) {
        return service.getLifecyclePolicy(profileId);
    }

    @PutMapping("/api/v1/profiles/{profileId}/lifecycle")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public LifecyclePolicyResponse setLifecyclePolicy(
            @PathVariable UUID profileId,
            @Valid @RequestBody LifecyclePolicyRequest req) {
        return service.setLifecyclePolicy(profileId, req);
    }

    @DeleteMapping("/api/v1/profiles/{profileId}/lifecycle")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> deleteLifecyclePolicy(@PathVariable UUID profileId) {
        service.deleteLifecyclePolicy(profileId);
        return ResponseEntity.noContent().build();
    }

    // ── Approval Config ───────────────────────────────────────────────────────

    @GetMapping("/api/v1/profiles/{profileId}/approval")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApprovalConfigResponse getApprovalConfig(@PathVariable UUID profileId) {
        return service.getApprovalConfig(profileId);
    }

    @PutMapping("/api/v1/profiles/{profileId}/approval")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApprovalConfigResponse setApprovalConfig(
            @PathVariable UUID profileId,
            @Valid @RequestBody ApprovalConfigRequest req) {
        return service.setApprovalConfig(profileId, req);
    }

    // ── Approvers ─────────────────────────────────────────────────────────────

    @GetMapping("/api/v1/profiles/{profileId}/approvers")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<ProfileApproverResponse> getApprovers(@PathVariable UUID profileId) {
        return service.getApprovers(profileId);
    }

    @PutMapping("/api/v1/profiles/{profileId}/approvers")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<ProfileApproverResponse> setApprovers(
            @PathVariable UUID profileId,
            @Valid @RequestBody SetProfileApproversRequest req) {
        return service.setApprovers(profileId, req.accountIds());
    }

    // ── Superadmin list all profiles ──────────────────────────────────────────

    @GetMapping("/api/v1/profiles")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public List<ProfileResponse> listAll() {
        return service.listAll();
    }
}
