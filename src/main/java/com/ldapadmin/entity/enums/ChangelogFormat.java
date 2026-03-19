package com.ldapadmin.entity.enums;

/**
 * Identifies the changelog / audit-log format exposed by an LDAP server.
 */
public enum ChangelogFormat {

    /** Oracle DSEE / UnboundID — {@code cn=changelog} with {@code changeNumber}. */
    DSEE_CHANGELOG,

    /** OpenLDAP {@code slapo-accesslog} overlay — {@code cn=accesslog} with {@code reqStart}. */
    OPENLDAP_ACCESSLOG
}
