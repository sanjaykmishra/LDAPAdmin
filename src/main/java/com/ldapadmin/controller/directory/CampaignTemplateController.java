package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.DirectoryId;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.dto.accessreview.*;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.service.AccessReviewCampaignService;
import com.ldapadmin.service.CampaignTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/directories/{directoryId}/campaign-templates")
@RequiredArgsConstructor
public class CampaignTemplateController {

    private final CampaignTemplateService templateService;
    private final AccessReviewCampaignService campaignService;

    @PostMapping
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<CampaignTemplateResponse> create(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateCampaignTemplateRequest req) {
        var response = templateService.create(directoryId, req, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public List<CampaignTemplateResponse> list(
            @DirectoryId @PathVariable UUID directoryId) {
        return templateService.list(directoryId);
    }

    @GetMapping("/{templateId}")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    public CampaignTemplateResponse get(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID templateId) {
        return templateService.get(directoryId, templateId);
    }

    @PutMapping("/{templateId}")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    @PreAuthorize("hasRole('SUPERADMIN')")
    public CampaignTemplateResponse update(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID templateId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateCampaignTemplateRequest req) {
        return templateService.update(directoryId, templateId, req, principal);
    }

    @DeleteMapping("/{templateId}")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Void> delete(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID templateId) {
        templateService.delete(directoryId, templateId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{templateId}/create-campaign")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<CampaignDetailDto> createCampaignFromTemplate(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID templateId,
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description) {
        CreateCampaignRequest req = templateService.toCampaignRequest(directoryId, templateId, name, description);
        var campaign = campaignService.create(directoryId, req, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.get(directoryId, campaign.getId()));
    }

    @PostMapping("/from-campaign/{campaignId}")
    @RequiresFeature(FeatureKey.ACCESS_REVIEW_MANAGE)
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<CampaignTemplateResponse> saveAsTemplate(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable UUID campaignId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        var response = templateService.createFromCampaign(directoryId, campaignId, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
