package com.ldapadmin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.config.AppProperties;
import com.ldapadmin.dto.audit.AuditEventResponse;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.entity.enums.SodViolationStatus;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generates a ZIP evidence package containing compliance reports, campaign data,
 * SoD policies/violations, approval history, audit events, and user entitlements.
 *
 * <p>The manifest includes SHA-256 checksums for each file and is signed with
 * HMAC-SHA256 using the application's encryption key for tamper evidence.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvidencePackageService {

    private final DirectoryConnectionRepository directoryRepo;
    private final AccessReviewCampaignRepository campaignRepo;
    private final AccessReviewCampaignHistoryRepository historyRepo;
    private final AccessReviewCampaignService campaignService;
    private final SodPolicyRepository sodPolicyRepo;
    private final SodViolationRepository sodViolationRepo;
    private final PendingApprovalRepository approvalRepo;
    private final PdfReportService pdfReportService;
    private final LdapUserService ldapUserService;
    private final LdapGroupService ldapGroupService;
    private final AppProperties appProperties;
    private final AccountRepository accountRepo;
    private final AuditQueryService auditQueryService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    private static final String USER_OBJECTCLASS_FILTER =
            "(|(objectClass=inetOrgPerson)(&(objectClass=user)(!(objectClass=computer))))";

    private static final String GROUP_OBJECTCLASS_FILTER =
            "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup)(objectClass=group)(objectClass=groupOfURLs))";

    /** Maximum LDAP entries for entitlements export to prevent OOM. */
    private static final int MAX_ENTITLEMENT_ENTRIES = 50_000;

    /**
     * Generates a complete evidence package as a ZIP byte array.
     */
    @Transactional(readOnly = true)
    public byte[] generateEvidencePackage(UUID directoryId, List<UUID> campaignIds,
                                          boolean includeSod, boolean includeEntitlements,
                                          boolean includeAuditEvents,
                                          String generatedBy) throws IOException {
        DirectoryConnection dc = directoryRepo.findById(directoryId)
                .orElseThrow(() -> new IllegalArgumentException("Directory not found: " + directoryId));

        OffsetDateTime generatedAt = OffsetDateTime.now();
        Map<String, byte[]> files = new LinkedHashMap<>();

        // ── Standard PDF reports ────────────────────────────────────────────
        addPdfReports(files, directoryId);

        // ── Campaign-specific data ──────────────────────────────────────────
        addCampaignData(files, directoryId, campaignIds);

        // ── SoD data (optional) ─────────────────────────────────────────────
        if (includeSod) {
            addSodData(files, directoryId);
        }

        // ── Approval history ────────────────────────────────────────────────
        addApprovalHistory(files, directoryId);

        // ── Audit events (optional) ─────────────────────────────────────────
        if (includeAuditEvents) {
            addAuditEvents(files, directoryId);
        }

        // ── User entitlements (optional) ────────────────────────────────────
        if (includeEntitlements) {
            addUserEntitlements(files, dc);
        }

        // ── Build manifest with checksums ───────────────────────────────────
        byte[] manifest = buildManifest(files, directoryId, dc.getDisplayName(),
                generatedAt, generatedBy, campaignIds, includeSod, includeEntitlements);
        files.put("manifest.json", manifest);

        // ── Record audit event ──────────────────────────────────────────────
        recordGenerationAudit(directoryId, generatedBy, campaignIds, includeSod, includeEntitlements);

        // ── Package into ZIP ────────────────────────────────────────────────
        return buildZip(files);
    }

    // ── PDF reports ───────────────────────────────────────────────────────────

    private void addPdfReports(Map<String, byte[]> files, UUID directoryId) {
        try {
            byte[] userAccessPdf = pdfReportService.generateUserAccessReport(directoryId, null);
            files.put("reports/user-access-report.pdf", userAccessPdf);
        } catch (Exception e) {
            log.warn("Failed to generate user access report: {}", e.getMessage());
        }

        try {
            byte[] privilegedPdf = pdfReportService.generatePrivilegedAccountInventory();
            files.put("reports/privileged-account-inventory.pdf", privilegedPdf);
        } catch (Exception e) {
            log.warn("Failed to generate privileged account inventory: {}", e.getMessage());
        }
    }

    // ── Campaign data ─────────────────────────────────────────────────────────

    private void addCampaignData(Map<String, byte[]> files, UUID directoryId, List<UUID> campaignIds) {
        for (UUID campaignId : campaignIds) {
            Optional<AccessReviewCampaign> opt = campaignRepo.findById(campaignId);
            if (opt.isEmpty()) {
                log.warn("Campaign not found: {}", campaignId);
                continue;
            }

            AccessReviewCampaign campaign = opt.get();

            // Verify campaign belongs to this directory
            if (!campaign.getDirectory().getId().equals(directoryId)) {
                log.warn("Campaign {} does not belong to directory {} — skipping", campaignId, directoryId);
                continue;
            }

            String safeName = sanitizeFilename(campaign.getName());
            String prefix = "campaigns/" + safeName + "/";

            // Access review summary PDF
            try {
                byte[] summaryPdf = pdfReportService.generateAccessReviewSummary(campaignId);
                files.put(prefix + "access-review-summary.pdf", summaryPdf);
            } catch (Exception e) {
                log.warn("Failed to generate access review summary for campaign {}: {}",
                        campaignId, e.getMessage());
            }

            // Decisions CSV
            try {
                byte[] csv = campaignService.exportCsv(directoryId, campaignId);
                files.put(prefix + "decisions.csv", csv);
            } catch (Exception e) {
                log.warn("Failed to export CSV for campaign {}: {}", campaignId, e.getMessage());
            }

            // Campaign history JSON
            try {
                List<AccessReviewCampaignHistory> history =
                        historyRepo.findByCampaignIdOrderByChangedAtAsc(campaignId);
                List<Map<String, Object>> historyData = history.stream().map(h -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", h.getId().toString());
                    entry.put("oldStatus", h.getOldStatus() != null ? h.getOldStatus().name() : null);
                    entry.put("newStatus", h.getNewStatus() != null ? h.getNewStatus().name() : null);
                    entry.put("changedBy", h.getChangedBy() != null ? h.getChangedBy().getUsername() : null);
                    entry.put("changedAt", h.getChangedAt() != null ? h.getChangedAt().toString() : null);
                    entry.put("note", h.getNote());
                    return entry;
                }).toList();
                files.put(prefix + "history.json", objectMapper.writeValueAsBytes(historyData));
            } catch (Exception e) {
                log.warn("Failed to export history for campaign {}: {}", campaignId, e.getMessage());
            }
        }
    }

    // ── SoD data ──────────────────────────────────────────────────────────────

    private void addSodData(Map<String, byte[]> files, UUID directoryId) {
        try {
            List<SodPolicy> policies = sodPolicyRepo.findByDirectoryId(directoryId);
            List<Map<String, Object>> policyData = policies.stream().map(p -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", p.getId().toString());
                entry.put("name", p.getName());
                entry.put("description", p.getDescription());
                entry.put("groupADn", p.getGroupADn());
                entry.put("groupBDn", p.getGroupBDn());
                entry.put("groupAName", p.getGroupAName());
                entry.put("groupBName", p.getGroupBName());
                entry.put("severity", p.getSeverity() != null ? p.getSeverity().name() : null);
                entry.put("action", p.getAction() != null ? p.getAction().name() : null);
                entry.put("enabled", p.isEnabled());
                entry.put("createdBy", p.getCreatedBy() != null ? p.getCreatedBy().getUsername() : null);
                entry.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
                return entry;
            }).toList();
            files.put("sod/policies.json", objectMapper.writeValueAsBytes(policyData));
        } catch (Exception e) {
            log.warn("Failed to export SoD policies: {}", e.getMessage());
        }

        // Export ALL violations (OPEN, EXEMPTED, RESOLVED) for complete audit picture
        try {
            List<SodViolation> violations = sodViolationRepo.findByDirectoryId(directoryId);
            List<Map<String, Object>> violationData = violations.stream().map(v -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", v.getId().toString());
                entry.put("policyId", v.getPolicy() != null ? v.getPolicy().getId().toString() : null);
                entry.put("policyName", v.getPolicy() != null ? v.getPolicy().getName() : null);
                entry.put("userDn", v.getUserDn());
                entry.put("userDisplayName", v.getUserDisplayName());
                entry.put("status", v.getStatus() != null ? v.getStatus().name() : null);
                entry.put("detectedAt", v.getDetectedAt() != null ? v.getDetectedAt().toString() : null);
                entry.put("resolvedAt", v.getResolvedAt() != null ? v.getResolvedAt().toString() : null);
                entry.put("exemptedBy", v.getExemptedBy() != null ? v.getExemptedBy().getUsername() : null);
                entry.put("exemptionReason", v.getExemptionReason());
                entry.put("exemptionExpiresAt", v.getExemptionExpiresAt() != null ? v.getExemptionExpiresAt().toString() : null);
                return entry;
            }).toList();
            files.put("sod/violations.json", objectMapper.writeValueAsBytes(violationData));
        } catch (Exception e) {
            log.warn("Failed to export SoD violations: {}", e.getMessage());
        }
    }

    // ── Approval history ──────────────────────────────────────────────────────

    private void addApprovalHistory(Map<String, byte[]> files, UUID directoryId) {
        try {
            // Pre-load account lookup for resolving UUIDs to usernames
            Map<UUID, String> accountNames = new HashMap<>();

            List<PendingApproval> approvals = approvalRepo.findAllByDirectoryIdOrderByCreatedAtDesc(directoryId);
            List<Map<String, Object>> approvalData = approvals.stream().map(a -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", a.getId().toString());
                entry.put("requestType", a.getRequestType() != null ? a.getRequestType().name() : null);
                entry.put("status", a.getStatus() != null ? a.getStatus().name() : null);
                entry.put("requestedBy", resolveAccountName(a.getRequestedBy(), accountNames));
                entry.put("reviewedBy", resolveAccountName(a.getReviewedBy(), accountNames));
                entry.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
                entry.put("reviewedAt", a.getReviewedAt() != null ? a.getReviewedAt().toString() : null);
                entry.put("rejectReason", a.getRejectReason());
                return entry;
            }).toList();
            files.put("approval-history/approvals.json", objectMapper.writeValueAsBytes(approvalData));
        } catch (Exception e) {
            log.warn("Failed to export approval history: {}", e.getMessage());
        }
    }

    private String resolveAccountName(UUID accountId, Map<UUID, String> cache) {
        if (accountId == null) return null;
        return cache.computeIfAbsent(accountId, id ->
                accountRepo.findById(id).map(Account::getUsername).orElse(id.toString()));
    }

    // ── Audit events ─────────────────────────────────────────────────────────

    private void addAuditEvents(Map<String, byte[]> files, UUID directoryId) {
        try {
            // Export last 90 days of audit events for this directory
            OffsetDateTime from = OffsetDateTime.now().minusDays(90);
            Page<AuditEventResponse> events = auditQueryService.query(
                    directoryId, null, null, from, null, 0, 10_000);

            files.put("audit/events.json", objectMapper.writeValueAsBytes(events.getContent()));
            log.info("Exported {} audit events for evidence package", events.getNumberOfElements());
        } catch (Exception e) {
            log.warn("Failed to export audit events: {}", e.getMessage());
        }
    }

    // ── User entitlements ─────────────────────────────────────────────────────

    private void addUserEntitlements(Map<String, byte[]> files, DirectoryConnection dc) {
        try {
            List<LdapUser> users = ldapUserService.searchUsers(dc, USER_OBJECTCLASS_FILTER, null,
                    MAX_ENTITLEMENT_ENTRIES, "cn", "uid", "sAMAccountName", "displayName", "mail", "memberOf");
            if (users.size() >= MAX_ENTITLEMENT_ENTRIES) {
                log.warn("User entitlements export hit the {} limit — results truncated", MAX_ENTITLEMENT_ENTRIES);
            }

            List<LdapGroup> groups = ldapGroupService.searchGroups(dc, GROUP_OBJECTCLASS_FILTER,
                    null, MAX_ENTITLEMENT_ENTRIES, "cn", "member", "uniqueMember", "memberUid");

            // Build DN -> group name lookup
            Map<String, List<String>> userToGroups = new HashMap<>();
            for (LdapGroup group : groups) {
                String groupName = group.getCn() != null ? group.getCn() : group.getDn();
                for (String memberDn : group.getAllMembers()) {
                    userToGroups.computeIfAbsent(memberDn.toLowerCase(), k -> new ArrayList<>())
                            .add(groupName);
                }
            }

            List<Map<String, Object>> entitlements = users.stream().map(u -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("dn", u.getDn());
                entry.put("cn", u.getCn());
                entry.put("loginName", u.getLoginName());
                entry.put("displayName", u.getDisplayName());
                entry.put("mail", u.getMail());

                // Combine memberOf attribute + reverse group membership lookup
                Set<String> groupNames = new LinkedHashSet<>();
                List<String> memberOf = u.getMemberOf();
                if (memberOf != null) groupNames.addAll(memberOf);
                List<String> fromGroups = userToGroups.get(u.getDn().toLowerCase());
                if (fromGroups != null) groupNames.addAll(fromGroups);

                entry.put("groups", new ArrayList<>(groupNames));
                return entry;
            }).toList();
            files.put("entitlements/user-entitlements.json", objectMapper.writeValueAsBytes(entitlements));
        } catch (Exception e) {
            log.warn("Failed to export user entitlements: {}", e.getMessage());
        }
    }

    // ── Audit trail ──────────────────────────────────────────────────────────

    private void recordGenerationAudit(UUID directoryId, String generatedBy,
                                        List<UUID> campaignIds, boolean includeSod,
                                        boolean includeEntitlements) {
        try {
            var systemPrincipal = new com.ldapadmin.auth.AuthPrincipal(
                    com.ldapadmin.auth.PrincipalType.SUPERADMIN, new UUID(0, 0), generatedBy);

            auditService.record(systemPrincipal, directoryId,
                    AuditAction.BULK_ATTRIBUTE_UPDATE, // closest existing action for "export"
                    null,
                    Map.of("operation", "evidence_package_generated",
                            "generatedBy", generatedBy,
                            "campaignCount", String.valueOf(campaignIds.size()),
                            "includeSod", String.valueOf(includeSod),
                            "includeEntitlements", String.valueOf(includeEntitlements)));
        } catch (Exception e) {
            log.warn("Failed to record evidence package audit event: {}", e.getMessage());
        }
    }

    // ── Manifest ──────────────────────────────────────────────────────────────

    byte[] buildManifest(Map<String, byte[]> files, UUID directoryId, String directoryName,
                         OffsetDateTime generatedAt, String generatedBy,
                         List<UUID> campaignIds, boolean includeSod,
                         boolean includeEntitlements) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("generatedAt", generatedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        manifest.put("generatedBy", generatedBy);
        manifest.put("directoryId", directoryId.toString());
        manifest.put("directoryName", directoryName);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("campaignIds", campaignIds.stream().map(UUID::toString).toList());
        options.put("includeSod", includeSod);
        options.put("includeEntitlements", includeEntitlements);
        manifest.put("options", options);

        // File checksums
        List<Map<String, String>> fileEntries = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            Map<String, String> fileEntry = new LinkedHashMap<>();
            fileEntry.put("path", entry.getKey());
            fileEntry.put("sha256", sha256Hex(entry.getValue()));
            fileEntry.put("size", String.valueOf(entry.getValue().length));
            fileEntries.add(fileEntry);
        }
        manifest.put("files", fileEntries);

        // HMAC signature over the manifest content (without signature field)
        byte[] manifestBytes = objectMapper.writeValueAsBytes(manifest);
        String hmac = hmacSha256(manifestBytes);
        manifest.put("hmacSha256Signature", hmac);

        return objectMapper.writeValueAsBytes(manifest);
    }

    // ── ZIP construction ──────────────────────────────────────────────────────

    byte[] buildZip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    // ── Crypto helpers ────────────────────────────────────────────────────────

    String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    String hmacSha256(byte[] data) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(appProperties.getEncryption().getKey());
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] hmac = mac.doFinal(data);
            return bytesToHex(hmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 signing failed", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Sanitize a name for use as a ZIP path component.
     * Strips all characters except alphanumerics, underscores, and hyphens.
     * Dots are replaced to prevent directory traversal.
     */
    private static String sanitizeFilename(String name) {
        if (name == null) return "unnamed";
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
    }
}
