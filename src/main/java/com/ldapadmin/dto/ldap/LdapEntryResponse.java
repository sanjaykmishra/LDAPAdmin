package com.ldapadmin.dto.ldap;

import com.ldapadmin.ldap.model.LdapEntry;

import java.util.List;
import java.util.Map;

/**
 * Generic LDAP entry response — used for both user and group entries.
 * The attribute map contains all attributes returned by the directory;
 * keys are lower-cased, values are multi-valued string lists.
 */
public record LdapEntryResponse(
        String dn,
        Map<String, List<String>> attributes) {

    public static LdapEntryResponse from(LdapEntry entry) {
        return new LdapEntryResponse(entry.getDn(), entry.getAttributes());
    }
}
