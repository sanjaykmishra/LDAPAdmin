package com.ldapadmin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.dto.audit.AuditEventResponse;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.AuditAction;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
    private final CryptoService cryptoService;
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

            // Campaign history CSV
            try {
                List<AccessReviewCampaignHistory> history =
                        historyRepo.findByCampaignIdOrderByChangedAtAsc(campaignId);
                StringBuilder csv = new StringBuilder("Old Status,New Status,Changed By,Changed At,Note\n");
                for (AccessReviewCampaignHistory h : history) {
                    csv.append(csvEscape(h.getOldStatus() != null ? h.getOldStatus().name() : "")).append(',');
                    csv.append(csvEscape(h.getNewStatus() != null ? h.getNewStatus().name() : "")).append(',');
                    csv.append(csvEscape(h.getChangedBy() != null ? h.getChangedBy().getUsername() : "")).append(',');
                    csv.append(csvEscape(h.getChangedAt() != null ? h.getChangedAt().toString() : "")).append(',');
                    csv.append(csvEscape(h.getNote() != null ? h.getNote() : "")).append('\n');
                }
                files.put(prefix + "history.csv", csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.warn("Failed to export history for campaign {}: {}", campaignId, e.getMessage());
            }
        }
    }

    // ── SoD data ──────────────────────────────────────────────────────────────

    private void addSodData(Map<String, byte[]> files, UUID directoryId) {
        try {
            List<SodPolicy> policies = sodPolicyRepo.findByDirectoryId(directoryId);
            StringBuilder csv = new StringBuilder("Name,Description,Group A,Group B,Severity,Action,Enabled,Created By,Created At\n");
            for (SodPolicy p : policies) {
                csv.append(csvEscape(p.getName())).append(',');
                csv.append(csvEscape(p.getDescription())).append(',');
                csv.append(csvEscape(p.getGroupAName())).append(',');
                csv.append(csvEscape(p.getGroupBName())).append(',');
                csv.append(csvEscape(p.getSeverity() != null ? p.getSeverity().name() : "")).append(',');
                csv.append(csvEscape(p.getAction() != null ? p.getAction().name() : "")).append(',');
                csv.append(p.isEnabled()).append(',');
                csv.append(csvEscape(p.getCreatedBy() != null ? p.getCreatedBy().getUsername() : "")).append(',');
                csv.append(csvEscape(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "")).append('\n');
            }
            files.put("sod/policies.csv", csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to export SoD policies: {}", e.getMessage());
        }

        // Export ALL violations (OPEN, EXEMPTED, RESOLVED) for complete audit picture
        try {
            List<SodViolation> violations = sodViolationRepo.findByDirectoryId(directoryId);
            StringBuilder csv = new StringBuilder("Policy,User DN,User Name,Status,Detected At,Resolved At,Exempted By,Exemption Reason\n");
            for (SodViolation v : violations) {
                csv.append(csvEscape(v.getPolicy() != null ? v.getPolicy().getName() : "")).append(',');
                csv.append(csvEscape(v.getUserDn())).append(',');
                csv.append(csvEscape(v.getUserDisplayName())).append(',');
                csv.append(csvEscape(v.getStatus() != null ? v.getStatus().name() : "")).append(',');
                csv.append(csvEscape(v.getDetectedAt() != null ? v.getDetectedAt().toString() : "")).append(',');
                csv.append(csvEscape(v.getResolvedAt() != null ? v.getResolvedAt().toString() : "")).append(',');
                csv.append(csvEscape(v.getExemptedBy() != null ? v.getExemptedBy().getUsername() : "")).append(',');
                csv.append(csvEscape(v.getExemptionReason() != null ? v.getExemptionReason() : "")).append('\n');
            }
            files.put("sod/violations.csv", csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to export SoD violations: {}", e.getMessage());
        }
    }

    // ── Approval history ──────────────────────────────────────────────────────

    private void addApprovalHistory(Map<String, byte[]> files, UUID directoryId) {
        try {
            Map<UUID, String> accountNames = new HashMap<>();
            List<PendingApproval> approvals = approvalRepo.findAllByDirectoryIdOrderByCreatedAtDesc(directoryId);
            StringBuilder csv = new StringBuilder("Request Type,Status,Requested By,Reviewed By,Created At,Reviewed At,Reject Reason\n");
            for (PendingApproval a : approvals) {
                csv.append(csvEscape(a.getRequestType() != null ? a.getRequestType().name() : "")).append(',');
                csv.append(csvEscape(a.getStatus() != null ? a.getStatus().name() : "")).append(',');
                csv.append(csvEscape(resolveAccountName(a.getRequestedBy(), accountNames))).append(',');
                csv.append(csvEscape(resolveAccountName(a.getReviewedBy(), accountNames))).append(',');
                csv.append(csvEscape(a.getCreatedAt() != null ? a.getCreatedAt().toString() : "")).append(',');
                csv.append(csvEscape(a.getReviewedAt() != null ? a.getReviewedAt().toString() : "")).append(',');
                csv.append(csvEscape(a.getRejectReason() != null ? a.getRejectReason() : "")).append('\n');
            }
            files.put("approval-history/approvals.csv", csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
            OffsetDateTime from = OffsetDateTime.now().minusDays(90);
            Page<AuditEventResponse> events = auditQueryService.query(
                    directoryId, null, null, from, null, 0, 10_000);

            StringBuilder csv = new StringBuilder("Occurred At,Actor,Action,Target DN\n");
            for (AuditEventResponse e : events.getContent()) {
                csv.append(csvEscape(e.occurredAt() != null ? e.occurredAt().toString() : "")).append(',');
                csv.append(csvEscape(e.actorUsername() != null ? e.actorUsername() : "")).append(',');
                csv.append(csvEscape(e.action() != null ? e.action().name() : "")).append(',');
                csv.append(csvEscape(e.targetDn() != null ? e.targetDn() : "")).append('\n');
            }
            files.put("audit/events.csv", csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

            StringBuilder csv = new StringBuilder("DN,CN,Login Name,Display Name,Email,Groups\n");
            for (LdapUser u : users) {
                Set<String> groupNames = new LinkedHashSet<>();
                List<String> memberOf = u.getMemberOf();
                if (memberOf != null) groupNames.addAll(memberOf);
                List<String> fromGroups = userToGroups.get(u.getDn().toLowerCase());
                if (fromGroups != null) groupNames.addAll(fromGroups);

                csv.append(csvEscape(u.getDn())).append(',');
                csv.append(csvEscape(u.getCn())).append(',');
                csv.append(csvEscape(u.getLoginName())).append(',');
                csv.append(csvEscape(u.getDisplayName())).append(',');
                csv.append(csvEscape(u.getMail())).append(',');
                csv.append(csvEscape(String.join("; ", groupNames))).append('\n');
            }
            files.put("entitlements/user-entitlements.csv", csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

    // ── Crypto helpers (delegated to CryptoService) ─────────────────────────

    String sha256Hex(byte[] data) {
        return cryptoService.sha256Hex(data);
    }

    String hmacSha256(byte[] data) {
        return cryptoService.hmacSha256(data);
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

    /** RFC 4180 CSV field escaping. */
    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
