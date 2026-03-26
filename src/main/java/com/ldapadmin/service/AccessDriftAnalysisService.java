package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.drift.*;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.*;
import com.ldapadmin.exception.LdapAdminException;
import com.ldapadmin.exception.ResourceNotFoundException;
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
    private final AccessSnapshotUserRepository userRepo;
    private final AccessDriftFindingRepository findingRepo;
    private final DirectoryConnectionRepository directoryRepo;
    private final AccountRepository accountRepo;
    private final AuditService auditService;

    // ── Analysis ─────────────────────────────────────────────────────────────

    @Transactional
    public DriftAnalysisResult analyze(UUID directoryId, UUID snapshotId, AuthPrincipal principal) {
        directoryRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
        AccessSnapshot snapshot = snapshotRepo.findById(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("AccessSnapshot", snapshotId));

        // Verify snapshot belongs to directory
        if (!snapshot.getDirectory().getId().equals(directoryId)) {
            throw new ResourceNotFoundException("AccessSnapshot", snapshotId);
        }

        List<PeerGroupRule> rules = ruleRepo.findByDirectoryIdAndEnabledTrue(directoryId);
        if (rules.isEmpty()) {
            log.info("No enabled peer group rules for directory {} — skipping analysis", directoryId);
            return new DriftAnalysisResult(snapshotId, 0, 0, 0, 0, 0, 0, 0);
        }

        // Load all memberships from snapshot into memory (indexed by lowercase user DN)
        List<AccessSnapshotMembership> allMemberships = membershipRepo.findBySnapshotId(snapshotId);
        Map<String, Set<String>> userToGroups = new HashMap<>();
        Map<String, String> groupDnToName = new HashMap<>();

        for (AccessSnapshotMembership m : allMemberships) {
            userToGroups.computeIfAbsent(m.getUserDn().toLowerCase(), k -> new HashSet<>())
                    .add(m.getGroupDn().toLowerCase());
            groupDnToName.putIfAbsent(m.getGroupDn().toLowerCase(), m.getGroupName());
        }

        // Load user attributes from snapshot for peer bucketing (fix #1: no live LDAP)
        List<AccessSnapshotUser> snapshotUsers = userRepo.findBySnapshotId(snapshotId);
        Map<String, AccessSnapshotUser> userAttrMap = new HashMap<>();
        for (AccessSnapshotUser su : snapshotUsers) {
            userAttrMap.put(su.getUserDn().toLowerCase(), su);
        }

        int totalFindings = 0, highFindings = 0, mediumFindings = 0, lowFindings = 0, skipped = 0;
        int peerGroupsAnalyzed = 0;
        Set<String> resolvedFindingIds = new HashSet<>();

        for (PeerGroupRule rule : rules) {
            // Build peer buckets from snapshot user attributes (not live LDAP)
            Map<String, List<String>> peerBuckets = buildPeerBucketsFromSnapshot(
                    snapshotUsers, rule.getGroupingAttribute());

            for (Map.Entry<String, List<String>> bucket : peerBuckets.entrySet()) {
                String peerGroupValue = bucket.getKey();
                List<String> peerUserDns = bucket.getValue();
                if (peerUserDns.size() < 3) continue;

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

                for (String userDn : peerUserDns) {
                    Set<String> userGroups = userToGroups.getOrDefault(userDn.toLowerCase(), Set.of());

                    for (String groupDn : userGroups) {
                        int memberCount = groupMemberCount.getOrDefault(groupDn, 0);
                        double pct = (memberCount * 100.0) / peerGroupSize;

                        if (pct >= rule.getAnomalyThresholdPct()) continue;

                        // Check for existing open/exempted findings (case-insensitive)
                        if (!findingRepo.findExisting(rule.getId(), userDn, groupDn, DriftFindingStatus.OPEN).isEmpty()) {
                            skipped++;
                            continue;
                        }
                        if (!findingRepo.findExisting(rule.getId(), userDn, groupDn, DriftFindingStatus.EXEMPTED).isEmpty()) {
                            skipped++;
                            continue;
                        }

                        DriftFindingSeverity severity = computeSeverity(pct, rule.getAnomalyThresholdPct());

                        // Resolve display name from snapshot (fix #2: no LDAP during analysis)
                        AccessSnapshotUser su = userAttrMap.get(userDn.toLowerCase());
                        String displayName = su != null && su.getDisplayName() != null ? su.getDisplayName() : userDn;

                        AccessDriftFinding finding = new AccessDriftFinding();
                        finding.setSnapshot(snapshot);
                        finding.setRule(rule);
                        finding.setUserDn(userDn);
                        finding.setUserDisplay(displayName);
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

            // Auto-resolve: mark OPEN findings that are no longer detected (fix #8)
            autoResolveFindings(rule, peerBuckets, userToGroups);
        }

        if (principal != null) {
            auditService.record(principal, directoryId, AuditAction.INTEGRITY_CHECK, null,
                    Map.of("operation", "drift_analysis", "snapshotId", snapshotId.toString(),
                            "findings", totalFindings, "high", highFindings));
        }

        log.info("Drift analysis complete: {} rules, {} peer groups, {} findings ({} high), {} skipped",
                rules.size(), peerGroupsAnalyzed, totalFindings, highFindings, skipped);

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

        auditService.record(principal, f.getSnapshot().getDirectory().getId(),
                AuditAction.INTEGRITY_CHECK, f.getUserDn(),
                Map.of("operation", "drift_finding_acknowledged", "findingId", findingId.toString(),
                        "groupDn", f.getGroupDn()));

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

        auditService.record(principal, f.getSnapshot().getDirectory().getId(),
                AuditAction.INTEGRITY_CHECK, f.getUserDn(),
                Map.of("operation", "drift_finding_exempted", "findingId", findingId.toString(),
                        "groupDn", f.getGroupDn(), "reason", reason));

        return toResponse(f);
    }

    // ── Rule CRUD ────────────────────────────────────────────────────────────

    @Transactional
    public PeerGroupRuleResponse createRule(UUID directoryId, PeerGroupRuleRequest req, AuthPrincipal principal) {
        DirectoryConnection dir = directoryRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
        validateThresholds(req);
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
        validateThresholds(req);
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

    /**
     * Builds peer buckets from snapshot user attributes instead of live LDAP.
     */
    private Map<String, List<String>> buildPeerBucketsFromSnapshot(
            List<AccessSnapshotUser> snapshotUsers, String groupingAttribute) {
        Map<String, List<String>> buckets = new HashMap<>();
        for (AccessSnapshotUser su : snapshotUsers) {
            String value = switch (groupingAttribute) {
                case "department", "departmentNumber" -> su.getDepartment();
                case "title" -> su.getTitle();
                case "ou" -> su.getOu();
                default -> null;
            };
            if (value != null && !value.isBlank()) {
                buckets.computeIfAbsent(value, k -> new ArrayList<>()).add(su.getUserDn());
            }
        }
        return buckets;
    }

    /**
     * Auto-resolves OPEN findings that are no longer anomalous in the current analysis.
     */
    private void autoResolveFindings(PeerGroupRule rule, Map<String, List<String>> peerBuckets,
                                      Map<String, Set<String>> userToGroups) {
        List<AccessDriftFinding> openFindings = findingRepo.findByDirectoryIdAndStatus(
                rule.getDirectory().getId(), DriftFindingStatus.OPEN);

        for (AccessDriftFinding f : openFindings) {
            if (!f.getRule().getId().equals(rule.getId())) continue;

            // Check if user is still in the group
            Set<String> currentGroups = userToGroups.getOrDefault(f.getUserDn().toLowerCase(), Set.of());
            if (!currentGroups.contains(f.getGroupDn().toLowerCase())) {
                f.setStatus(DriftFindingStatus.RESOLVED);
                f.setAcknowledgedAt(OffsetDateTime.now());
                findingRepo.save(f);
                log.debug("Auto-resolved drift finding: {} no longer in {}", f.getUserDn(), f.getGroupDn());
            }
        }
    }

    private void validateThresholds(PeerGroupRuleRequest req) {
        if (req.anomalyThresholdPct() >= req.normalThresholdPct()) {
            throw new LdapAdminException("Anomaly threshold (" + req.anomalyThresholdPct()
                    + "%) must be less than normal threshold (" + req.normalThresholdPct() + "%)");
        }
    }

    private DriftFindingSeverity computeSeverity(double pct, int anomalyThreshold) {
        if (pct <= 5.0) return DriftFindingSeverity.HIGH;
        if (pct < anomalyThreshold) return DriftFindingSeverity.MEDIUM;
        return DriftFindingSeverity.LOW;
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

    // ── Visualization ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DriftVisualizationResponse buildVisualization(UUID directoryId) {
        // Get latest completed snapshot
        var snapshotOpt = snapshotRepo.findFirstByDirectoryIdOrderByCapturedAtDesc(directoryId);
        if (snapshotOpt.isEmpty()) {
            return new DriftVisualizationResponse(List.of());
        }
        var snapshot = snapshotOpt.get();
        if (snapshot.getStatus() != com.ldapadmin.entity.enums.SnapshotStatus.COMPLETED) {
            return new DriftVisualizationResponse(List.of());
        }

        // Load all users with their peer group attribute (department)
        var snapshotUsers = userRepo.findBySnapshotId(snapshot.getId());
        var memberships = membershipRepo.findBySnapshotId(snapshot.getId());

        // Build user → groups map
        Map<String, List<String>> userGroups = new HashMap<>();
        for (var m : memberships) {
            userGroups.computeIfAbsent(m.getUserDn().toLowerCase(), k -> new ArrayList<>())
                    .add(m.getGroupName() != null ? m.getGroupName() : m.getGroupDn());
        }

        // Group users by peer group (department)
        Map<String, List<AccessSnapshotUser>> peerGroupUsers = new LinkedHashMap<>();
        for (var u : snapshotUsers) {
            String pg = u.getDepartment() != null && !u.getDepartment().isBlank()
                    ? u.getDepartment() : "(No Department)";
            peerGroupUsers.computeIfAbsent(pg, k -> new ArrayList<>()).add(u);
        }

        // Load open drift findings for outlier detection
        var findings = findingRepo.findByDirectoryIdAndStatus(directoryId, DriftFindingStatus.OPEN);
        Map<String, List<AccessDriftFinding>> userFindings = new HashMap<>();
        for (var f : findings) {
            userFindings.computeIfAbsent(f.getUserDn().toLowerCase(), k -> new ArrayList<>()).add(f);
        }

        // Build visualization per peer group
        List<DriftVisualizationResponse.PeerGroupViz> peerGroupVizs = new ArrayList<>();

        for (var entry : peerGroupUsers.entrySet()) {
            String pgName = entry.getKey();
            List<AccessSnapshotUser> users = entry.getValue();
            int userCount = users.size();

            // Count membership percentages per group
            Map<String, Integer> groupMemberCount = new LinkedHashMap<>();
            for (var u : users) {
                var groups = userGroups.getOrDefault(u.getUserDn().toLowerCase(), List.of());
                for (String g : groups) {
                    groupMemberCount.merge(g, 1, Integer::sum);
                }
            }

            // Build group membership list sorted by percentage desc
            List<DriftVisualizationResponse.GroupMembership> groupMemberships = new ArrayList<>();
            for (var ge : groupMemberCount.entrySet()) {
                double pct = userCount > 0 ? (ge.getValue() * 100.0 / userCount) : 0;
                groupMemberships.add(new DriftVisualizationResponse.GroupMembership(ge.getKey(), Math.round(pct * 10) / 10.0));
            }
            groupMemberships.sort((a, b) -> Double.compare(b.membershipPct(), a.membershipPct()));

            // Build outliers list (users with open drift findings)
            List<DriftVisualizationResponse.Outlier> outliers = new ArrayList<>();
            for (var u : users) {
                var uFindings = userFindings.get(u.getUserDn().toLowerCase());
                if (uFindings != null && !uFindings.isEmpty()) {
                    List<String> extraGroups = uFindings.stream()
                            .map(f -> f.getGroupName() != null ? f.getGroupName() : f.getGroupDn())
                            .distinct().toList();
                    String severity = uFindings.stream()
                            .map(f -> f.getSeverity().name())
                            .min(Comparator.comparingInt(s -> switch (s) { case "HIGH" -> 0; case "MEDIUM" -> 1; default -> 2; }))
                            .orElse("LOW");
                    outliers.add(new DriftVisualizationResponse.Outlier(
                            u.getUserDn(), u.getDisplayName(), extraGroups, severity));
                }
            }

            peerGroupVizs.add(new DriftVisualizationResponse.PeerGroupViz(pgName, userCount, groupMemberships, outliers));
        }

        // Sort by user count desc
        peerGroupVizs.sort((a, b) -> Integer.compare(b.userCount(), a.userCount()));

        return new DriftVisualizationResponse(peerGroupVizs);
    }
}
