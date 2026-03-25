package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.DirectoryId;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.dto.sod.*;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.entity.enums.SodViolationStatus;
import com.ldapadmin.service.SodPolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/directories/{directoryId}/sod-policies")
@RequiredArgsConstructor
public class SodPolicyController {

    private final SodPolicyService sodPolicyService;

    @PostMapping
    @RequiresFeature(FeatureKey.SOD_MANAGE)
    public ResponseEntity<SodPolicyResponse> create(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateSodPolicyRequest req) {
        var policy = sodPolicyService.create(directoryId, req, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(sodPolicyService.getPolicy(policy.getId()));
    }

    @GetMapping
    @RequiresFeature(FeatureKey.SOD_VIEW)
    public List<SodPolicyResponse> list(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return sodPolicyService.listPolicies(directoryId);
    }

    @GetMapping("/{policyId}")
    @RequiresFeature(FeatureKey.SOD_VIEW)
    public SodPolicyResponse get(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID policyId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return sodPolicyService.getPolicy(policyId);
    }

    @PutMapping("/{policyId}")
    @RequiresFeature(FeatureKey.SOD_MANAGE)
    public SodPolicyResponse update(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID policyId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateSodPolicyRequest req) {
        sodPolicyService.update(policyId, req, principal);
        return sodPolicyService.getPolicy(policyId);
    }

    @DeleteMapping("/{policyId}")
    @RequiresFeature(FeatureKey.SOD_MANAGE)
    public ResponseEntity<Void> delete(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID policyId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        sodPolicyService.delete(policyId, principal);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/scan")
    @RequiresFeature(FeatureKey.SOD_MANAGE)
    public SodScanResultDto scan(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return sodPolicyService.scanDirectory(directoryId, principal);
    }

    // ── Violations ──────────────────────────────────────────────────────────

    @GetMapping("/violations")
    @RequiresFeature(FeatureKey.SOD_VIEW)
    public List<SodViolationResponse> listViolations(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) SodViolationStatus status) {
        return sodPolicyService.listViolations(directoryId, status);
    }

    @PostMapping("/violations/{violationId}/exempt")
    @RequiresFeature(FeatureKey.SOD_MANAGE)
    public SodViolationResponse exempt(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID violationId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ExemptViolationRequest req) {
        return sodPolicyService.exemptViolation(violationId, req, principal);
    }

    @PostMapping("/violations/{violationId}/resolve")
    @RequiresFeature(FeatureKey.SOD_MANAGE)
    public SodViolationResponse resolve(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID violationId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return sodPolicyService.resolveViolation(violationId, principal);
    }
}
