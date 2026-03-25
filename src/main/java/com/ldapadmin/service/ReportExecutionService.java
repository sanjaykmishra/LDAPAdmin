package com.ldapadmin.service;

import com.ldapadmin.dto.profile.GroupChangePreview;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.ProvisioningProfile;
import com.ldapadmin.entity.SodViolation;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.entity.enums.OutputFormat;
import com.ldapadmin.entity.enums.ReportType;
import com.ldapadmin.entity.enums.SodViolationStatus;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.AuditEventRepository;
import com.ldapadmin.repository.ProvisioningProfileRepository;
import com.ldapadmin.repository.SodViolationRepository;
import com.ldapadmin.util.CsvUtils;
import com.unboundid.ldap.sdk.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Executes a report on-demand and returns the results as CSV or PDF bytes.
 *
 * <h3>Report types and their queries</h3>
 * <ul>
 *   <li><b>USERS_IN_GROUP</b>     — {@code (memberOf=<groupDn>)}; param {@code groupDn} required.</li>
 *   <li><b>USERS_IN_BRANCH</b>    — {@code (objectClass=inetOrgPerson)},
 *       base = {@code branchDn}; param {@code branchDn} required.</li>
 *   <li><b>USERS_WITH_NO_GROUP</b>— {@code (&(objectClass=inetOrgPerson)(!(memberOf=*)))}.</li>
 *   <li><b>RECENTLY_ADDED</b>     — {@code (createTimestamp>=<timestamp>)}; param {@code lookbackDays}.</li>
 *   <li><b>RECENTLY_MODIFIED</b>  — {@code (modifyTimestamp>=<timestamp>)}; param {@code lookbackDays}.</li>
 *   <li><b>RECENTLY_DELETED</b>   — audit events ({@code USER_DELETE} + changelog deletes).</li>
 *   <li><b>DISABLED_ACCOUNTS</b>  — {@code (|(pwdAccountLockedTime=*)(loginDisabled=TRUE))}.</li>
 *   <li><b>MISSING_PROFILE_GROUPS</b> — profile group gap analysis.</li>
 *   <li><b>SOD_VIOLATIONS</b>     — current SoD violations from the database.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportExecutionService {

    private static final DateTimeFormatter LDAP_TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'");

    /** Maximum LDAP entries returned per report to prevent OOM. */
    private static final int MAX_LDAP_RESULTS = 10_000;

    private final LdapUserService                userService;
    private final AuditEventRepository           auditEventRepo;
    private final ProvisioningProfileRepository  profileRepo;
    private final ProvisioningProfileService     profileService;
    private final SodViolationRepository         sodViolationRepo;
    private final PdfReportService               pdfReportService;

    /**
     * Runs the report and returns the result as CSV or PDF bytes.
     */
    public byte[] run(DirectoryConnection dc,
                      ReportType reportType,
                      Map<String, Object> params,
                      OutputFormat format,
                      UUID directoryId) throws IOException {

        Map<String, Object> safeParams = params != null ? params : Map.of();

        ReportData data = switch (reportType) {
            case USERS_IN_GROUP      -> runLdapReportData(dc, buildGroupFilter(safeParams), null);
            case USERS_IN_BRANCH     -> runLdapReportData(dc, "(objectClass=inetOrgPerson)",
                                                       requireString(safeParams, "branchDn"));
            case USERS_WITH_NO_GROUP -> runLdapReportData(dc,
                                                       "(&(objectClass=inetOrgPerson)(!(memberOf=*)))", null);
            case RECENTLY_ADDED      -> runLdapReportData(dc,
                                                       "(createTimestamp>=" + lookbackTimestamp(safeParams) + ")", null);
            case RECENTLY_MODIFIED   -> runLdapReportData(dc,
                                                       "(modifyTimestamp>=" + lookbackTimestamp(safeParams) + ")", null);
            case RECENTLY_DELETED    -> runDeletedReportData(directoryId, safeParams);
            case DISABLED_ACCOUNTS   -> runLdapReportData(dc,
                                                       "(|(pwdAccountLockedTime=*)(loginDisabled=TRUE))", null);
            case MISSING_PROFILE_GROUPS -> runMissingProfileGroupsReportData(dc, directoryId);
            case SOD_VIOLATIONS      -> runSodViolationsReportData(directoryId);
        };

        if (format == OutputFormat.PDF) {
            return pdfReportService.buildPdf(reportType.name().replace('_', ' '), "", data.columns, data.toRowLists());
        }
        return CsvUtils.write(data.columns, data.rows);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Intermediate report data container (columns + rows). */
    record ReportData(List<String> columns, List<Map<String, String>> rows) {
        /** Convert to list-of-lists for PDF rendering. */
        List<List<String>> toRowLists() {
            return rows.stream()
                    .map(row -> columns.stream().map(c -> row.getOrDefault(c, "")).toList())
                    .toList();
        }
    }

    private ReportData runLdapReportData(DirectoryConnection dc, String filter, String baseDn) {
        List<LdapUser> users = userService.searchUsers(dc, filter, baseDn, MAX_LDAP_RESULTS, "*");
        if (users.size() >= MAX_LDAP_RESULTS) {
            log.warn("Report query hit the {} result limit — results may be truncated. "
                    + "Filter: {}, baseDn: {}", MAX_LDAP_RESULTS, filter, baseDn);
        }

        // Deterministic column order: dn first, then remaining attributes sorted
        TreeSet<String> attrNames = new TreeSet<>();
        users.forEach(u -> attrNames.addAll(u.getAttributes().keySet()));

        List<String> columns = new ArrayList<>();
        columns.add("dn");
        columns.addAll(attrNames);

        List<Map<String, String>> rows = users.stream()
                .map(u -> buildRow(u, columns))
                .toList();

        log.debug("Report query [filter={}, base={}] → {} entries", filter, baseDn, rows.size());
        return new ReportData(columns, rows);
    }

    private Map<String, String> buildRow(LdapUser user, List<String> columns) {
        Map<String, String> row = new LinkedHashMap<>();
        for (String col : columns) {
            if ("dn".equals(col)) {
                row.put("dn", user.getDn());
            } else {
                row.put(col, String.join("|", user.getValues(col)));
            }
        }
        return row;
    }

    /**
     * Queries audit events for both internal USER_DELETE and LDAP_CHANGE delete events.
     */
    private ReportData runDeletedReportData(UUID directoryId, Map<String, Object> params) {
        int lookbackDays = lookbackDays(params);
        OffsetDateTime from = OffsetDateTime.now().minusDays(lookbackDays);

        // Internal deletes
        var internalDeletes = auditEventRepo.findAll(
                directoryId, null, AuditAction.USER_DELETE.getDbValue(),
                null, from, null, Pageable.unpaged());

        // Changelog deletes (LDAP_CHANGE events where detail contains delete indicators)
        var changelogDeletes = auditEventRepo.findAll(
                directoryId, null, AuditAction.LDAP_CHANGE.getDbValue(),
                null, from, null, Pageable.unpaged());

        List<String> columns = List.of("dn", "deletedBy", "deletedAt", "source");
        List<Map<String, String>> rows = new ArrayList<>();

        internalDeletes.getContent().forEach(e -> {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("dn",        e.getTargetDn() != null ? e.getTargetDn() : "");
            row.put("deletedBy", e.getActorUsername() != null ? e.getActorUsername() : "");
            row.put("deletedAt", e.getOccurredAt() != null ? e.getOccurredAt().toString() : "");
            row.put("source",    "INTERNAL");
            rows.add(row);
        });

        changelogDeletes.getContent().stream()
                .filter(e -> e.getDetail() != null && isDeleteChange(e.getDetail()))
                .forEach(e -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("dn",        e.getTargetDn() != null ? e.getTargetDn() : "");
                    row.put("deletedBy", "LDAP_CHANGELOG");
                    row.put("deletedAt", e.getOccurredAt() != null ? e.getOccurredAt().toString() : "");
                    row.put("source",    "LDAP_CHANGELOG");
                    rows.add(row);
                });

        return new ReportData(columns, rows);
    }

    private boolean isDeleteChange(Map<String, Object> detail) {
        Object changeType = detail.get("changeType");
        if (changeType != null && changeType.toString().equalsIgnoreCase("delete")) return true;
        Object changes = detail.get("changes");
        return changes != null && changes.toString().toLowerCase().contains("changetype: delete");
    }

    private ReportData runMissingProfileGroupsReportData(DirectoryConnection dc, UUID directoryId) {
        List<ProvisioningProfile> profiles =
                profileRepo.findAllByDirectoryIdAndEnabledTrue(directoryId);

        List<String> columns = List.of("userDn", "profileName", "missingGroupDn", "memberAttribute");
        List<Map<String, String>> rows = new ArrayList<>();

        for (ProvisioningProfile profile : profiles) {
            try {
                GroupChangePreview preview =
                        profileService.evaluateGroupChanges(directoryId, profile.getId());
                for (GroupChangePreview.UserGroupChange change : preview.changes()) {
                    for (GroupChangePreview.GroupChange add : change.groupsToAdd()) {
                        Map<String, String> row = new LinkedHashMap<>();
                        row.put("userDn", change.userDn());
                        row.put("profileName", profile.getName());
                        row.put("missingGroupDn", add.groupDn());
                        row.put("memberAttribute", add.memberAttribute());
                        rows.add(row);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate group changes for profile {}: {}",
                        profile.getName(), e.getMessage());
            }
        }

        return new ReportData(columns, rows);
    }

    private ReportData runSodViolationsReportData(UUID directoryId) {
        List<SodViolation> violations = sodViolationRepo.findByDirectoryId(directoryId);

        List<String> columns = List.of("userDn", "userDisplayName", "policyName",
                "groupADn", "groupBDn", "status", "detectedAt", "exemptedBy", "exemptionReason");
        List<Map<String, String>> rows = new ArrayList<>();

        for (SodViolation v : violations) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("userDn", v.getUserDn() != null ? v.getUserDn() : "");
            row.put("userDisplayName", v.getUserDisplayName() != null ? v.getUserDisplayName() : "");
            row.put("policyName", v.getPolicy().getName());
            row.put("groupADn", v.getPolicy().getGroupADn());
            row.put("groupBDn", v.getPolicy().getGroupBDn());
            row.put("status", v.getStatus().name());
            row.put("detectedAt", v.getDetectedAt() != null ? v.getDetectedAt().toString() : "");
            row.put("exemptedBy", v.getExemptedBy() != null ? v.getExemptedBy().getUsername() : "");
            row.put("exemptionReason", v.getExemptionReason() != null ? v.getExemptionReason() : "");
            rows.add(row);
        }

        return new ReportData(columns, rows);
    }

    private String buildGroupFilter(Map<String, Object> params) {
        String groupDn = requireString(params, "groupDn");
        return "(memberOf=" + Filter.encodeValue(groupDn) + ")";
    }

    private String lookbackTimestamp(Map<String, Object> params) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(lookbackDays(params));
        return cutoff.withOffsetSameInstant(java.time.ZoneOffset.UTC)
                     .format(LDAP_TIMESTAMP_FMT);
    }

    private int lookbackDays(Map<String, Object> params) {
        Object raw = params.get("lookbackDays");
        if (raw instanceof Number n) return n.intValue();
        if (raw instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { }
        }
        return 30;
    }

    private String requireString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(
                    "Report parameter '" + key + "' is required for this report type");
        }
        return value.toString();
    }
}
