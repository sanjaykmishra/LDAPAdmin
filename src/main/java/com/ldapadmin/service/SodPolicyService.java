package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.sod.*;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.*;
import com.ldapadmin.exception.ConflictException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.exception.SodViolationException;
import com.ldapadmin.ldap.LdapGroupService;
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
public class SodPolicyService {

    private final SodPolicyRepository policyRepo;
    private final SodViolationRepository violationRepo;
    private final DirectoryConnectionRepository directoryRepo;
    private final AccountRepository accountRepo;
    private final LdapGroupService ldapGroupService;
    private final LdapUserService ldapUserService;
    private final AuditService auditService;

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional
    public SodPolicy create(UUID directoryId, CreateSodPolicyRequest req, AuthPrincipal principal) {
        DirectoryConnection dir = directoryRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
        Account creator = accountRepo.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Account", principal.id()));

        validateGroupPair(req.groupADn(), req.groupBDn());
        if (policyRepo.existsDuplicateGroupPair(directoryId, req.groupADn(), req.groupBDn())) {
            throw new ConflictException("An enabled SoD policy already exists for this group pair");
        }

        SodPolicy policy = new SodPolicy();
        policy.setDirectory(dir);
        policy.setName(req.name());
        policy.setDescription(req.description());
        policy.setGroupADn(req.groupADn());
        policy.setGroupBDn(req.groupBDn());
        policy.setGroupAName(req.groupAName());
        policy.setGroupBName(req.groupBName());
        policy.setSeverity(req.severity());
        policy.setAction(req.action());
        policy.setEnabled(req.enabled());
        policy.setCreatedBy(creator);

        policy = policyRepo.save(policy);

        auditService.record(principal, directoryId, AuditAction.SOD_POLICY_CREATED, null,
                Map.of("policyName", req.name(), "policyId", policy.getId().toString()));

        return policy;
    }

    @Transactional
    public SodPolicy update(UUID policyId, UpdateSodPolicyRequest req, AuthPrincipal principal) {
        SodPolicy policy = getPolicyOrThrow(policyId);

        // Determine effective group DNs after update
        String effectiveGroupA = req.groupADn() != null ? req.groupADn() : policy.getGroupADn();
        String effectiveGroupB = req.groupBDn() != null ? req.groupBDn() : policy.getGroupBDn();

        if (req.groupADn() != null || req.groupBDn() != null) {
            validateGroupPair(effectiveGroupA, effectiveGroupB);
        }

        if (req.name() != null) policy.setName(req.name());
        if (req.description() != null) policy.setDescription(req.description());
        if (req.groupADn() != null) policy.setGroupADn(req.groupADn());
        if (req.groupBDn() != null) policy.setGroupBDn(req.groupBDn());
        if (req.groupAName() != null) policy.setGroupAName(req.groupAName());
        if (req.groupBName() != null) policy.setGroupBName(req.groupBName());
        if (req.severity() != null) policy.setSeverity(req.severity());
        if (req.action() != null) policy.setAction(req.action());
        if (req.enabled() != null) policy.setEnabled(req.enabled());

        policy = policyRepo.save(policy);

        auditService.record(principal, policy.getDirectory().getId(), AuditAction.SOD_POLICY_UPDATED, null,
                Map.of("policyName", policy.getName(), "policyId", policyId.toString()));

        return policy;
    }

    @Transactional
    public void delete(UUID policyId, AuthPrincipal principal) {
        SodPolicy policy = getPolicyOrThrow(policyId);
        UUID directoryId = policy.getDirectory().getId();
        String name = policy.getName();

        // Audit the violation count being cascade-deleted
        long openViolations = violationRepo.countByPolicyIdAndStatus(policyId, SodViolationStatus.OPEN);
        long exemptedViolations = violationRepo.countByPolicyIdAndStatus(policyId, SodViolationStatus.EXEMPTED);

        policyRepo.delete(policy);

        auditService.record(principal, directoryId, AuditAction.SOD_POLICY_DELETED, null,
                Map.of("policyName", name, "policyId", policyId.toString(),
                        "cascadedOpenViolations", String.valueOf(openViolations),
                        "cascadedExemptedViolations", String.valueOf(exemptedViolations)));
    }

    @Transactional(readOnly = true)
    public List<SodPolicyResponse> listPolicies(UUID directoryId) {
        return policyRepo.findByDirectoryId(directoryId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SodPolicyResponse getPolicy(UUID policyId) {
        return toResponse(getPolicyOrThrow(policyId));
    }

    // ── Scan ─────────────────────────────────────────────────────────────────

    @Transactional
    public SodScanResultDto scanDirectory(UUID directoryId, AuthPrincipal principal) {
        DirectoryConnection dir = directoryRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));

        List<SodPolicy> policies = policyRepo.findByDirectoryIdAndEnabledTrue(directoryId);
        int totalViolations = 0;
        int newViolations = 0;
        int resolvedViolations = 0;

        for (SodPolicy policy : policies) {
            try {
                List<String> membersA = ldapGroupService.getMembers(dir, policy.getGroupADn(), "member");
                List<String> membersB = ldapGroupService.getMembers(dir, policy.getGroupBDn(), "member");

                // Case-insensitive intersection using lowercase DN keys
                Set<String> normalizedA = new HashSet<>();
                Map<String, String> originalDnMap = new HashMap<>();
                for (String dn : membersA) {
                    normalizedA.add(dn.toLowerCase());
                    originalDnMap.put(dn.toLowerCase(), dn);
                }
                Set<String> intersectionNormalized = new HashSet<>();
                for (String dn : membersB) {
                    String lower = dn.toLowerCase();
                    if (normalizedA.contains(lower)) {
                        intersectionNormalized.add(lower);
                        originalDnMap.putIfAbsent(lower, dn);
                    }
                }

                // Detect new violations
                for (String normalizedDn : intersectionNormalized) {
                    String userDn = originalDnMap.get(normalizedDn);
                    totalViolations++;

                    // Skip if there's already an OPEN or non-expired EXEMPTED violation
                    if (hasActiveViolation(policy.getId(), userDn)) {
                        continue;
                    }

                    SodViolation v = new SodViolation();
                    v.setPolicy(policy);
                    v.setUserDn(userDn);
                    v.setUserDisplayName(resolveDisplayName(dir, userDn));
                    v.setDetectedAt(OffsetDateTime.now());
                    v.setStatus(SodViolationStatus.OPEN);
                    violationRepo.save(v);
                    newViolations++;

                    auditService.record(principal, directoryId, AuditAction.SOD_VIOLATION_DETECTED, userDn,
                            Map.of("policyName", policy.getName(), "policyId", policy.getId().toString(),
                                    "groupA", policy.getGroupADn(), "groupB", policy.getGroupBDn()));
                }

                // Resolve violations where user is no longer in both groups
                List<SodViolation> openViolations = violationRepo.findByPolicyId(policy.getId()).stream()
                        .filter(v -> v.getStatus() == SodViolationStatus.OPEN)
                        .toList();
                for (SodViolation v : openViolations) {
                    if (!intersectionNormalized.contains(v.getUserDn().toLowerCase())) {
                        v.setStatus(SodViolationStatus.RESOLVED);
                        v.setResolvedAt(OffsetDateTime.now());
                        violationRepo.save(v);
                        resolvedViolations++;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to scan policy '{}' (id={}): {}", policy.getName(), policy.getId(), e.getMessage());
            }
        }

        auditService.record(principal, directoryId, AuditAction.SOD_SCAN_EXECUTED, null,
                Map.of("policiesScanned", String.valueOf(policies.size()),
                        "violationsFound", String.valueOf(totalViolations),
                        "newViolations", String.valueOf(newViolations)));

        return new SodScanResultDto(policies.size(), totalViolations, newViolations, resolvedViolations);
    }

    // ── Real-time check (called on group member addition) ────────────────────

    /**
     * Check if adding userDn to groupDn would violate any SoD policy.
     * If a BLOCK policy is violated and no active exemption exists, throws SodViolationException.
     * If an ALERT policy is violated, creates the violation record and logs an audit event.
     */
    @Transactional
    public void checkMembership(UUID directoryId, String userDn, String groupDn, AuthPrincipal principal) {
        DirectoryConnection dir = directoryRepo.findById(directoryId).orElse(null);
        if (dir == null) return;

        List<SodPolicy> policies = policyRepo.findByDirectoryIdAndEnabledTrue(directoryId);

        for (SodPolicy policy : policies) {
            String otherGroupDn = null;

            if (policy.getGroupADn().equalsIgnoreCase(groupDn)) {
                otherGroupDn = policy.getGroupBDn();
            } else if (policy.getGroupBDn().equalsIgnoreCase(groupDn)) {
                otherGroupDn = policy.getGroupADn();
            }

            if (otherGroupDn == null) continue;

            // Check if user is already a member of the other group
            try {
                List<String> otherMembers = ldapGroupService.getMembers(dir, otherGroupDn, "member");
                boolean isMember = otherMembers.stream().anyMatch(m -> m.equalsIgnoreCase(userDn));
                if (!isMember) continue;

                // Check for active exemption — skip enforcement if exempted
                if (hasActiveExemption(policy.getId(), userDn)) {
                    log.info("SoD policy '{}' violated by user '{}' but exemption is active — allowing",
                            policy.getName(), userDn);
                    continue;
                }

                String message = String.format(
                        "SoD policy '%s' violated: user '%s' cannot be in both '%s' and '%s'",
                        policy.getName(), userDn, policy.getGroupADn(), policy.getGroupBDn());

                if (policy.getAction() == SodAction.BLOCK) {
                    auditService.record(principal, directoryId, AuditAction.SOD_VIOLATION_BLOCKED, userDn,
                            Map.of("policyName", policy.getName(), "policyId", policy.getId().toString(),
                                    "groupDn", groupDn, "conflictingGroup", otherGroupDn));
                    throw new SodViolationException(message);
                }

                // ALERT — create violation record but allow the operation
                Optional<SodViolation> existing = violationRepo.findByPolicyIdAndUserDnAndStatus(
                        policy.getId(), userDn, SodViolationStatus.OPEN);
                if (existing.isEmpty()) {
                    SodViolation v = new SodViolation();
                    v.setPolicy(policy);
                    v.setUserDn(userDn);
                    v.setUserDisplayName(resolveDisplayName(dir, userDn));
                    v.setDetectedAt(OffsetDateTime.now());
                    v.setStatus(SodViolationStatus.OPEN);
                    violationRepo.save(v);
                }

                auditService.record(principal, directoryId, AuditAction.SOD_VIOLATION_DETECTED, userDn,
                        Map.of("policyName", policy.getName(), "policyId", policy.getId().toString(),
                                "groupDn", groupDn, "conflictingGroup", otherGroupDn, "action", "ALERT"));
                log.warn("SoD ALERT: {}", message);
            } catch (SodViolationException e) {
                throw e; // re-throw BLOCK exceptions
            } catch (Exception e) {
                log.error("Error checking SoD policy '{}' for user {}: {}", policy.getName(), userDn, e.getMessage());
            }
        }
    }

    // ── Violation management ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SodViolationResponse> listViolations(UUID directoryId, SodViolationStatus status) {
        List<SodViolation> violations;
        if (status != null) {
            violations = violationRepo.findByDirectoryIdAndStatus(directoryId, status);
        } else {
            violations = violationRepo.findByDirectoryId(directoryId);
        }
        return violations.stream().map(this::toViolationResponse).toList();
    }

    @Transactional
    public SodViolationResponse exemptViolation(UUID violationId, ExemptViolationRequest req, AuthPrincipal principal) {
        SodViolation v = violationRepo.findById(violationId)
                .orElseThrow(() -> new ResourceNotFoundException("SodViolation", violationId));
        Account exemptedBy = accountRepo.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Account", principal.id()));

        v.setStatus(SodViolationStatus.EXEMPTED);
        v.setExemptedBy(exemptedBy);
        v.setExemptionReason(req.reason());
        v.setExemptionExpiresAt(req.expiresAt());
        v.setResolvedAt(OffsetDateTime.now());
        violationRepo.save(v);

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("policyName", v.getPolicy().getName());
        auditDetails.put("violationId", violationId.toString());
        auditDetails.put("reason", req.reason());
        if (req.expiresAt() != null) {
            auditDetails.put("expiresAt", req.expiresAt().toString());
        }

        auditService.record(principal, v.getPolicy().getDirectory().getId(), AuditAction.SOD_VIOLATION_EXEMPTED,
                v.getUserDn(), auditDetails);

        return toViolationResponse(v);
    }

    @Transactional
    public SodViolationResponse resolveViolation(UUID violationId, AuthPrincipal principal) {
        SodViolation v = violationRepo.findById(violationId)
                .orElseThrow(() -> new ResourceNotFoundException("SodViolation", violationId));
        v.setStatus(SodViolationStatus.RESOLVED);
        v.setResolvedAt(OffsetDateTime.now());
        violationRepo.save(v);

        auditService.record(principal, v.getPolicy().getDirectory().getId(), AuditAction.SOD_VIOLATION_RESOLVED,
                v.getUserDn(),
                Map.of("policyName", v.getPolicy().getName(), "violationId", violationId.toString()));

        return toViolationResponse(v);
    }

    /**
     * Reopen exempted violations whose exemption has expired.
     * Called by {@link SodScheduler}.
     */
    @Transactional
    public int reopenExpiredExemptions() {
        List<SodViolation> expired = violationRepo.findExpiredExemptions(
                SodViolationStatus.EXEMPTED, OffsetDateTime.now());
        for (SodViolation v : expired) {
            v.setStatus(SodViolationStatus.OPEN);
            v.setResolvedAt(null);
            v.setExemptedBy(null);
            v.setExemptionReason(null);
            v.setExemptionExpiresAt(null);
            violationRepo.save(v);
            log.info("Reopened expired SoD exemption for user '{}' on policy '{}'",
                    v.getUserDn(), v.getPolicy().getName());
        }
        return expired.size();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private SodPolicy getPolicyOrThrow(UUID policyId) {
        return policyRepo.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("SodPolicy", policyId));
    }

    /**
     * Returns true if there's an existing OPEN violation or a non-expired EXEMPTED violation
     * for this policy+user combination — meaning a new OPEN violation should NOT be created.
     */
    private boolean hasActiveViolation(UUID policyId, String userDn) {
        // Already has an OPEN violation
        if (violationRepo.findByPolicyIdAndUserDnAndStatus(policyId, userDn, SodViolationStatus.OPEN).isPresent()) {
            return true;
        }
        // Has a non-expired exemption
        return hasActiveExemption(policyId, userDn);
    }

    /**
     * Returns true if the user has an active (non-expired) exemption for this policy.
     */
    private boolean hasActiveExemption(UUID policyId, String userDn) {
        Optional<SodViolation> exempted = violationRepo.findByPolicyIdAndUserDnAndStatus(
                policyId, userDn, SodViolationStatus.EXEMPTED);
        if (exempted.isEmpty()) return false;

        SodViolation v = exempted.get();
        // No expiry set means permanent exemption
        if (v.getExemptionExpiresAt() == null) return true;
        // Check if exemption is still valid
        return v.getExemptionExpiresAt().isAfter(OffsetDateTime.now());
    }

    private void validateGroupPair(String groupADn, String groupBDn) {
        if (groupADn.equalsIgnoreCase(groupBDn)) {
            throw new IllegalArgumentException("Group A and Group B cannot be the same DN");
        }
    }

    private SodPolicyResponse toResponse(SodPolicy p) {
        long openCount = violationRepo.countByPolicyIdAndStatus(p.getId(), SodViolationStatus.OPEN);
        return new SodPolicyResponse(
                p.getId(), p.getName(), p.getDescription(),
                p.getDirectory().getId(),
                p.getGroupADn(), p.getGroupBDn(),
                p.getGroupAName(), p.getGroupBName(),
                p.getSeverity(), p.getAction(), p.isEnabled(),
                openCount,
                p.getCreatedBy().getUsername(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    private SodViolationResponse toViolationResponse(SodViolation v) {
        SodPolicy p = v.getPolicy();
        return new SodViolationResponse(
                v.getId(), p.getId(), p.getName(),
                v.getUserDn(), v.getUserDisplayName(),
                v.getStatus(), v.getDetectedAt(), v.getResolvedAt(),
                v.getExemptedBy() != null ? v.getExemptedBy().getUsername() : null,
                v.getExemptionReason(),
                v.getExemptionExpiresAt(),
                p.getGroupADn(), p.getGroupBDn(),
                p.getGroupAName(), p.getGroupBName());
    }

    private String resolveDisplayName(DirectoryConnection dir, String userDn) {
        try {
            LdapUser user = ldapUserService.getUser(dir, userDn, "cn", "displayName");
            String display = user.getFirstValue("displayName");
            if (display == null) display = user.getCn();
            return display != null ? display : userDn;
        } catch (Exception e) {
            return userDn;
        }
    }
}
