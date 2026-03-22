package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.accessreview.BulkDecisionRequest;
import com.ldapadmin.dto.accessreview.DecisionDto;
import com.ldapadmin.entity.AccessReviewDecision;
import com.ldapadmin.entity.AccessReviewGroup;
import com.ldapadmin.entity.Account;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.entity.enums.ReviewDecision;
import com.ldapadmin.exception.LdapAdminException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.repository.AccessReviewDecisionRepository;
import com.ldapadmin.repository.AccessReviewGroupRepository;
import com.ldapadmin.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccessReviewDecisionService {

    private final AccessReviewDecisionRepository decisionRepo;
    private final AccessReviewGroupRepository groupRepo;
    private final AccountRepository accountRepo;
    private final LdapGroupService ldapGroupService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<DecisionDto> listForReviewGroup(UUID reviewGroupId, AuthPrincipal principal) {
        AccessReviewGroup group = groupRepo.findById(reviewGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("AccessReviewGroup", reviewGroupId));

        verifyReviewerAccess(group, principal);

        return decisionRepo.findByReviewGroupId(reviewGroupId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public DecisionDto decide(UUID decisionId, ReviewDecision decision, String comment, AuthPrincipal principal) {
        AccessReviewDecision d = decisionRepo.findById(decisionId)
                .orElseThrow(() -> new ResourceNotFoundException("AccessReviewDecision", decisionId));

        AccessReviewGroup group = d.getReviewGroup();
        verifyReviewerAccess(group, principal);
        verifyCampaignActive(group);

        Account actor = accountRepo.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Account", principal.id()));

        d.setDecision(decision);
        d.setComment(comment);
        d.setDecidedBy(actor);
        d.setDecidedAt(OffsetDateTime.now());

        // Auto-revoke if enabled and decision is REVOKE
        if (decision == ReviewDecision.REVOKE && group.getCampaign().isAutoRevoke()) {
            executeImmediateRevoke(d, group, principal);
        }

        decisionRepo.save(d);

        AuditAction auditAction = decision == ReviewDecision.CONFIRM
                ? AuditAction.REVIEW_CONFIRMED
                : AuditAction.REVIEW_REVOKED;

        auditService.record(principal, group.getCampaign().getDirectory().getId(), auditAction,
                d.getMemberDn(),
                Map.of("groupDn", group.getGroupDn(),
                        "campaignId", group.getCampaign().getId().toString(),
                        "decision", decision.name()));

        return toDto(d);
    }

    @Transactional
    public List<DecisionDto> bulkDecide(UUID reviewGroupId, List<BulkDecisionRequest.BulkDecisionItem> items, AuthPrincipal principal) {
        AccessReviewGroup group = groupRepo.findById(reviewGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("AccessReviewGroup", reviewGroupId));

        verifyReviewerAccess(group, principal);
        verifyCampaignActive(group);

        List<DecisionDto> results = new ArrayList<>();
        for (BulkDecisionRequest.BulkDecisionItem item : items) {
            results.add(decide(item.decisionId(), item.decision(), item.comment(), principal));
        }
        return results;
    }

    private void executeImmediateRevoke(AccessReviewDecision d, AccessReviewGroup group, AuthPrincipal principal) {
        DirectoryConnection dir = group.getCampaign().getDirectory();
        try {
            ldapGroupService.removeMember(dir, group.getGroupDn(), group.getMemberAttribute(), d.getMemberDn());
            d.setRevokedAt(OffsetDateTime.now());
            log.info("Auto-revoked member {} from group {}", d.getMemberDn(), group.getGroupDn());

            auditService.record(principal, dir.getId(), AuditAction.REVIEW_AUTO_REVOKED,
                    d.getMemberDn(),
                    Map.of("groupDn", group.getGroupDn(),
                            "campaignId", group.getCampaign().getId().toString()));
        } catch (Exception e) {
            log.error("Failed to auto-revoke member {} from group {}: {}",
                    d.getMemberDn(), group.getGroupDn(), e.getMessage());
        }
    }

    private void verifyReviewerAccess(AccessReviewGroup group, AuthPrincipal principal) {
        if (principal.isSuperadmin()) return;
        if (!group.getReviewer().getId().equals(principal.id())) {
            throw new AccessDeniedException("Not the assigned reviewer for this group");
        }
    }

    private void verifyCampaignActive(AccessReviewGroup group) {
        if (group.getCampaign().getStatus() != CampaignStatus.ACTIVE) {
            throw new LdapAdminException("Campaign is not active — decisions can only be submitted on active campaigns");
        }
    }

    private DecisionDto toDto(AccessReviewDecision d) {
        return new DecisionDto(
                d.getId(),
                d.getMemberDn(),
                d.getMemberDisplay(),
                d.getDecision(),
                d.getComment(),
                d.getDecidedBy() != null ? d.getDecidedBy().getUsername() : null,
                d.getDecidedAt(),
                d.getRevokedAt());
    }
}
