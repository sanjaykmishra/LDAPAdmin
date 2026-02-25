package com.ldapadmin.service;

import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.entity.enums.OutputFormat;
import com.ldapadmin.entity.enums.ReportType;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.repository.AuditEventRepository;
import com.ldapadmin.util.CsvUtils;
import com.unboundid.ldap.sdk.Filter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

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

    private final LdapUserService      userService;
    private final AuditEventRepository auditEventRepo;

    /**
     * Runs the report and returns the result as UTF-8 CSV bytes.
     *
     * @param dc         target directory connection
     * @param reportType the type of report to run
     * @param params     report-specific parameters (may be null/empty)
     * @param format     output format (only CSV is supported)
     * @param directoryId  UUID of the directory (used for audit queries)
     * @param tenantId   UUID of the tenant (used for audit queries)
     */
    public byte[] run(DirectoryConnection dc,
                      ReportType reportType,
                      Map<String, Object> params,
                      OutputFormat format,
                      UUID directoryId,
                      UUID tenantId) throws IOException {

        if (format == OutputFormat.PDF) {
            throw new UnsupportedOperationException("PDF output is not yet supported");
        }

        Map<String, Object> safeParams = params != null ? params : Map.of();

        return switch (reportType) {
            case USERS_IN_GROUP      -> runLdapReport(dc, buildGroupFilter(safeParams), null);
            case USERS_IN_BRANCH     -> runLdapReport(dc, "(objectClass=inetOrgPerson)",
                                                       requireString(safeParams, "branchDn"));
            case USERS_WITH_NO_GROUP -> runLdapReport(dc,
                                                       "(&(objectClass=inetOrgPerson)(!(memberOf=*)))", null);
            case RECENTLY_ADDED      -> runLdapReport(dc,
                                                       "(createTimestamp>=" + lookbackTimestamp(safeParams) + ")", null);
            case RECENTLY_MODIFIED   -> runLdapReport(dc,
                                                       "(modifyTimestamp>=" + lookbackTimestamp(safeParams) + ")", null);
            case RECENTLY_DELETED    -> runDeletedReport(directoryId, tenantId, safeParams);
            case DISABLED_ACCOUNTS   -> runLdapReport(dc,
                                                       "(|(pwdAccountLockedTime=*)(loginDisabled=TRUE))", null);
        };
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Queries LDAP and serialises the result as CSV.
     * Returns {@code dn} + all attributes found across the result set.
     */
    private byte[] runLdapReport(DirectoryConnection dc, String filter,
                                   String baseDn) throws IOException {
        List<LdapUser> users = userService.searchUsers(dc, filter, baseDn);

        // Derive column set: dn first, then all unique attribute names
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
        return CsvUtils.write(columns, rows);
    }

    /** Builds a CSV row from a single LDAP entry; multi-values are pipe-joined. */
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
     * Returns a CSV with columns: {@code dn}, {@code deletedBy}, {@code deletedAt}.
     */
    private byte[] runDeletedReport(UUID directoryId, UUID tenantId,
                                     Map<String, Object> params) throws IOException {
        int lookbackDays = lookbackDays(params);
        OffsetDateTime from = OffsetDateTime.now().minusDays(lookbackDays);

        var page = auditEventRepo.findByTenant(
                tenantId, directoryId, null, AuditAction.USER_DELETE,
                from, null, Pageable.unpaged());

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

        return CsvUtils.write(columns, rows);
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
