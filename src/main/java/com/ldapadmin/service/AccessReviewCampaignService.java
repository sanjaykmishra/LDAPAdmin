package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.accessreview.*;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.*;
import com.ldapadmin.exception.LdapAdminException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccessReviewCampaignService {

    private final AccessReviewCampaignRepository campaignRepo;
    private final AccessReviewGroupRepository groupRepo;
    private final AccessReviewDecisionRepository decisionRepo;
    private final AccessReviewCampaignHistoryRepository historyRepo;
    private final LdapGroupService ldapGroupService;
    private final LdapUserService ldapUserService;
    private final DirectoryConnectionRepository directoryRepo;
    private final AccountRepository accountRepo;
    private final AuditService auditService;
    private final AccessReviewNotificationService notificationService;

    @Transactional
    public AccessReviewCampaign create(UUID directoryId, CreateCampaignRequest req, AuthPrincipal principal) {
        DirectoryConnection dir = directoryRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));

        if (req.deadline().isBefore(OffsetDateTime.now())) {
            throw new LdapAdminException("Deadline must be in the future");
        }

        Account creator = accountRepo.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Account", principal.id()));

        AccessReviewCampaign campaign = new AccessReviewCampaign();
        campaign.setDirectory(dir);
        campaign.setName(req.name());
        campaign.setDescription(req.description());
        campaign.setStartsAt(req.startsAt());
        campaign.setDeadline(req.deadline());
        campaign.setAutoRevoke(req.autoRevoke());
        campaign.setAutoRevokeOnExpiry(req.autoRevokeOnExpiry());
        campaign.setCreatedBy(creator);
        campaign.setStatus(CampaignStatus.DRAFT);

        for (CreateCampaignRequest.GroupAssignment ga : req.groups()) {
            Account reviewer = accountRepo.findById(ga.reviewerAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account", ga.reviewerAccountId()));

            AccessReviewGroup group = new AccessReviewGroup();
            group.setCampaign(campaign);
            group.setGroupDn(ga.groupDn());
            group.setMemberAttribute(ga.memberAttribute() != null ? ga.memberAttribute() : "member");
            group.setReviewer(reviewer);

            // Attempt to resolve group display name from LDAP
            try {
                var ldapGroup = ldapGroupService.getGroup(dir, ga.groupDn(), "cn");
                group.setGroupName(ldapGroup.getFirstValue("cn"));
            } catch (Exception e) {
                log.warn("Could not resolve group name for {}: {}", ga.groupDn(), e.getMessage());
                group.setGroupName(ga.groupDn());
            }

            campaign.getReviewGroups().add(group);
        }

        campaign = campaignRepo.save(campaign);
        recordHistory(campaign, null, CampaignStatus.DRAFT, creator, "Campaign created");

        auditService.record(principal, directoryId, AuditAction.CAMPAIGN_CREATED,
                null, Map.of("campaignName", req.name(), "campaignId", campaign.getId().toString()));

        return campaign;
    }

    @Transactional
    public AccessReviewCampaign activate(UUID campaignId, AuthPrincipal principal) {
        AccessReviewCampaign campaign = getCampaignOrThrow(campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new LdapAdminException("Can only activate campaigns in DRAFT status");
        }

        DirectoryConnection dir = campaign.getDirectory();
        Account actor = accountRepo.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Account", principal.id()));

        // Snapshot current members for each review group
        for (AccessReviewGroup group : campaign.getReviewGroups()) {
            List<String> members = ldapGroupService.getMembers(dir, group.getGroupDn(), group.getMemberAttribute());
            for (String memberDn : members) {
                AccessReviewDecision decision = new AccessReviewDecision();
                decision.setReviewGroup(group);
                decision.setMemberDn(memberDn);
                decision.setMemberDisplay(resolveMemberDisplay(dir, memberDn));
                group.getDecisions().add(decision);
            }
        }

        CampaignStatus oldStatus = campaign.getStatus();
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaignRepo.save(campaign);

        recordHistory(campaign, oldStatus, CampaignStatus.ACTIVE, actor, "Campaign activated — members snapshot taken");

        // Notify reviewers
        notificationService.notifyReviewersAssigned(campaign);

        auditService.record(principal, dir.getId(), AuditAction.CAMPAIGN_ACTIVATED,
                null, Map.of("campaignId", campaignId.toString(), "campaignName", campaign.getName()));

        return campaign;
    }

    @Transactional
    public AccessReviewCampaign close(UUID campaignId, boolean forceClose, AuthPrincipal principal) {
        AccessReviewCampaign campaign = getCampaignOrThrow(campaignId);
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new LdapAdminException("Can only close campaigns in ACTIVE status");
        }

        long pendingCount = decisionRepo.countPendingByCampaignId(campaignId);
        if (pendingCount > 0 && !forceClose) {
            throw new LdapAdminException("Campaign has " + pendingCount
                    + " undecided items. Use force=true to close anyway.");
        }

        Account actor = accountRepo.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Account", principal.id()));
        DirectoryConnection dir = campaign.getDirectory();

        // Execute auto-revocations if enabled
        if (campaign.isAutoRevoke()) {
            executeRevocations(campaign, dir, principal);
        }

        CampaignStatus oldStatus = campaign.getStatus();
        campaign.setStatus(CampaignStatus.CLOSED);
        campaign.setCompletedAt(OffsetDateTime.now());
        campaignRepo.save(campaign);

        recordHistory(campaign, oldStatus, CampaignStatus.CLOSED, actor,
                forceClose ? "Campaign force-closed with " + pendingCount + " undecided items" : "Campaign closed");

        notificationService.notifyCampaignClosed(campaign);

        auditService.record(principal, dir.getId(), AuditAction.CAMPAIGN_CLOSED,
                null, Map.of("campaignId", campaignId.toString(), "campaignName", campaign.getName()));

        return campaign;
    }

    @Transactional
    public AccessReviewCampaign cancel(UUID campaignId, AuthPrincipal principal) {
        AccessReviewCampaign campaign = getCampaignOrThrow(campaignId);
        if (campaign.getStatus() != CampaignStatus.DRAFT && campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new LdapAdminException("Can only cancel campaigns in DRAFT or ACTIVE status");
        }

        Account actor = accountRepo.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Account", principal.id()));

        CampaignStatus oldStatus = campaign.getStatus();
        campaign.setStatus(CampaignStatus.CANCELLED);
        campaign.setCompletedAt(OffsetDateTime.now());
        campaignRepo.save(campaign);

        recordHistory(campaign, oldStatus, CampaignStatus.CANCELLED, actor, "Campaign cancelled");

        auditService.record(principal, campaign.getDirectory().getId(), AuditAction.CAMPAIGN_CANCELLED,
                null, Map.of("campaignId", campaignId.toString(), "campaignName", campaign.getName()));

        return campaign;
    }

    @Transactional(readOnly = true)
    public Page<CampaignSummaryDto> list(UUID directoryId, Pageable pageable) {
        return campaignRepo.findByDirectoryId(directoryId, pageable)
                .map(this::toSummaryDto);
    }

    @Transactional(readOnly = true)
    public CampaignDetailDto get(UUID campaignId) {
        AccessReviewCampaign campaign = getCampaignOrThrow(campaignId);
        return toDetailDto(campaign);
    }

    @Transactional(readOnly = true)
    public List<ReviewGroupDto> listReviewGroups(UUID campaignId, AuthPrincipal principal) {
        List<AccessReviewGroup> groups;
        if (principal.isSuperadmin()) {
            groups = groupRepo.findByCampaignId(campaignId);
        } else {
            groups = groupRepo.findByCampaignIdAndReviewerId(campaignId, principal.id());
        }
        return groups.stream().map(this::toReviewGroupDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CampaignHistoryDto> getHistory(UUID campaignId) {
        return historyRepo.findByCampaignIdOrderByChangedAtAsc(campaignId).stream()
                .map(h -> new CampaignHistoryDto(
                        h.getId(),
                        h.getOldStatus(),
                        h.getNewStatus(),
                        h.getChangedBy().getUsername(),
                        h.getChangedAt(),
                        h.getNote()))
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(UUID campaignId) {
        AccessReviewCampaign campaign = getCampaignOrThrow(campaignId);
        StringBuilder csv = new StringBuilder();
        csv.append("Campaign,Group DN,Group Name,Member DN,Member Display,Decision,Decided By,Comment,Decided At,Revoked At\n");

        for (AccessReviewGroup group : campaign.getReviewGroups()) {
            List<AccessReviewDecision> decisions = decisionRepo.findByReviewGroupId(group.getId());
            for (AccessReviewDecision d : decisions) {
                csv.append(escapeCsv(campaign.getName())).append(',');
                csv.append(escapeCsv(group.getGroupDn())).append(',');
                csv.append(escapeCsv(group.getGroupName())).append(',');
                csv.append(escapeCsv(d.getMemberDn())).append(',');
                csv.append(escapeCsv(d.getMemberDisplay())).append(',');
                csv.append(d.getDecision() != null ? d.getDecision().name() : "PENDING").append(',');
                csv.append(d.getDecidedBy() != null ? escapeCsv(d.getDecidedBy().getUsername()) : "").append(',');
                csv.append(escapeCsv(d.getComment())).append(',');
                csv.append(d.getDecidedAt() != null ? d.getDecidedAt().toString() : "").append(',');
                csv.append(d.getRevokedAt() != null ? d.getRevokedAt().toString() : "");
                csv.append('\n');
            }
        }
        return csv.toString().getBytes();
    }

    // ── Expiry support (called by scheduler) ────────────────────────────────

    @Transactional
    public void expireCampaign(AccessReviewCampaign campaign, AuthPrincipal systemPrincipal) {
        DirectoryConnection dir = campaign.getDirectory();

        if (campaign.isAutoRevokeOnExpiry()) {
            executeRevocations(campaign, dir, systemPrincipal);
        }

        CampaignStatus oldStatus = campaign.getStatus();
        campaign.setStatus(CampaignStatus.EXPIRED);
        campaign.setCompletedAt(OffsetDateTime.now());
        campaignRepo.save(campaign);

        Account systemAccount = accountRepo.findById(systemPrincipal.id()).orElse(null);
        if (systemAccount != null) {
            recordHistory(campaign, oldStatus, CampaignStatus.EXPIRED, systemAccount, "Campaign expired — deadline passed");
        }

        notificationService.notifyCampaignExpired(campaign);

        auditService.record(systemPrincipal, dir.getId(), AuditAction.CAMPAIGN_EXPIRED,
                null, Map.of("campaignId", campaign.getId().toString(), "campaignName", campaign.getName()));
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private void executeRevocations(AccessReviewCampaign campaign, DirectoryConnection dir, AuthPrincipal principal) {
        List<AccessReviewDecision> revokedDecisions = decisionRepo.findByCampaignIdAndDecision(
                campaign.getId(), ReviewDecision.REVOKE);

        for (AccessReviewDecision d : revokedDecisions) {
            if (d.getRevokedAt() != null) continue; // already revoked

            try {
                ldapGroupService.removeMember(dir,
                        d.getReviewGroup().getGroupDn(),
                        d.getReviewGroup().getMemberAttribute(),
                        d.getMemberDn());
                d.setRevokedAt(OffsetDateTime.now());
                decisionRepo.save(d);

                auditService.record(principal, dir.getId(), AuditAction.REVIEW_AUTO_REVOKED,
                        d.getMemberDn(),
                        Map.of("groupDn", d.getReviewGroup().getGroupDn(),
                                "campaignId", campaign.getId().toString()));
            } catch (Exception e) {
                log.error("Failed to auto-revoke member {} from group {}: {}",
                        d.getMemberDn(), d.getReviewGroup().getGroupDn(), e.getMessage());
            }
        }
    }

    private String resolveMemberDisplay(DirectoryConnection dir, String memberDn) {
        try {
            LdapUser user = ldapUserService.getUser(dir, memberDn, "cn", "displayName");
            String display = user.getFirstValue("displayName");
            if (display == null) display = user.getCn();
            return display != null ? display : memberDn;
        } catch (Exception e) {
            log.debug("Could not resolve display name for {}: {}", memberDn, e.getMessage());
            return memberDn;
        }
    }

    private void recordHistory(AccessReviewCampaign campaign, CampaignStatus oldStatus,
                               CampaignStatus newStatus, Account changedBy, String note) {
        AccessReviewCampaignHistory history = new AccessReviewCampaignHistory();
        history.setCampaign(campaign);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(changedBy);
        history.setNote(note);
        historyRepo.save(history);
    }

    private AccessReviewCampaign getCampaignOrThrow(UUID campaignId) {
        return campaignRepo.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("AccessReviewCampaign", campaignId));
    }

    private CampaignProgressDto buildProgress(UUID campaignId) {
        long total = decisionRepo.countTotalByCampaignId(campaignId);
        long confirmed = decisionRepo.countByCampaignIdAndDecision(campaignId, ReviewDecision.CONFIRM);
        long revoked = decisionRepo.countByCampaignIdAndDecision(campaignId, ReviewDecision.REVOKE);
        long pending = total - confirmed - revoked;
        double pct = total > 0 ? ((double) (confirmed + revoked) / total) * 100.0 : 0.0;
        return new CampaignProgressDto(total, confirmed, revoked, pending, pct);
    }

    private CampaignSummaryDto toSummaryDto(AccessReviewCampaign c) {
        return new CampaignSummaryDto(
                c.getId(), c.getName(), c.getStatus(),
                c.getStartsAt(), c.getDeadline(), c.getCreatedAt(),
                c.getCreatedBy().getUsername(),
                buildProgress(c.getId()));
    }

    private CampaignDetailDto toDetailDto(AccessReviewCampaign c) {
        List<ReviewGroupDto> groups = c.getReviewGroups().stream()
                .map(this::toReviewGroupDto).toList();
        List<CampaignHistoryDto> history = getHistory(c.getId());
        return new CampaignDetailDto(
                c.getId(), c.getName(), c.getDescription(), c.getStatus(),
                c.getStartsAt(), c.getDeadline(),
                c.isAutoRevoke(), c.isAutoRevokeOnExpiry(),
                c.getCreatedAt(), c.getCompletedAt(),
                c.getCreatedBy().getUsername(),
                buildProgress(c.getId()),
                groups, history);
    }

    private ReviewGroupDto toReviewGroupDto(AccessReviewGroup g) {
        List<AccessReviewDecision> decisions = decisionRepo.findByReviewGroupId(g.getId());
        long total = decisions.size();
        long confirmed = decisions.stream().filter(d -> d.getDecision() == ReviewDecision.CONFIRM).count();
        long revoked = decisions.stream().filter(d -> d.getDecision() == ReviewDecision.REVOKE).count();
        long pending = total - confirmed - revoked;
        return new ReviewGroupDto(
                g.getId(), g.getGroupDn(), g.getGroupName(), g.getMemberAttribute(),
                g.getReviewer().getUsername(), g.getReviewer().getId(),
                total, confirmed, revoked, pending);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
