package com.ldapadmin.ldap;

import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.exception.LdapOperationException;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Browses the LDAP Directory Information Tree (DIT) using one-level searches.
 *
 * <p>Designed for the superadmin directory browser — returns direct children of
 * a given DN and determines whether each child has sub-entries of its own
 * (so the UI can show expand/collapse arrows).</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LdapBrowseService {

    private final LdapConnectionFactory connectionFactory;

    /**
     * Fetches the entry at {@code dn} together with its direct children.
     *
     * @param dc   directory connection
     * @param dn   the base DN to browse (null falls back to directory base DN)
     * @return browse result containing the entry's attributes and child list
     */
    public BrowseResult browse(DirectoryConnection dc, String dn) {
        String baseDn = (dn != null && !dn.isBlank()) ? dn : dc.getBaseDn();

        return connectionFactory.withConnection(dc, conn -> {
            // 1. Read the entry itself
            Map<String, List<String>> attributes = readEntry(conn, baseDn);

            // 2. One-level search to find direct children
            List<ChildEntry> children = listChildren(conn, dc, baseDn);

            return new BrowseResult(baseDn, attributes, children);
        });
    }

    private Map<String, List<String>> readEntry(LDAPConnection conn, String dn)
            throws LDAPException {
        SearchResultEntry entry = conn.getEntry(dn);
        if (entry == null) {
            return Map.of();
        }
        Map<String, List<String>> attrs = new LinkedHashMap<>();
        for (var attr : entry.getAttributes()) {
            attrs.put(attr.getBaseName(), Arrays.asList(attr.getValues()));
        }
        return attrs;
    }

    private List<ChildEntry> listChildren(LDAPConnection conn,
                                           DirectoryConnection dc,
                                           String baseDn) throws LDAPException {
        List<ChildEntry> children = new ArrayList<>();

        try {
            SearchRequest request = new SearchRequest(
                    baseDn, SearchScope.ONE,
                    Filter.createPresenceFilter("objectClass"),
                    "dn");
            request.addControl(new SimplePagedResultsControl(dc.getPagingSize(), null));

            SearchResult result = conn.search(request);
            for (SearchResultEntry child : result.getSearchEntries()) {
                String childDn = child.getDN();
                String rdn = extractRdn(childDn, baseDn);
                boolean hasChildren = hasSubEntries(conn, childDn);
                children.add(new ChildEntry(childDn, rdn, hasChildren));
            }
        } catch (LDAPSearchException e) {
            if (e.getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                log.debug("Base '{}' does not exist — returning empty children", baseDn);
                return children;
            }
            throw e;
        }

        children.sort(Comparator.comparing(ChildEntry::rdn, String.CASE_INSENSITIVE_ORDER));
        return children;
    }

    private boolean hasSubEntries(LDAPConnection conn, String dn) {
        try {
            SearchRequest probe = new SearchRequest(
                    dn, SearchScope.ONE,
                    Filter.createPresenceFilter("objectClass"),
                    "1.1"); // no attributes — just check existence
            probe.setSizeLimit(1);
            SearchResult result = conn.search(probe);
            return !result.getSearchEntries().isEmpty();
        } catch (LDAPException e) {
            // If we can't probe, assume it might have children
            return false;
        }
    }

    /**
     * Creates a new LDAP entry with the given DN and attributes.
     */
    public void createEntry(DirectoryConnection dc, String dn,
                            Map<String, List<String>> attributes) {
        List<Attribute> ldapAttrs = new ArrayList<>();
        attributes.forEach((name, values) ->
            ldapAttrs.add(new Attribute(name, values.toArray(new String[0]))));

        connectionFactory.withConnection(dc, conn -> {
            LDAPResult result = conn.add(new AddRequest(dn, ldapAttrs));
            if (result.getResultCode() != ResultCode.SUCCESS) {
                throw new LdapOperationException(
                    "createEntry failed for [" + dn + "]: "
                    + result.getResultCode() + " — " + result.getDiagnosticMessage());
            }
            log.info("Created LDAP entry {}", dn);
            return null;
        });
    }

    /**
     * Updates an existing LDAP entry by applying the given attribute modifications.
     */
    public void updateEntry(DirectoryConnection dc, String dn,
                            List<Modification> modifications) {
        connectionFactory.withConnection(dc, conn -> {
            LDAPResult result = conn.modify(new ModifyRequest(dn, modifications));
            if (result.getResultCode() != ResultCode.SUCCESS) {
                throw new LdapOperationException(
                    "updateEntry failed for [" + dn + "]: "
                    + result.getResultCode() + " — " + result.getDiagnosticMessage());
            }
            log.info("Updated LDAP entry {}", dn);
            return null;
        });
    }

    /**
     * Deletes an LDAP entry.  When {@code recursive} is true, all descendant
     * entries are deleted bottom-up first (OpenLDAP rejects delete on non-leaf).
     */
    public void deleteEntry(DirectoryConnection dc, String dn, boolean recursive) {
        connectionFactory.withConnection(dc, conn -> {
            if (recursive) {
                deleteSubtree(conn, dc, dn);
            } else {
                LDAPResult result = conn.delete(dn);
                if (result.getResultCode() != ResultCode.SUCCESS) {
                    throw new LdapOperationException(
                        "deleteEntry failed for [" + dn + "]: "
                        + result.getResultCode() + " — " + result.getDiagnosticMessage());
                }
            }
            log.info("Deleted LDAP entry {}{}", dn, recursive ? " (recursive)" : "");
            return null;
        });
    }

    private void deleteSubtree(LDAPConnection conn, DirectoryConnection dc,
                                String dn) throws LDAPException {
        // Depth-first: delete children before the parent
        List<ChildEntry> children = listChildren(conn, dc, dn);
        for (ChildEntry child : children) {
            deleteSubtree(conn, dc, child.dn());
        }
        LDAPResult result = conn.delete(dn);
        if (result.getResultCode() != ResultCode.SUCCESS) {
            throw new LdapOperationException(
                "deleteEntry failed for [" + dn + "]: "
                + result.getResultCode() + " — " + result.getDiagnosticMessage());
        }
    }

    private String extractRdn(String childDn, String parentDn) {
        // Remove ",parentDn" suffix to get the RDN
        if (childDn.toLowerCase().endsWith("," + parentDn.toLowerCase())) {
            return childDn.substring(0, childDn.length() - parentDn.length() - 1);
        }
        return childDn;
    }

    // ── Value objects ─────────────────────────────────────────────────────────

    public record BrowseResult(
            String dn,
            Map<String, List<String>> attributes,
            List<ChildEntry> children
    ) {}

    public record ChildEntry(
            String dn,
            String rdn,
            boolean hasChildren
    ) {}
}
