package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.drift.*;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.*;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccessDriftAnalysisService {

    private final PeerGroupRuleRepository ruleRepo;
    private final AccessSnapshotRepository snapshotRepo;
    private final AccessSnapshotMembershipRepository membershipRepo;
    private final AccessDriftFindingRepository findingRepo;
    private final DirectoryConnectionRepository directoryRepo;
    private final AccountRepository accountRepo;
    private final LdapUserService ldapUserService;
    private final AuditService auditService;

    private static final int MAX_USERS_FOR_ANALYSIS = 50_000;

    // ── Analysis ─────────────────────────────────────────────────────────────

    @Transactional
    public DriftAnalysisResult analyze(UUID directoryId, UUID snapshotId, AuthPrincipal principal) {
        DirectoryConnection dc = directoryRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
        AccessSnapshot snapshot = snapshotRepo.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("AccessSnapshot", snapshotId));

        List<PeerGroupRule> rules = ruleRepo.findByDirectoryIdAndEnabledTrue(directoryId);
        if (rules.isEmpty()) {
            log.info("No enabled peer group rules for directory {} — skipping analysis", directoryId);
            return new DriftAnalysisResult(snapshotId, 0, 0, 0, 0, 0, 0, 0);
        }

        // Load all memberships from snapshot into memory (indexed by user)
        List<AccessSnapshotMembership> allMemberships = membershipRepo.findBySnapshotId(snapshotId);
        Map<String, Set<String>> userToGroups = new HashMap<>();
        Map<String, String> groupDnToName = new HashMap<>();

        for (AccessSnapshotMembership m : allMemberships) {
            userToGroups.computeIfAbsent(m.getUserDn().toLowerCase(), k -> new HashSet<>())
                    .add(m.getGroupDn());
            groupDnToName.putIfAbsent(m.getGroupDn(), m.getGroupName());
        }

        int totalFindings = 0, highFindings = 0, mediumFindings = 0, lowFindings = 0, skipped = 0;
        int peerGroupsAnalyzed = 0;

        for (PeerGroupRule rule : rules) {
            // Query LDAP for the grouping attribute to bucket users
            Map<String, List<String>> peerBuckets = buildPeerBuckets(dc, rule.getGroupingAttribute());

            for (Map.Entry<String, List<String>> bucket : peerBuckets.entrySet()) {
                String peerGroupValue = bucket.getKey();
                List<String> peerUserDns = bucket.getValue();
                if (peerUserDns.size() < 3) continue; // skip tiny peer groups

                peerGroupsAnalyzed++;

                // Compute per-group membership percentages within this peer group
                Map<String, Integer> groupMemberCount = new HashMap<>();
                for (String userDn : peerUserDns) {
                    Set<String> groups = userToGroups.getOrDefault(userDn.toLowerCase(), Set.of());
                    for (String groupDn : groups) {
                        groupMemberCount.merge(groupDn, 1, Integer::sum);
                    }
                }

                int peerGroupSize = peerUserDns.size();

                // Check each user for anomalous memberships
                for (String userDn : peerUserDns) {
                    Set<String> userGroups = userToGroups.getOrDefault(userDn.toLowerCase(), Set.of());

                    for (String groupDn : userGroups) {
                        int memberCount = groupMemberCount.getOrDefault(groupDn, 0);
                        double pct = (memberCount * 100.0) / peerGroupSize;

                        if (pct >= rule.getAnomalyThresholdPct()) continue; // normal enough

                        // Check for existing open finding to avoid duplicates
                        if (!findingRepo.findExisting(rule.getId(), userDn, groupDn, DriftFindingStatus.OPEN).isEmpty()) {
                            skipped++;
                            continue;
                        }
                        // Also skip if exempted
                        if (!findingRepo.findExisting(rule.getId(), userDn, groupDn, DriftFindingStatus.EXEMPTED).isEmpty()) {
                            skipped++;
                            continue;
                        }

                        DriftFindingSeverity severity = computeSeverity(pct, rule.getAnomalyThresholdPct());

                        AccessDriftFinding finding = new AccessDriftFinding();
                        finding.setSnapshot(snapshot);
                        finding.setRule(rule);
                        finding.setUserDn(userDn);
                        finding.setUserDisplay(resolveDisplayName(dc, userDn));
                        finding.setPeerGroupValue(peerGroupValue);
                        finding.setPeerGroupSize(peerGroupSize);
                        finding.setGroupDn(groupDn);
                        finding.setGroupName(groupDnToName.getOrDefault(groupDn, groupDn));
                        finding.setPeerMembershipPct(Math.round(pct * 10) / 10.0);
                        finding.setSeverity(severity);
                        finding.setStatus(DriftFindingStatus.OPEN);
                        finding.setDetectedAt(OffsetDateTime.now());
                        findingRepo.save(finding);

                        totalFindings++;
                        switch (severity) {
                            case HIGH -> highFindings++;
                            case MEDIUM -> mediumFindings++;
                            case LOW -> lowFindings++;
                        }
                    }
                }
            }
        }

        if (principal != null) {
            auditService.record(principal, directoryId, AuditAction.INTEGRITY_CHECK, null,
                    Map.of("operation", "drift_analysis", "snapshotId", snapshotId.toString(),
                            "findings", totalFindings, "high", highFindings));
        }

        log.info("Drift analysis complete for directory {}: {} rules, {} peer groups, {} findings ({} high)",
                dc.getDisplayName(), rules.size(), peerGroupsAnalyzed, totalFindings, highFindings);

        return new DriftAnalysisResult(snapshotId, rules.size(), peerGroupsAnalyzed,
                totalFindings, highFindings, mediumFindings, lowFindings, skipped);
    }

    // ── Findings management ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DriftFindingResponse> getFindings(UUID directoryId, DriftFindingStatus status) {
        List<AccessDriftFinding> findings = status != null
                ? findingRepo.findByDirectoryIdAndStatus(directoryId, status)
                : findingRepo.findByDirectoryId(directoryId);
        return findings.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DriftSummaryResponse getSummary(UUID directoryId) {
        long high = findingRepo.countByDirectoryIdAndStatusAndSeverity(directoryId, DriftFindingStatus.OPEN, DriftFindingSeverity.HIGH);
        long medium = findingRepo.countByDirectoryIdAndStatusAndSeverity(directoryId, DriftFindingStatus.OPEN, DriftFindingSeverity.MEDIUM);
        long low = findingRepo.countByDirectoryIdAndStatusAndSeverity(directoryId, DriftFindingStatus.OPEN, DriftFindingSeverity.LOW);
        OffsetDateTime lastAnalysis = snapshotRepo.findFirstByDirectoryIdOrderByCapturedAtDesc(directoryId)
                .map(AccessSnapshot::getCapturedAt).orElse(null);
        return new DriftSummaryResponse(high, medium, low, high + medium + low, lastAnalysis);
    }

    @Transactional
    public DriftFindingResponse acknowledgeFinding(UUID findingId, AuthPrincipal principal) {
        AccessDriftFinding f = findingRepo.findById(findingId)
                .orElseThrow(() -> new ResourceNotFoundException("AccessDriftFinding", findingId));
        Account actor = accountRepo.findById(principal.id()).orElse(null);
        f.setStatus(DriftFindingStatus.ACKNOWLEDGED);
        f.setAcknowledgedBy(actor);
        f.setAcknowledgedAt(OffsetDateTime.now());
        findingRepo.save(f);
        return toResponse(f);
    }

    @Transactional
    public DriftFindingResponse exemptFinding(UUID findingId, String reason, AuthPrincipal principal) {
        AccessDriftFinding f = findingRepo.findById(findingId)
                .orElseThrow(() -> new ResourceNotFoundException("AccessDriftFinding", findingId));
        Account actor = accountRepo.findById(principal.id()).orElse(null);
        f.setStatus(DriftFindingStatus.EXEMPTED);
        f.setAcknowledgedBy(actor);
        f.setAcknowledgedAt(OffsetDateTime.now());
        f.setExemptionReason(reason);
        findingRepo.save(f);
        return toResponse(f);
    }

    // ── Rule CRUD ────────────────────────────────────────────────────────────

    @Transactional
    public PeerGroupRuleResponse createRule(UUID directoryId, PeerGroupRuleRequest req, AuthPrincipal principal) {
        DirectoryConnection dir = directoryRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
        Account creator = accountRepo.findById(principal.id()).orElse(null);

        PeerGroupRule rule = new PeerGroupRule();
        rule.setDirectory(dir);
        rule.setName(req.name());
        rule.setGroupingAttribute(req.groupingAttribute());
        rule.setNormalThresholdPct(req.normalThresholdPct());
        rule.setAnomalyThresholdPct(req.anomalyThresholdPct());
        rule.setEnabled(req.enabled());
        rule.setCreatedBy(creator);
        ruleRepo.save(rule);
        return toRuleResponse(rule);
    }

    @Transactional
    public PeerGroupRuleResponse updateRule(UUID directoryId, UUID ruleId, PeerGroupRuleRequest req) {
        PeerGroupRule rule = getRuleForDirectory(directoryId, ruleId);
        rule.setName(req.name());
        rule.setGroupingAttribute(req.groupingAttribute());
        rule.setNormalThresholdPct(req.normalThresholdPct());
        rule.setAnomalyThresholdPct(req.anomalyThresholdPct());
        rule.setEnabled(req.enabled());
        ruleRepo.save(rule);
        return toRuleResponse(rule);
    }

    @Transactional
    public void deleteRule(UUID directoryId, UUID ruleId) {
        PeerGroupRule rule = getRuleForDirectory(directoryId, ruleId);
        ruleRepo.delete(rule);
    }

    @Transactional(readOnly = true)
    public List<PeerGroupRuleResponse> listRules(UUID directoryId) {
        return ruleRepo.findByDirectoryIdOrderByCreatedAtDesc(directoryId).stream()
                .map(this::toRuleResponse).toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Map<String, List<String>> buildPeerBuckets(DirectoryConnection dc, String groupingAttribute) {
        Map<String, List<String>> buckets = new HashMap<>();
        try {
            String filter = "(" + groupingAttribute + "=*)";
            List<LdapUser> users = ldapUserService.searchUsers(dc, filter, null, MAX_USERS_FOR_ANALYSIS,
                    "dn", groupingAttribute);
            for (LdapUser user : users) {
                String value = user.getFirstValue(groupingAttribute);
                if (value != null && !value.isBlank()) {
                    buckets.computeIfAbsent(value, k -> new ArrayList<>()).add(user.getDn());
                }
            }
        } catch (Exception e) {
            log.error("Failed to build peer buckets for attribute '{}': {}", groupingAttribute, e.getMessage());
        }
        return buckets;
    }

    private DriftFindingSeverity computeSeverity(double pct, int anomalyThreshold) {
        if (pct <= 5.0) return DriftFindingSeverity.HIGH;
        if (pct < anomalyThreshold) return DriftFindingSeverity.MEDIUM;
        return DriftFindingSeverity.LOW;
    }

    private String resolveDisplayName(DirectoryConnection dc, String userDn) {
        try {
            LdapUser user = ldapUserService.getUser(dc, userDn, "cn", "displayName");
            String display = user.getFirstValue("displayName");
            if (display == null) display = user.getCn();
            return display != null ? display : userDn;
        } catch (Exception e) {
            return userDn;
        }
    }

    private PeerGroupRule getRuleForDirectory(UUID directoryId, UUID ruleId) {
        PeerGroupRule rule = ruleRepo.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("PeerGroupRule", ruleId));
        if (!rule.getDirectory().getId().equals(directoryId)) {
            throw new ResourceNotFoundException("PeerGroupRule", ruleId);
        }
        return rule;
    }

    private DriftFindingResponse toResponse(AccessDriftFinding f) {
        return new DriftFindingResponse(
                f.getId(), f.getUserDn(), f.getUserDisplay(),
                f.getPeerGroupValue(), f.getPeerGroupSize(),
                f.getGroupDn(), f.getGroupName(),
                f.getPeerMembershipPct(), f.getSeverity(), f.getStatus(),
                f.getRule().getName(),
                f.getAcknowledgedBy() != null ? f.getAcknowledgedBy().getUsername() : null,
                f.getAcknowledgedAt(), f.getExemptionReason(), f.getDetectedAt());
    }

    private PeerGroupRuleResponse toRuleResponse(PeerGroupRule r) {
        return new PeerGroupRuleResponse(
                r.getId(), r.getName(), r.getGroupingAttribute(),
                r.getNormalThresholdPct(), r.getAnomalyThresholdPct(), r.isEnabled(),
                r.getCreatedBy() != null ? r.getCreatedBy().getUsername() : null,
                r.getCreatedAt());
    }
}
