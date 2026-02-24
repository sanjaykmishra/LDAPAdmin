package com.ldapadmin.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal RFC 4180 CSV parser and writer — no third-party dependencies required.
 *
 * <h3>Parse</h3>
 * <ul>
 *   <li>First row is treated as a header row; subsequent rows become ordered
 *       maps of {@code header → value}.</li>
 *   <li>Fields may be optionally enclosed in double-quotes.</li>
 *   <li>Embedded double-quotes inside a quoted field are doubled ({@code ""}).</li>
 *   <li>Multi-line quoted values spanning physical newlines are NOT supported;
 *       newlines within a field must be represented by the caller.</li>
 * </ul>
 *
 * <h3>Write</h3>
 * <ul>
 *   <li>All cell values are double-quoted for portability.</li>
 *   <li>Embedded double-quotes are escaped as {@code ""}.</li>
 *   <li>Lines are terminated with {@code \r\n} per RFC 4180.</li>
 * </ul>
 */
public final class CsvUtils {

    private CsvUtils() {}

    // ── Parse ─────────────────────────────────────────────────────────────────

    /**
     * Parses a UTF-8 CSV {@link InputStream} into a list of row maps.
     * The first row is consumed as column headers.
     *
     * @return ordered list of row maps; empty list if the stream has no data rows
     * @throws IOException on I/O errors
     */
    public static List<Map<String, String>> parse(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8));

        List<String[]> rawRows = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                rawRows.add(parseRow(line));
            }
        }

        if (rawRows.isEmpty()) {
            return Collections.emptyList();
        }

        String[] headers = rawRows.get(0);
        List<Map<String, String>> result = new ArrayList<>();
        for (int r = 1; r < rawRows.size(); r++) {
            String[] row = rawRows.get(r);
            Map<String, String> map = new LinkedHashMap<>();
            for (int c = 0; c < headers.length; c++) {
                map.put(headers[c], c < row.length ? row[c] : "");
            }
            result.add(map);
        }
        return result;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Serialises a list of row maps into a UTF-8 CSV {@code byte[]}.
     * The {@code headers} list controls column order and produces the first row.
     *
     * @param headers column names in the desired order
     * @param rows    data rows; missing keys produce empty cells
     */
    public static byte[] write(List<String> headers, List<Map<String, String>> rows) {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, headers);
        for (Map<String, String> row : rows) {
            List<String> values = new ArrayList<>(headers.size());
            for (String h : headers) {
                values.add(row.getOrDefault(h, ""));
            }
            appendRow(sb, values);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Appends a single RFC 4180 CSV row (all fields quoted). */
    private static void appendRow(StringBuilder sb, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            String v = values.get(i) != null ? values.get(i) : "";
            sb.append('"');
            sb.append(v.replace("\"", "\"\""));
            sb.append('"');
        }
        sb.append("\r\n");
    }

    /**
     * Parses a single CSV line into an array of field values.
     * Handles quoted fields with embedded commas and doubled double-quotes.
     */
    static String[] parseRow(String line) {
        List<String> fields = new ArrayList<>();
        int i = 0;
        while (i <= line.length()) {
            if (i == line.length()) {
                // Trailing comma produced an empty last field
                fields.add("");
                break;
            }

            if (line.charAt(i) == '"') {
                // Quoted field
                i++; // skip opening quote
                StringBuilder field = new StringBuilder();
                while (i < line.length()) {
                    char c = line.charAt(i);
                    if (c == '"') {
                        if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                            field.append('"'); // escaped double-quote
                            i += 2;
                        } else {
                            i++; // closing quote
                            break;
                        }
                    } else {
                        field.append(c);
                        i++;
                    }
                }
                fields.add(field.toString());
                if (i < line.length() && line.charAt(i) == ',') {
                    i++; // skip delimiter
                }

            } else {
                // Unquoted field — read until next comma or end
                int start = i;
                while (i < line.length() && line.charAt(i) != ',') {
                    i++;
                }
                fields.add(line.substring(start, i));
                if (i < line.length()) {
                    i++; // skip comma
                }
            }
        }
        return fields.toArray(new String[0]);
    }
}
