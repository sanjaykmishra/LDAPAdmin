package com.ldapadmin.dto.csv;

import java.util.List;

/**
 * Preview result returned before a bulk CSV import is confirmed.
 * Contains the parsed rows with computed DNs but no LDAP writes have occurred.
 */
public record BulkImportPreviewResult(
        int totalRows,
        List<BulkImportPreviewRow> rows) {
}
