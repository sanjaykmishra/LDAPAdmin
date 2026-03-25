package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.DirectoryId;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.dto.accessreview.*;
import com.ldapadmin.dto.admin.AdminAccountResponse;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.service.AccessReviewCampaignService;
import com.ldapadmin.service.AccessReviewDecisionService;
import com.ldapadmin.service.AdminManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/directories/{directoryId}/access-reviews")
@RequiredArgsConstructor
public class AccessReviewController {

    private final AccessReviewCampaignService campaignService;
    private final AccessReviewDecisionService decisionService;
    private final AdminManagementService adminService;

    // ── Reviewer lookup ─────────────────────────────────────────────────────

    @GetMapping("/reviewers")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public List<AdminAccountResponse> listReviewers(
            @DirectoryId @PathVariable UUID directoryId) {
        return adminService.listAdminsByDirectory(directoryId);
    }

    // ── Campaign CRUD ────────────────────────────────────────────────────────

    @GetMapping
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public Page<CampaignSummaryDto> list(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return campaignService.list(directoryId, pageable);
    }

    @PostMapping
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<CampaignDetailDto> create(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateCampaignRequest req) {
        var campaign = campaignService.create(directoryId, req, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.get(directoryId, campaign.getId()));
    }

    @GetMapping("/{campaignId}")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public CampaignDetailDto get(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return campaignService.get(directoryId, campaignId);
    }

    @PostMapping("/{campaignId}/activate")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public CampaignDetailDto activate(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        campaignService.activate(directoryId, campaignId, principal);
        return campaignService.get(directoryId, campaignId);
    }

    @PostMapping("/{campaignId}/close")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public CampaignDetailDto close(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "false") boolean force) {
        campaignService.close(directoryId, campaignId, force, principal);
        return campaignService.get(directoryId, campaignId);
    }

    @PostMapping("/{campaignId}/cancel")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public CampaignDetailDto cancel(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        campaignService.cancel(directoryId, campaignId, principal);
        return campaignService.get(directoryId, campaignId);
    }

    // ── Review groups & decisions ────────────────────────────────────────────

    @GetMapping("/{campaignId}/groups")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_REVIEW)
    public List<ReviewGroupDto> listGroups(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return campaignService.listReviewGroups(directoryId, campaignId, principal);
    }

    @GetMapping("/{campaignId}/groups/{groupId}/decisions")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_REVIEW)
    public List<DecisionDto> listDecisions(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID campaignId,
            @PathVariable UUID groupId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return decisionService.listForReviewGroup(groupId, principal);
    }

    @PostMapping("/{campaignId}/groups/{groupId}/decisions/{decisionId}")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_REVIEW)
    public DecisionDto submitDecision(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID campaignId,
            @PathVariable UUID groupId,
            @PathVariable UUID decisionId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody SubmitDecisionRequest req) {
        return decisionService.decide(decisionId, req.decision(), req.comment(), principal);
    }

    @PostMapping("/{campaignId}/groups/{groupId}/decisions/bulk")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_REVIEW)
    public List<DecisionDto> bulkDecide(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID campaignId,
            @PathVariable UUID groupId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody BulkDecisionRequest req) {
        return decisionService.bulkDecide(groupId, req.items(), principal);
    }

    // ── Export & history ─────────────────────────────────────────────────────

    @GetMapping("/{campaignId}/export")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public ResponseEntity<byte[]> export(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "csv") String format) {
        byte[] data = campaignService.exportCsv(directoryId, campaignId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("access-review-" + campaignId + ".csv").build());
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    @GetMapping("/{campaignId}/reminders")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public List<CampaignReminderDto> listReminders(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return campaignService.listReminders(directoryId, campaignId);
    }

    @GetMapping("/{campaignId}/history")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public List<CampaignHistoryDto> getHistory(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return campaignService.getHistory(directoryId, campaignId);
    }
}
