package com.ldapadmin.service.siem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldapadmin.entity.AuditEvent;
import com.ldapadmin.entity.enums.SiemFormat;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts {@link AuditEvent} instances into SIEM-consumable string formats.
 */
@Component
public class SiemFormatter {

    private static final String CEF_VERSION = "0";
    private static final String DEVICE_VENDOR = "LDAPAdmin";
    private static final String DEVICE_PRODUCT = "LDAPAdmin";
    private static final String DEVICE_VERSION = "1.0";

    private final ObjectMapper objectMapper;

    public SiemFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Formats an audit event into the requested output format.
     */
    public String format(AuditEvent event, SiemFormat fmt) {
        return switch (fmt) {
            case RFC5424 -> toRfc5424(event);
            case CEF -> toCef(event);
            case JSON -> toJson(event);
        };
    }

    // ── RFC 5424 syslog ─────────────────────────────────────────────────────

    /**
     * RFC 5424 format:
     * {@code <priority>1 timestamp hostname app-name procid msgid [sd-id params] msg}
     */
    private String toRfc5424(AuditEvent event) {
        // PRI = facility(16=local0) * 8 + severity(6=info) = 134
        int priority = 134;
        String timestamp = event.getOccurredAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String hostname = "-";
        String appName = "LDAPAdmin";
        String procId = "-";
        String msgId = event.getAction().name();

        // Structured data
        StringBuilder sd = new StringBuilder();
        sd.append("[event@0 ");
        sd.append("id=\"").append(event.getId()).append("\" ");
        sd.append("action=\"").append(event.getAction().name()).append("\" ");
        sd.append("source=\"").append(event.getSource().name()).append("\"");
        if (event.getActorUsername() != null) {
            sd.append(" actor=\"").append(escapeSD(event.getActorUsername())).append("\"");
        }
        if (event.getDirectoryName() != null) {
            sd.append(" directory=\"").append(escapeSD(event.getDirectoryName())).append("\"");
        }
        if (event.getTargetDn() != null) {
            sd.append(" targetDn=\"").append(escapeSD(event.getTargetDn())).append("\"");
        }
        sd.append("]");

        // BOM + message body
        String msg = event.getAction().name();
        if (event.getTargetDn() != null) {
            msg += " " + event.getTargetDn();
        }

        return String.format("<%d>1 %s %s %s %s %s %s %s",
                priority, timestamp, hostname, appName, procId, msgId, sd, msg);
    }

    /** Escape SD-PARAM values per RFC 5424 §6.3.3: escape \, ", ] */
    private String escapeSD(String value) {
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("]", "\\]");
    }

    // ── CEF (Common Event Format) ───────────────────────────────────────────

    /**
     * CEF format:
     * {@code CEF:0|vendor|product|version|signatureId|name|severity|extension}
     */
    private String toCef(AuditEvent event) {
        String signatureId = event.getAction().name();
        String name = event.getAction().name();
        int severity = cefSeverity(event);

        StringBuilder ext = new StringBuilder();
        ext.append("rt=").append(event.getOccurredAt().toInstant().toEpochMilli());
        if (event.getActorUsername() != null) {
            ext.append(" suser=").append(escapeCef(event.getActorUsername()));
        }
        if (event.getTargetDn() != null) {
            ext.append(" cs1=").append(escapeCef(event.getTargetDn()));
            ext.append(" cs1Label=targetDn");
        }
        if (event.getDirectoryName() != null) {
            ext.append(" cs2=").append(escapeCef(event.getDirectoryName()));
            ext.append(" cs2Label=directoryName");
        }
        if (event.getSource() != null) {
            ext.append(" cs3=").append(event.getSource().name());
            ext.append(" cs3Label=source");
        }

        return String.format("CEF:%s|%s|%s|%s|%s|%s|%s|%s",
                CEF_VERSION, DEVICE_VENDOR, DEVICE_PRODUCT, DEVICE_VERSION,
                signatureId, name, severity, ext);
    }

    private int cefSeverity(AuditEvent event) {
        return switch (event.getAction().name()) {
            case String s when s.contains("DELETE") || s.contains("REVOKE") -> 7;
            case String s when s.contains("CREATE") || s.contains("UPDATE") -> 3;
            default -> 5;
        };
    }

    /** Escape CEF extension values: backslash, equals, newlines. */
    private String escapeCef(String value) {
        return value.replace("\\", "\\\\")
                    .replace("=", "\\=")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
    }

    // ── JSON ────────────────────────────────────────────────────────────────

    private String toJson(AuditEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", event.getId());
        map.put("timestamp", event.getOccurredAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        map.put("source", event.getSource().name());
        map.put("action", event.getAction().name());
        map.put("actorId", event.getActorId());
        map.put("actorUsername", event.getActorUsername());
        map.put("actorType", event.getActorType());
        map.put("directoryId", event.getDirectoryId());
        map.put("directoryName", event.getDirectoryName());
        map.put("targetDn", event.getTargetDn());
        map.put("detail", event.getDetail());
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            // Fallback: minimal JSON
            return "{\"id\":\"" + event.getId() + "\",\"action\":\"" + event.getAction().name() + "\"}";
        }
    }
}
