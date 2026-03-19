package com.ldapadmin.ldap;

import com.ldapadmin.dto.ldap.IntegrityReport;
import com.ldapadmin.dto.ldap.IntegrityReport.IntegrityIssue;
import com.ldapadmin.dto.ldap.IntegrityReport.IssueType;
import com.ldapadmin.entity.DirectoryConnection;
import com.unboundid.ldap.sdk.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Referential integrity checker for LDAP directories.
 * Searches for broken member references, orphaned entries, and empty groups.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IntegrityCheckService {

    private final LdapConnectionFactory connectionFactory;

    /**
     * Runs the selected integrity checks against the directory.
     *
     * @param dc      directory connection
     * @param baseDn  base DN to search under
     * @param checks  which checks to run (BROKEN_MEMBER, ORPHANED_ENTRY, EMPTY_GROUP)
     * @return report with all issues found
     */
    public IntegrityReport runChecks(DirectoryConnection dc, String baseDn,
                                     Set<IssueType> checks) {
        String searchBase = (baseDn != null && !baseDn.isBlank()) ? baseDn : dc.getBaseDn();

        return connectionFactory.withConnection(dc, conn -> {
            List<IntegrityIssue> issues = new ArrayList<>();

            if (checks.contains(IssueType.BROKEN_MEMBER)) {
                issues.addAll(checkBrokenMembers(conn, searchBase));
            }
            if (checks.contains(IssueType.ORPHANED_ENTRY)) {
                issues.addAll(checkOrphanedEntries(conn, searchBase));
            }
            if (checks.contains(IssueType.EMPTY_GROUP)) {
                issues.addAll(checkEmptyGroups(conn, searchBase));
            }

            log.info("Integrity check on '{}': {} issues found", searchBase, issues.size());
            return new IntegrityReport(issues);
        });
    }

    /**
     * Finds entries with member/uniqueMember attributes that reference DNs
     * which do not exist in the directory.
     */
    private List<IntegrityIssue> checkBrokenMembers(LDAPConnection conn,
                                                     String baseDn) throws LDAPException {
        List<IntegrityIssue> issues = new ArrayList<>();

        // Search for all entries that have member or uniqueMember attributes
        SearchRequest request = new SearchRequest(
                baseDn, SearchScope.SUB,
                "(|(member=*)(uniqueMember=*))",
                "member", "uniqueMember");
        request.setSizeLimit(1000);

        SearchResult result;
        try {
            result = conn.search(request);
        } catch (LDAPSearchException e) {
            if (e.getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                return issues;
            }
            throw e;
        }

        for (SearchResultEntry entry : result.getSearchEntries()) {
            checkMemberAttribute(conn, entry, "member", issues);
            checkMemberAttribute(conn, entry, "uniqueMember", issues);
        }

        return issues;
    }

    private void checkMemberAttribute(LDAPConnection conn, SearchResultEntry entry,
                                       String attrName, List<IntegrityIssue> issues)
            throws LDAPException {
        String[] values = entry.getAttributeValues(attrName);
        if (values == null) return;

        for (String memberDn : values) {
            if (memberDn.isBlank()) continue;
            try {
                SearchResultEntry memberEntry = conn.getEntry(memberDn, "1.1");
                if (memberEntry == null) {
                    issues.add(new IntegrityIssue(
                            IssueType.BROKEN_MEMBER,
                            entry.getDN(),
                            "Attribute '" + attrName + "' references non-existent DN: " + memberDn));
                }
            } catch (LDAPException e) {
                if (e.getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                    issues.add(new IntegrityIssue(
                            IssueType.BROKEN_MEMBER,
                            entry.getDN(),
                            "Attribute '" + attrName + "' references non-existent DN: " + memberDn));
                }
                // Other errors (e.g. insufficient access) — skip silently
            }
        }
    }

    /**
     * Finds entries whose parent DN does not exist in the directory.
     * The base DN itself is excluded from this check.
     */
    private List<IntegrityIssue> checkOrphanedEntries(LDAPConnection conn,
                                                       String baseDn) throws LDAPException {
        List<IntegrityIssue> issues = new ArrayList<>();

        SearchRequest request = new SearchRequest(
                baseDn, SearchScope.SUB,
                "(objectClass=*)",
                "1.1");
        request.setSizeLimit(5000);

        SearchResult result;
        try {
            result = conn.search(request);
        } catch (LDAPSearchException e) {
            if (e.getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                return issues;
            }
            throw e;
        }

        // Collect all DNs in a set for quick lookup
        Set<String> allDns = new HashSet<>();
        for (SearchResultEntry entry : result.getSearchEntries()) {
            allDns.add(entry.getDN().toLowerCase());
        }

        for (SearchResultEntry entry : result.getSearchEntries()) {
            String dn = entry.getDN();
            // Skip the base DN itself
            if (dn.equalsIgnoreCase(baseDn)) continue;

            String parentDn = extractParentDn(dn);
            if (parentDn != null && !allDns.contains(parentDn.toLowerCase())) {
                issues.add(new IntegrityIssue(
                        IssueType.ORPHANED_ENTRY,
                        dn,
                        "Parent DN does not exist: " + parentDn));
            }
        }

        return issues;
    }

    /**
     * Finds group entries (groupOfNames, groupOfUniqueNames, posixGroup)
     * that have no members.
     */
    private List<IntegrityIssue> checkEmptyGroups(LDAPConnection conn,
                                                   String baseDn) throws LDAPException {
        List<IntegrityIssue> issues = new ArrayList<>();

        // Search for group entries
        SearchRequest request = new SearchRequest(
                baseDn, SearchScope.SUB,
                "(|(objectClass=groupOfNames)(objectClass=groupOfUniqueNames)(objectClass=posixGroup))",
                "member", "uniqueMember", "memberUid");
        request.setSizeLimit(1000);

        SearchResult result;
        try {
            result = conn.search(request);
        } catch (LDAPSearchException e) {
            if (e.getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                return issues;
            }
            throw e;
        }

        for (SearchResultEntry entry : result.getSearchEntries()) {
            boolean hasMembers = false;

            for (String attr : List.of("member", "uniqueMember", "memberUid")) {
                String[] values = entry.getAttributeValues(attr);
                if (values != null && values.length > 0) {
                    hasMembers = true;
                    break;
                }
            }

            if (!hasMembers) {
                issues.add(new IntegrityIssue(
                        IssueType.EMPTY_GROUP,
                        entry.getDN(),
                        "Group has no members"));
            }
        }

        return issues;
    }

    private String extractParentDn(String dn) {
        int idx = dn.indexOf(',');
        if (idx < 0 || idx + 1 >= dn.length()) {
            return null;
        }
        return dn.substring(idx + 1);
    }
}
