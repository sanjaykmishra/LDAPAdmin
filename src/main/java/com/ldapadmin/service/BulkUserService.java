package com.ldapadmin.service;

import com.ldapadmin.dto.csv.BulkImportResult;
import com.ldapadmin.dto.csv.BulkImportRowResult;
import com.ldapadmin.dto.csv.CsvColumnMappingDto;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.ConflictHandling;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.ldap.model.LdapUser;
import com.ldapadmin.util.CsvUtils;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV parsing and generation service for bulk user import and export operations.
 *
 * <h3>Import</h3>
 * <p>Reads a UTF-8 CSV stream (first row = column headers), maps each header
 * to an LDAP attribute via the supplied column mappings, then for every data
 * row either creates a new entry or updates/skips an existing one depending on
 * {@link ConflictHandling}.  All rows are processed regardless of individual
 * errors; the caller receives a per-row result list.</p>
 *
 * <h3>Export</h3>
 * <p>Searches the directory for matching entries, writes a header row followed
 * by one data row per entry, and returns the result as a UTF-8 {@code byte[]}.
 * Multi-valued LDAP attributes are serialised as pipe-separated strings.</p>
 *
 * <p>This service operates directly on {@link LdapUserService}; permission
 * checks are the caller's responsibility (see {@link LdapOperationService}).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkUserService {

    private final LdapUserService userService;

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Parses {@code csvInput} and applies creates/updates to the LDAP directory.
     *
     * @param dc              target directory connection
     * @param csvInput        raw CSV byte stream (UTF-8, first row = headers)
     * @param parentDn        DN of the container where new entries are created
     * @param targetKeyAttr   LDAP attribute whose value serves as the RDN (e.g. {@code uid})
     * @param conflictHandling action when an entry with the given key already exists
     * @param columnMappings  CSV column → LDAP attribute mapping;
     *                        empty list = use CSV header names as attribute names directly
     */
    public BulkImportResult importCsv(DirectoryConnection dc,
                                      InputStream csvInput,
                                      String parentDn,
                                      String targetKeyAttr,
                                      ConflictHandling conflictHandling,
                                      List<CsvColumnMappingDto> columnMappings) throws IOException {

        Map<String, String> colToAttr = resolveColumnMap(columnMappings);
        List<Map<String, String>> rows = CsvUtils.parse(csvInput);

        List<BulkImportRowResult> rowResults = new ArrayList<>();
        int rowNum = 0;

        for (Map<String, String> row : rows) {
            rowNum++;
            rowResults.add(processRow(dc, row, colToAttr, targetKeyAttr,
                    parentDn, conflictHandling, rowNum));
        }

        long created = countByStatus(rowResults, BulkImportRowResult.Status.CREATED);
        long updated = countByStatus(rowResults, BulkImportRowResult.Status.UPDATED);
        long skipped = countByStatus(rowResults, BulkImportRowResult.Status.SKIPPED);
        long errors  = countByStatus(rowResults, BulkImportRowResult.Status.ERROR);

        log.info("Bulk import complete: {} rows — created={}, updated={}, skipped={}, errors={}",
                rowNum, created, updated, skipped, errors);

        return new BulkImportResult(rowNum, created, updated, skipped, errors, rowResults);
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Searches the directory and serialises matching entries as CSV bytes.
     *
     * <p>Column order: {@code dn} is always the first column, followed by the
     * requested attributes in the order provided.  When {@code attributes} is
     * empty the column set is derived from all attribute names found across the
     * returned entries.</p>
     *
     * @param dc         directory connection
     * @param filter     LDAP filter (null = {@code (objectClass=*)})
     * @param baseDn     search base (null = directory base DN)
     * @param attributes LDAP attribute names to include as columns
     */
    public byte[] exportCsv(DirectoryConnection dc,
                             String filter,
                             String baseDn,
                             List<String> attributes) throws IOException {

        String effectiveFilter = (filter == null || filter.isBlank())
                ? "(objectClass=*)" : filter;
        String[] attrArray = attributes.isEmpty()
                ? new String[0]
                : attributes.toArray(new String[0]);

        List<LdapUser> users = userService.searchUsers(dc, effectiveFilter, baseDn, attrArray);

        List<String> columns = buildExportColumns(users, attributes);
        List<Map<String, String>> rows = users.stream()
                .map(u -> buildExportRow(u, columns))
                .toList();

        return CsvUtils.write(columns, rows);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Processes a single CSV data row: builds the LDAP attribute map, checks
     * whether the target entry already exists, and applies the appropriate
     * create/update/skip action.
     */
    private BulkImportRowResult processRow(DirectoryConnection dc,
                                           Map<String, String> row,
                                           Map<String, String> colToAttr,
                                           String targetKeyAttr,
                                           String parentDn,
                                           ConflictHandling conflictHandling,
                                           int rowNum) {
        // Build ldapAttribute→[value] map from the CSV row
        Map<String, List<String>> attrMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> cell : row.entrySet()) {
            String csvCol  = cell.getKey();
            String rawVal  = cell.getValue();
            if (rawVal == null || rawVal.isBlank()) continue;

            // colToAttr: null value = explicitly ignored; absent key = passthrough
            String ldapAttr;
            if (colToAttr.containsKey(csvCol)) {
                ldapAttr = colToAttr.get(csvCol);
                if (ldapAttr == null) continue; // ignored column
            } else {
                ldapAttr = csvCol; // passthrough: header IS the attribute name
            }

            attrMap.put(ldapAttr, List.of(rawVal));
        }

        // The key attribute value drives both DN construction and duplicate detection
        List<String> keyValues = attrMap.get(targetKeyAttr);
        if (keyValues == null || keyValues.isEmpty()) {
            return BulkImportRowResult.error(rowNum, null,
                    "Missing value for key attribute '" + targetKeyAttr + "'");
        }
        String keyValue = keyValues.get(0);
        String dn = buildDn(targetKeyAttr, keyValue, parentDn);

        try {
            boolean exists;
            try {
                userService.getUser(dc, dn);
                exists = true;
            } catch (ResourceNotFoundException e) {
                exists = false;
            }

            if (exists) {
                if (conflictHandling == ConflictHandling.OVERWRITE) {
                    List<Modification> mods = attrMap.entrySet().stream()
                            .filter(e -> !e.getKey().equals(targetKeyAttr))
                            .map(e -> new Modification(
                                    ModificationType.REPLACE,
                                    e.getKey(),
                                    e.getValue().toArray(new String[0])))
                            .toList();
                    if (!mods.isEmpty()) {
                        userService.updateUser(dc, dn, mods);
                    }
                    return BulkImportRowResult.updated(rowNum, dn);
                } else {
                    // SKIP or PROMPT — no action taken
                    return BulkImportRowResult.skipped(rowNum, dn, "Entry already exists");
                }
            } else {
                userService.createUser(dc, dn, attrMap);
                return BulkImportRowResult.created(rowNum, dn);
            }

        } catch (Exception ex) {
            log.warn("Row {} failed [dn={}]: {}", rowNum, dn, ex.getMessage());
            return BulkImportRowResult.error(rowNum, dn, ex.getMessage());
        }
    }

    /**
     * Builds a lookup map from CSV column name to LDAP attribute name.
     * A {@code null} value in the returned map signals "ignore this column".
     * Columns absent from the map are handled as passthrough.
     */
    private Map<String, String> resolveColumnMap(List<CsvColumnMappingDto> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (CsvColumnMappingDto m : mappings) {
            if (m.ignored()) {
                result.put(m.csvColumn(), null);
            } else {
                result.put(m.csvColumn(),
                        m.ldapAttribute() != null ? m.ldapAttribute() : m.csvColumn());
            }
        }
        return result;
    }

    /** Constructs the full DN for a new entry: {@code rdnAttr=rdnValue,parentDn}. */
    private String buildDn(String rdnAttr, String rdnValue, String parentDn) {
        return rdnAttr + "=" + rdnValue + "," + parentDn;
    }

    /**
     * Determines the ordered column list for the export CSV.
     * {@code dn} is always first; remaining columns come from {@code requestedAttrs}
     * or, if empty, from all attribute names found in the returned entries.
     */
    private List<String> buildExportColumns(List<LdapUser> users, List<String> requestedAttrs) {
        List<String> cols = new ArrayList<>();
        cols.add("dn");
        if (!requestedAttrs.isEmpty()) {
            cols.addAll(requestedAttrs);
        } else {
            users.stream()
                    .flatMap(u -> u.getAttributes().keySet().stream())
                    .distinct()
                    .forEach(cols::add);
        }
        return cols;
    }

    /** Builds a single CSV data row from an LDAP entry. Multi-values are pipe-joined. */
    private Map<String, String> buildExportRow(LdapUser user, List<String> columns) {
        Map<String, String> row = new LinkedHashMap<>();
        for (String col : columns) {
            if ("dn".equals(col)) {
                row.put("dn", user.getDn());
            } else {
                List<String> vals = user.getValues(col);
                row.put(col, String.join("|", vals));
            }
        }
        return row;
    }

    private long countByStatus(List<BulkImportRowResult> results,
                                BulkImportRowResult.Status status) {
        return results.stream().filter(r -> r.status() == status).count();
    }
}
