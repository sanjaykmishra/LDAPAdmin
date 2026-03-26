package com.ldapadmin.service;

import com.ldapadmin.dto.audit.AuditEventResponse;
import com.ldapadmin.entity.*;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapGroup;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Provides scoped, read-only data queries for the auditor portal.
 * Each method respects the link's scope (campaigns, SoD, entitlements,
 * audit events) and time bounds ({@code dataFrom}/{@code dataTo}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditorPortalService {

    private final AccessReviewCampaignRepository campaignRepo;
    private final AccessReviewCampaignHistoryRepository historyRepo;
    private final AccessReviewDecisionRepository decisionRepo;
    private final SodPolicyRepository sodPolicyRepo;
    private final SodViolationRepository sodViolationRepo;
    private final PendingApprovalRepository approvalRepo;
    private final AuditQueryService auditQueryService;
    private final LdapUserService ldapUserService;
    private final LdapGroupService ldapGroupService;
    private final AccountRepository accountRepo;
    private final DirectoryConnectionRepository directoryRepo;

    private static final String USER_OBJECTCLASS_FILTER =
            "(|(objectClass=inetOrgPerson)(&(objectClass=user)(!(objectClass=computer))))";
    private static final String GROUP_OBJECTCLASS_FILTER =
            "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup)(objectClass=group)(objectClass=groupOfURLs))";
    private static final int MAX_ENTITLEMENT_ENTRIES = 50_000;

    /**
     * Returns campaign summaries for the scoped campaign IDs.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getCampaigns(AuditorLink link) {
        List<Map<String, Object>> result = new ArrayList<>();
        UUID directoryId = link.getDirectory().getId();

        for (UUID campaignId : link.getCampaignIds()) {
            campaignRepo.findById(campaignId).ifPresent(campaign -> {
                if (campaign.getDirectory().getId().equals(directoryId)) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", campaign.getId().toString());
                    entry.put("name", campaign.getName());
                    entry.put("description", campaign.getDescription());
                    entry.put("status", campaign.getStatus() != null ? campaign.getStatus().name() : null);
                    entry.put("startsAt", fmt(campaign.getStartsAt()));
                    entry.put("deadline", fmt(campaign.getDeadline()));
                    entry.put("completedAt", fmt(campaign.getCompletedAt()));
                    entry.put("createdBy", campaign.getCreatedBy() != null
                            ? campaign.getCreatedBy().getUsername() : null);

                    // Decision summary counts
                    long total = decisionRepo.countTotalByCampaignId(campaignId);
                    long pending = decisionRepo.countPendingByCampaignId(campaignId);
                    entry.put("totalDecisions", total);
                    entry.put("completedDecisions", total - pending);

                    result.add(entry);
                }
            });
        }
        return result;
    }

    /**
     * Returns full campaign detail including all decisions.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCampaignDetail(AuditorLink link, UUID campaignId) {
        if (!link.getCampaignIds().contains(campaignId)) {
            return null;
        }

        return campaignRepo.findById(campaignId).map(campaign -> {
            if (!campaign.getDirectory().getId().equals(link.getDirectory().getId())) {
                return null;
            }

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("id", campaign.getId().toString());
            detail.put("name", campaign.getName());
            detail.put("status", campaign.getStatus() != null ? campaign.getStatus().name() : null);
            detail.put("startsAt", fmt(campaign.getStartsAt()));
            detail.put("deadline", fmt(campaign.getDeadline()));
            detail.put("completedAt", fmt(campaign.getCompletedAt()));

            // Decisions
            var decisions = decisionRepo.findByCampaignId(campaignId);
            List<Map<String, Object>> decisionList = decisions.stream().map(d -> {
                Map<String, Object> de = new LinkedHashMap<>();
                de.put("id", d.getId().toString());
                de.put("memberDn", d.getMemberDn());
                de.put("memberDisplayName", d.getMemberDisplay());
                de.put("decision", d.getDecision() != null ? d.getDecision().name() : "PENDING");
                de.put("decidedBy", d.getDecidedBy() != null ? d.getDecidedBy().getUsername() : null);
                de.put("decidedAt", fmt(d.getDecidedAt()));
                return de;
            }).toList();
            detail.put("decisions", decisionList);

            // History
            var history = historyRepo.findByCampaignIdOrderByChangedAtAsc(campaignId);
            List<Map<String, Object>> historyList = history.stream().map(h -> {
                Map<String, Object> he = new LinkedHashMap<>();
                he.put("oldStatus", h.getOldStatus() != null ? h.getOldStatus().name() : null);
                he.put("newStatus", h.getNewStatus() != null ? h.getNewStatus().name() : null);
                he.put("changedBy", h.getChangedBy() != null ? h.getChangedBy().getUsername() : null);
                he.put("changedAt", fmt(h.getChangedAt()));
                he.put("note", h.getNote());
                return he;
            }).toList();
            detail.put("history", historyList);

            return detail;
        }).orElse(null);
    }

    /**
     * Returns SoD policies and violations for the directory.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSodData(AuditorLink link) {
        UUID directoryId = link.getDirectory().getId();
        Map<String, Object> result = new LinkedHashMap<>();

        List<SodPolicy> policies = sodPolicyRepo.findByDirectoryId(directoryId);
        result.put("policies", policies.stream().map(p -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", p.getId().toString());
            entry.put("name", p.getName());
            entry.put("description", p.getDescription());
            entry.put("groupAName", p.getGroupAName());
            entry.put("groupBName", p.getGroupBName());
            entry.put("severity", p.getSeverity() != null ? p.getSeverity().name() : null);
            entry.put("action", p.getAction() != null ? p.getAction().name() : null);
            entry.put("enabled", p.isEnabled());
            return entry;
        }).toList());

        List<SodViolation> violations = sodViolationRepo.findByDirectoryId(directoryId);
        result.put("violations", violations.stream().map(v -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", v.getId().toString());
            entry.put("policyName", v.getPolicy() != null ? v.getPolicy().getName() : null);
            entry.put("userDn", v.getUserDn());
            entry.put("userDisplayName", v.getUserDisplayName());
            entry.put("status", v.getStatus() != null ? v.getStatus().name() : null);
            entry.put("detectedAt", fmt(v.getDetectedAt()));
            entry.put("resolvedAt", fmt(v.getResolvedAt()));
            return entry;
        }).toList());

        return result;
    }

    /**
     * Returns user entitlements snapshot from LDAP.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEntitlements(AuditorLink link) {
        DirectoryConnection dc = link.getDirectory();
        try {
            List<LdapUser> users = ldapUserService.searchUsers(dc, USER_OBJECTCLASS_FILTER, null,
                    MAX_ENTITLEMENT_ENTRIES, "cn", "uid", "sAMAccountName", "displayName", "mail", "memberOf");

            List<LdapGroup> groups = ldapGroupService.searchGroups(dc, GROUP_OBJECTCLASS_FILTER,
                    null, MAX_ENTITLEMENT_ENTRIES, "cn", "member", "uniqueMember", "memberUid");

            Map<String, List<String>> userToGroups = new HashMap<>();
            for (LdapGroup group : groups) {
                String groupName = group.getCn() != null ? group.getCn() : group.getDn();
                for (String memberDn : group.getAllMembers()) {
                    userToGroups.computeIfAbsent(memberDn.toLowerCase(), k -> new ArrayList<>())
                            .add(groupName);
                }
            }

            return users.stream().map(u -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("dn", u.getDn());
                entry.put("cn", u.getCn());
                entry.put("loginName", u.getLoginName());
                entry.put("displayName", u.getDisplayName());
                entry.put("mail", u.getMail());
                Set<String> groupNames = new LinkedHashSet<>();
                if (u.getMemberOf() != null) groupNames.addAll(u.getMemberOf());
                List<String> fromGroups = userToGroups.get(u.getDn().toLowerCase());
                if (fromGroups != null) groupNames.addAll(fromGroups);
                entry.put("groups", new ArrayList<>(groupNames));
                return entry;
            }).toList();
        } catch (Exception e) {
            log.warn("Failed to fetch entitlements for auditor portal: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Returns audit events scoped to the link's time bounds.
     */
    @Transactional(readOnly = true)
    public List<AuditEventResponse> getAuditEvents(AuditorLink link) {
        UUID directoryId = link.getDirectory().getId();
        OffsetDateTime from = link.getDataFrom() != null
                ? link.getDataFrom() : OffsetDateTime.now().minusDays(90);
        OffsetDateTime to = link.getDataTo();

        Page<AuditEventResponse> events = auditQueryService.query(
                directoryId, null, null, from, to, 0, 10_000);
        return events.getContent();
    }

    /**
     * Returns approval workflow history for the directory.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getApprovals(AuditorLink link) {
        UUID directoryId = link.getDirectory().getId();
        Map<UUID, String> accountNames = new HashMap<>();

        List<PendingApproval> approvals = approvalRepo.findAllByDirectoryIdOrderByCreatedAtDesc(directoryId);
        return approvals.stream().map(a -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", a.getId().toString());
            entry.put("requestType", a.getRequestType() != null ? a.getRequestType().name() : null);
            entry.put("status", a.getStatus() != null ? a.getStatus().name() : null);
            entry.put("requestedBy", resolveAccountName(a.getRequestedBy(), accountNames));
            entry.put("reviewedBy", resolveAccountName(a.getReviewedBy(), accountNames));
            entry.put("createdAt", fmt(a.getCreatedAt()));
            entry.put("reviewedAt", fmt(a.getReviewedAt()));
            return entry;
        }).toList();
    }

    private String resolveAccountName(UUID accountId, Map<UUID, String> cache) {
        if (accountId == null) return null;
        return cache.computeIfAbsent(accountId, id ->
                accountRepo.findById(id).map(Account::getUsername).orElse(id.toString()));
    }

    private static String fmt(OffsetDateTime dt) {
        return dt != null ? dt.toString() : null;
    }
}
