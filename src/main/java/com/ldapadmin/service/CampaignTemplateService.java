package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.accessreview.*;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.exception.LdapAdminException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.repository.AccessReviewCampaignRepository;
import com.ldapadmin.repository.AccountRepository;
import com.ldapadmin.repository.CampaignTemplateRepository;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CampaignTemplateService {

    private final CampaignTemplateRepository templateRepo;
    private final DirectoryConnectionRepository directoryRepo;
    private final AccountRepository accountRepo;
    private final AccessReviewCampaignRepository campaignRepo;
    private final AuditService auditService;

    @Transactional
    public CampaignTemplateResponse create(UUID directoryId, CreateCampaignTemplateRequest req, AuthPrincipal principal) {
        DirectoryConnection dir = directoryRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
        Account creator = accountRepo.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Account", principal.id()));

        validateConfig(req.deadlineDays(), req.recurrenceMonths());

        CampaignTemplateConfig config = new CampaignTemplateConfig(
                req.deadlineDays(),
                req.recurrenceMonths(),
                req.autoRevoke(),
                req.autoRevokeOnExpiry(),
                req.groups().stream()
                        .map(g -> new CampaignTemplateConfig.GroupConfig(g.groupDn(), g.memberAttribute(), g.reviewerAccountId()))
                        .toList()
        );

        CampaignTemplate template = new CampaignTemplate();
        template.setDirectory(dir);
        template.setName(req.name());
        template.setDescription(req.description());
        template.setConfig(config);
        template.setCreatedBy(creator);

        template = templateRepo.save(template);

        auditService.record(principal, directoryId, AuditAction.CAMPAIGN_CREATED, null,
                Map.of("operation", "template_created", "templateName", req.name(),
                        "templateId", template.getId().toString()));

        log.info("Created campaign template '{}' (id={}) for directory {}", req.name(), template.getId(), directoryId);
        return toResponse(template);
    }

    @Transactional
    public CampaignTemplateResponse update(UUID directoryId, UUID templateId, UpdateCampaignTemplateRequest req, AuthPrincipal principal) {
        CampaignTemplate template = getTemplateOrThrow(templateId, directoryId);

        validateConfig(req.deadlineDays(), req.recurrenceMonths());

        template.setName(req.name());
        template.setDescription(req.description());

        CampaignTemplateConfig config = new CampaignTemplateConfig(
                req.deadlineDays(),
                req.recurrenceMonths(),
                req.autoRevoke(),
                req.autoRevokeOnExpiry(),
                req.groups().stream()
                        .map(g -> new CampaignTemplateConfig.GroupConfig(g.groupDn(), g.memberAttribute(), g.reviewerAccountId()))
                        .toList()
        );
        template.setConfig(config);

        template = templateRepo.save(template);

        auditService.record(principal, directoryId, AuditAction.CAMPAIGN_CREATED, null,
                Map.of("operation", "template_updated", "templateName", req.name(),
                        "templateId", templateId.toString()));

        log.info("Updated campaign template '{}' (id={})", req.name(), templateId);
        return toResponse(template);
    }

    @Transactional(readOnly = true)
    public List<CampaignTemplateResponse> list(UUID directoryId) {
        return templateRepo.findByDirectoryIdOrderByCreatedAtDesc(directoryId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CampaignTemplateResponse get(UUID directoryId, UUID templateId) {
        return toResponse(getTemplateOrThrow(templateId, directoryId));
    }

    @Transactional
    public void delete(UUID directoryId, UUID templateId, AuthPrincipal principal) {
        CampaignTemplate template = getTemplateOrThrow(templateId, directoryId);
        String name = template.getName();
        templateRepo.delete(template);

        auditService.record(principal, directoryId, AuditAction.CAMPAIGN_CREATED, null,
                Map.of("operation", "template_deleted", "templateName", name,
                        "templateId", templateId.toString()));

        log.info("Deleted campaign template '{}' (id={})", name, templateId);
    }

    @Transactional
    public CampaignTemplateResponse createFromCampaign(UUID directoryId, UUID campaignId, AuthPrincipal principal) {
        AccessReviewCampaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("AccessReviewCampaign", campaignId));

        if (!campaign.getDirectory().getId().equals(directoryId)) {
            throw new LdapAdminException("Campaign does not belong to this directory");
        }

        List<CampaignTemplateConfig.GroupConfig> groups = campaign.getReviewGroups().stream()
                .map(g -> new CampaignTemplateConfig.GroupConfig(
                        g.getGroupDn(),
                        g.getMemberAttribute(),
                        g.getReviewer().getId()))
                .toList();

        var req = new CreateCampaignTemplateRequest(
                campaign.getName() + " (Template)",
                campaign.getDescription(),
                campaign.getDeadlineDays() != null ? campaign.getDeadlineDays() : 30,
                campaign.getRecurrenceMonths(),
                campaign.isAutoRevoke(),
                campaign.isAutoRevokeOnExpiry(),
                groups.stream()
                        .map(g -> new CreateCampaignTemplateRequest.GroupConfig(
                                g.getGroupDn(), g.getMemberAttribute(), g.getReviewerAccountId()))
                        .toList()
        );

        return create(directoryId, req, principal);
    }

    /**
     * Duplicates an existing template with a new name.
     */
    @Transactional
    public CampaignTemplateResponse duplicate(UUID directoryId, UUID templateId, AuthPrincipal principal) {
        CampaignTemplate source = getTemplateOrThrow(templateId, directoryId);
        CampaignTemplateConfig cfg = source.getConfig();

        var req = new CreateCampaignTemplateRequest(
                source.getName() + " (Copy)",
                source.getDescription(),
                cfg.getDeadlineDays(),
                cfg.getRecurrenceMonths(),
                cfg.isAutoRevoke(),
                cfg.isAutoRevokeOnExpiry(),
                cfg.getGroups().stream()
                        .map(g -> new CreateCampaignTemplateRequest.GroupConfig(
                                g.getGroupDn(), g.getMemberAttribute(), g.getReviewerAccountId()))
                        .toList()
        );

        return create(directoryId, req, principal);
    }

    @Transactional(readOnly = true)
    public CreateCampaignRequest toCampaignRequest(UUID directoryId, UUID templateId, String nameOverride, String descriptionOverride) {
        CampaignTemplate template = getTemplateOrThrow(templateId, directoryId);
        CampaignTemplateConfig config = template.getConfig();

        String name = nameOverride != null && !nameOverride.isBlank() ? nameOverride : template.getName();
        String description = descriptionOverride != null ? descriptionOverride : template.getDescription();

        // Validate that all referenced reviewers still exist
        for (CampaignTemplateConfig.GroupConfig g : config.getGroups()) {
            if (!accountRepo.existsById(g.getReviewerAccountId())) {
                throw new LdapAdminException("Reviewer account " + g.getReviewerAccountId()
                        + " referenced in template no longer exists. Please update the template.");
            }
        }

        List<CreateCampaignRequest.GroupAssignment> groups = config.getGroups().stream()
                .map(g -> new CreateCampaignRequest.GroupAssignment(
                        g.getGroupDn(),
                        g.getMemberAttribute(),
                        g.getReviewerAccountId()))
                .toList();

        return new CreateCampaignRequest(
                name, description,
                config.getDeadlineDays(),
                config.getRecurrenceMonths(),
                config.isAutoRevoke(),
                config.isAutoRevokeOnExpiry(),
                groups
        );
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private void validateConfig(Integer deadlineDays, Integer recurrenceMonths) {
        if (deadlineDays == null || deadlineDays < 1) {
            throw new LdapAdminException("Deadline must be at least 1 day");
        }
        if (recurrenceMonths != null && recurrenceMonths < 1) {
            throw new LdapAdminException("Recurrence interval must be at least 1 month");
        }
    }

    private CampaignTemplate getTemplateOrThrow(UUID templateId, UUID directoryId) {
        CampaignTemplate template = templateRepo.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("CampaignTemplate", templateId));
        if (!template.getDirectory().getId().equals(directoryId)) {
            throw new ResourceNotFoundException("CampaignTemplate", templateId);
        }
        return template;
    }

    private CampaignTemplateResponse toResponse(CampaignTemplate t) {
        CampaignTemplateConfig c = t.getConfig();

        // Resolve reviewer usernames with in-memory cache for this response
        Map<UUID, String> reviewerNames = new HashMap<>();
        for (CampaignTemplateConfig.GroupConfig g : c.getGroups()) {
            reviewerNames.computeIfAbsent(g.getReviewerAccountId(),
                    id -> accountRepo.findById(id).map(Account::getUsername).orElse(null));
        }

        var groupDtos = c.getGroups().stream()
                .map(g -> new CampaignTemplateResponse.GroupConfigDto(
                        g.getGroupDn(), g.getMemberAttribute(), g.getReviewerAccountId(),
                        reviewerNames.get(g.getReviewerAccountId())))
                .toList();

        var configDto = new CampaignTemplateResponse.CampaignTemplateConfigDto(
                c.getDeadlineDays(), c.getRecurrenceMonths(),
                c.isAutoRevoke(), c.isAutoRevokeOnExpiry(), groupDtos);

        return new CampaignTemplateResponse(
                t.getId(), t.getName(), t.getDescription(),
                t.getDirectory().getId(), configDto,
                t.getCreatedBy().getUsername(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}
