package com.ldapadmin.entity.enums;

/**
 * All supported alert rule types for continuous access monitoring.
 */
public enum AlertRuleType {

    // ── Separation of Duties ─────────────────────────────────────────────
    SOD_VIOLATION_NEW,
    SOD_VIOLATION_UNRESOLVED,
    SOD_EXEMPTION_EXPIRING,

    // ── Access Reviews ───────────────────────────────────────────────────
    CAMPAIGN_DEADLINE_APPROACHING,
    CAMPAIGN_OVERDUE,
    REVIEWER_INACTIVE,
    USER_NOT_REVIEWED,

    // ── Privileged Access ────────────────────────────────────────────────
    PRIVILEGED_GROUP_ADDITION,
    ADMIN_ACCOUNT_CREATED,
    BULK_GROUP_ADDITION,

    // ── Account Lifecycle ────────────────────────────────────────────────
    DISABLED_ACCOUNT_IN_GROUPS,
    DORMANT_ACCOUNT,
    ORPHANED_ACCOUNT,
    ACCOUNT_POST_TERMINATION,

    // ── Approvals ────────────────────────────────────────────────────────
    APPROVAL_STALE,
    PROVISIONING_FAILURE,

    // ── Directory Health ─────────────────────────────────────────────────
    DIRECTORY_UNREACHABLE,
    CHANGELOG_GAP,
    HIGH_CHANGE_VOLUME,

    // ── Compliance ───────────────────────────────────────────────────────
    SCHEDULED_REPORT_FAILURE,
    AUDITOR_LINK_EXPIRING,
}
