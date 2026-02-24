package com.ldapadmin.entity.enums;

import java.util.Arrays;

/**
 * All recordable audit actions.
 * DB values use dot-notation; a custom JPA converter handles the mapping.
 */
public enum AuditAction {

    // ── User operations ───────────────────────────────────────────────────────
    USER_CREATE          ("user.create"),
    USER_UPDATE          ("user.update"),
    USER_DELETE          ("user.delete"),
    USER_ENABLE          ("user.enable"),
    USER_DISABLE         ("user.disable"),
    USER_MOVE            ("user.move"),

    // ── Group operations ──────────────────────────────────────────────────────
    GROUP_CREATE         ("group.create"),
    GROUP_DELETE         ("group.delete"),
    GROUP_MEMBER_ADD     ("group.member_add"),
    GROUP_MEMBER_REMOVE  ("group.member_remove"),

    // ── Changelog-sourced (raw LDAP changelog entry) ──────────────────────────
    LDAP_CHANGE          ("ldap.change");

    private final String dbValue;

    AuditAction(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static AuditAction fromDbValue(String value) {
        return Arrays.stream(values())
                .filter(a -> a.dbValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown audit action: " + value));
    }
}
