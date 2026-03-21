package com.ldapadmin.dto.ldap;

import java.util.List;

/**
 * Result of a bulk attribute update operation.
 */
public record BulkAttributeUpdateResult(
        int updated,
        int errors,
        List<BulkUpdateError> failures) {

    public record BulkUpdateError(String dn, String message) {}
}
