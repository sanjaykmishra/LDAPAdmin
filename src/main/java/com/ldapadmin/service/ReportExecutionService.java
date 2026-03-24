package com.ldapadmin.service;

import com.ldapadmin.dto.profile.GroupChangePreview;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.ProvisioningProfile;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.entity.enums.OutputFormat;
import com.ldapadmin.entity.enums.ReportType;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.AuditEventRepository;
import com.ldapadmin.repository.ProvisioningProfileRepository;
import com.ldapadmin.util.CsvUtils;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.unboundid.ldap.sdk.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executes a report on-demand and returns the results as a CSV byte array (§9.1).
 *
 * <h3>Report types and their LDAP queries</h3>
 * <ul>
 *   <li><b>USERS_IN_GROUP</b>     — {@code (memberOf=<groupDn>)},
 *       base = directory base DN; param {@code groupDn} required.</li>
 *   <li><b>USERS_IN_BRANCH</b>    — {@code (objectClass=inetOrgPerson)},
 *       base = {@code branchDn}; param {@code branchDn} required.</li>
 *   <li><b>USERS_WITH_NO_GROUP</b>— {@code (&(objectClass=inetOrgPerson)(!(memberOf=*)))},
 *       base = directory base DN; no params.</li>
 *   <li><b>RECENTLY_ADDED</b>     — {@code (createTimestamp>=<timestamp>)},
 *       base = directory base DN; param {@code lookbackDays} (default 30).</li>
 *   <li><b>RECENTLY_MODIFIED</b>  — {@code (modifyTimestamp>=<timestamp>)},
 *       base = directory base DN; param {@code lookbackDays} (default 30).</li>
 *   <li><b>RECENTLY_DELETED</b>   — queries the internal audit events table for
 *       {@link AuditAction#USER_DELETE} events within {@code lookbackDays};
 *       returns dn + actorUsername + occurredAt columns.</li>
 *   <li><b>DISABLED_ACCOUNTS</b>  — {@code (|(pwdAccountLockedTime=*)(loginDisabled=TRUE))},
 *       base = directory base DN; no params.</li>
 *   <li><b>MISSING_PROFILE_GROUPS</b> — for each provisioning profile in the directory,
 *       compares users' actual group memberships against the profile's effective group
 *       set (own + additional + auto-include). Returns userDn, profileName,
 *       missingGroupDn, memberAttribute columns; no params.</li>
 * </ul>
 *
 * <p>{@link OutputFormat#PDF} is not currently supported and will throw
 * {@link UnsupportedOperationException}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportExecutionService {

    private static final DateTimeFormatter LDAP_TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'");

    private final LdapUserService                userService;
    private final AuditEventRepository           auditEventRepo;
    private final ProvisioningProfileRepository  profileRepo;
    private final ProvisioningProfileService     profileService;

    /**
     * Runs the report and returns the result as UTF-8 CSV bytes.
     *
     * @param dc         target directory connection
     * @param reportType the type of report to run
     * @param params     report-specific parameters (may be null/empty)
     * @param format     output format (only CSV is supported)
     * @param directoryId  UUID of the directory (used for audit queries)
     */
    public byte[] run(DirectoryConnection dc,
                      ReportType reportType,
                      Map<String, Object> params,
                      OutputFormat format,
                      UUID directoryId) throws IOException {

        Map<String, Object> safeParams = params != null ? params : Map.of();

        // Gather CSV-style row data (shared by both CSV and PDF output)
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
        };

        if (format == OutputFormat.PDF) {
            return renderPdf(reportType.name(), data);
        }
        return CsvUtils.write(data.columns, data.rows);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Intermediate report data container (columns + rows). */
    record ReportData(List<String> columns, List<Map<String, String>> rows) {}

    /**
     * Queries LDAP and returns structured report data.
     */
    private ReportData runLdapReportData(DirectoryConnection dc, String filter,
                                         String baseDn) {
        List<LdapUser> users = userService.searchUsers(dc, filter, baseDn);

        List<String> columns = new ArrayList<>();
        columns.add("dn");
        users.stream()
                .flatMap(u -> u.getAttributes().keySet().stream())
                .distinct()
                .forEach(columns::add);

        List<Map<String, String>> rows = users.stream()
                .map(u -> buildRow(u, columns))
                .toList();

        log.debug("Report query [filter={}, base={}] → {} entries", filter, baseDn, rows.size());
        return new ReportData(columns, rows);
    }

    /** Builds a row from a single LDAP entry; multi-values are pipe-joined. */
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
     * Queries the internal audit events table for USER_DELETE actions.
     */
    private ReportData runDeletedReportData(UUID directoryId,
                                            Map<String, Object> params) {
        int lookbackDays = lookbackDays(params);
        OffsetDateTime from = OffsetDateTime.now().minusDays(lookbackDays);

        var page = auditEventRepo.findAll(
                directoryId, null, AuditAction.USER_DELETE.getDbValue(),
                null, from, null, Pageable.unpaged());

        List<String> columns = List.of("dn", "deletedBy", "deletedAt");
        List<Map<String, String>> rows = page.getContent().stream()
                .map(e -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("dn",        e.getTargetDn() != null ? e.getTargetDn() : "");
                    row.put("deletedBy", e.getActorUsername() != null ? e.getActorUsername() : "");
                    row.put("deletedAt", e.getOccurredAt() != null
                            ? e.getOccurredAt().toString() : "");
                    return row;
                })
                .toList();

        return new ReportData(columns, rows);
    }

    /**
     * For each enabled profile in the directory, evaluates the group membership
     * diff and collects missing groups into a flat report.
     */
    private ReportData runMissingProfileGroupsReportData(DirectoryConnection dc,
                                                         UUID directoryId) {
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

        log.debug("Missing profile groups report → {} rows across {} profiles",
                rows.size(), profiles.size());
        return new ReportData(columns, rows);
    }

    /**
     * Renders report data as a styled PDF document.
     */
    private byte[] renderPdf(String reportTitle, ReportData data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);

        Font titleFont  = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(33, 37, 41));
        Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
        Font cellFont   = new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(33, 37, 41));
        Color headerBg  = new Color(52, 58, 64);
        Color altRowBg  = new Color(248, 249, 250);

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            Paragraph title = new Paragraph(reportTitle.replace('_', ' '), titleFont);
            title.setSpacingAfter(12);
            document.add(title);

            if (data.columns.isEmpty()) {
                document.add(new Paragraph("No data available.", cellFont));
            } else {
                PdfPTable table = new PdfPTable(data.columns.size());
                table.setWidthPercentage(100);

                for (String col : data.columns) {
                    PdfPCell cell = new PdfPCell(new Phrase(col, headerFont));
                    cell.setBackgroundColor(headerBg);
                    cell.setPadding(6);
                    table.addCell(cell);
                }

                int rowIdx = 0;
                for (Map<String, String> row : data.rows) {
                    Color bg = (rowIdx % 2 == 1) ? altRowBg : Color.WHITE;
                    for (String col : data.columns) {
                        String value = row.getOrDefault(col, "");
                        PdfPCell cell = new PdfPCell(new Phrase(value, cellFont));
                        cell.setBackgroundColor(bg);
                        cell.setPadding(5);
                        table.addCell(cell);
                    }
                    rowIdx++;
                }

                document.add(table);
            }

            document.close();
        } catch (DocumentException e) {
            throw new IOException("Failed to generate PDF: " + e.getMessage(), e);
        }

        return baos.toByteArray();
    }

    private String buildGroupFilter(Map<String, Object> params) {
        String groupDn = requireString(params, "groupDn");
        // Escape the DN so LDAP special characters (*, \, (, ), \0) can't corrupt the filter
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
