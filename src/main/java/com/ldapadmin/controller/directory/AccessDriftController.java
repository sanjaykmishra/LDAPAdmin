package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.DirectoryId;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.dto.drift.*;
import com.ldapadmin.entity.AccessSnapshot;
import com.ldapadmin.entity.enums.DriftFindingStatus;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.service.AccessDriftAnalysisService;
import com.ldapadmin.service.AccessSnapshotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/directories/{directoryId}/drift")
@RequiredArgsConstructor
public class AccessDriftController {

    private final AccessDriftAnalysisService analysisService;
    private final AccessSnapshotService snapshotService;

    // ── Rules CRUD ──────────────────────────────────────────────────────────

    @GetMapping("/rules")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public List<PeerGroupRuleResponse> listRules(
            @DirectoryId @PathVariable UUID directoryId) {
        return analysisService.listRules(directoryId);
    }

    @PostMapping("/rules")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public ResponseEntity<PeerGroupRuleResponse> createRule(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PeerGroupRuleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(analysisService.createRule(directoryId, req, principal));
    }

    @PutMapping("/rules/{ruleId}")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public PeerGroupRuleResponse updateRule(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody PeerGroupRuleRequest req) {
        return analysisService.updateRule(directoryId, ruleId, req);
    }

    @DeleteMapping("/rules/{ruleId}")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public ResponseEntity<Void> deleteRule(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID ruleId) {
        analysisService.deleteRule(directoryId, ruleId);
        return ResponseEntity.noContent().build();
    }

    // ── Analysis ────────────────────────────────────────────────────────────

    @PostMapping("/analyze")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public java.util.Map<String, String> analyze(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        // Run in background thread to avoid HTTP timeout for large directories
        UUID dirId = directoryId;
        AuthPrincipal p = principal;
        new Thread(() -> {
            try {
                AccessSnapshot snapshot = snapshotService.captureSnapshot(dirId);
                analysisService.analyze(dirId, snapshot.getId(), p);
            } catch (Exception e) {
                log.error("Background drift analysis failed for directory {}: {}", dirId, e.getMessage());
            }
        }, "drift-analysis-" + directoryId).start();
        return java.util.Map.of("status", "started", "message", "Analysis running in background. Refresh findings when complete.");
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AccessDriftController.class);

    // ── Findings ────────────────────────────────────────────────────────────

    @GetMapping("/findings")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public List<DriftFindingResponse> listFindings(
            @DirectoryId @PathVariable UUID directoryId,
            @RequestParam(required = false) DriftFindingStatus status) {
        return analysisService.getFindings(directoryId, status);
    }

    @GetMapping("/findings/summary")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public DriftSummaryResponse summary(
            @DirectoryId @PathVariable UUID directoryId) {
        return analysisService.getSummary(directoryId);
    }

    @PostMapping("/findings/{findingId}/acknowledge")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public DriftFindingResponse acknowledge(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID findingId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return analysisService.acknowledgeFinding(findingId, principal);
    }

    @PostMapping("/findings/{findingId}/exempt")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public DriftFindingResponse exempt(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID findingId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody ExemptRequest req) {
        return analysisService.exemptFinding(findingId, req.reason(), principal);
    }

    public record ExemptRequest(@NotBlank String reason) {}

    // ── Snapshots ───────────────────────────────────────────────────────────

    @GetMapping("/snapshots")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public List<SnapshotResponse> listSnapshots(
            @DirectoryId @PathVariable UUID directoryId) {
        return snapshotService.listSnapshots(directoryId).stream()
                .map(s -> new SnapshotResponse(s.getId(), s.getCapturedAt(), s.getStatus().name(),
                        s.getTotalUsers(), s.getTotalGroups(), s.getCompletedAt()))
                .toList();
    }

    record SnapshotResponse(UUID id, java.time.OffsetDateTime capturedAt, String status,
                             Integer totalUsers, Integer totalGroups, java.time.OffsetDateTime completedAt) {}
}
