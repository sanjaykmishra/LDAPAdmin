package com.ldapadmin.dto.csv;

import java.util.Map;

/**
 * A single preview row showing the computed DN and attribute values
 * that would be written during a bulk import.
 */
public record BulkImportPreviewRow(
        int rowNumber,
        String computedDn,
        Map<String, String> attributes) {
}
