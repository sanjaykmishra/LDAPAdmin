package com.ldapadmin.ldap;

import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.exception.LdapOperationException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.model.LdapGroup;
import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LDAP group operations — search, read, create, delete, and member management.
 *
 * <p>Member attribute names differ by group schema:
 * <ul>
 *   <li>{@code member} — groupOfNames / AD groups (DN values)</li>
 *   <li>{@code uniqueMember} — groupOfUniqueNames (DN values)</li>
 *   <li>{@code memberUid} — posixGroup (UID strings)</li>
 * </ul>
 * The caller supplies the correct attribute name for their schema.
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LdapGroupService {

    private final LdapConnectionFactory connectionFactory;

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Searches for groups matching the given LDAP filter.
     *
     * @param dc         directory connection
     * @param filter     LDAP filter string
     * @param baseDn     search base (null falls back to connection's base DN)
     * @param attributes attributes to retrieve; empty = all
     */
    public List<LdapGroup> searchGroups(DirectoryConnection dc,
                                        String filter,
                                        String baseDn,
                                        String... attributes) {
        String searchBase = baseDn != null ? baseDn : dc.getBaseDn();
        int pageSize = dc.getPagingSize();
        List<LdapGroup> results = new ArrayList<>();

        return connectionFactory.withConnection(dc, conn -> {
            ASN1OctetString cookie = null;
            do {
                SimplePagedResultsControl pagingRequest =
                    new SimplePagedResultsControl(pageSize, cookie);

                SearchRequest request = new SearchRequest(
                    searchBase, SearchScope.SUB,
                    Filter.create(filter), attributes);
                request.addControl(pagingRequest);

                SearchResult searchResult = conn.search(request);
                for (SearchResultEntry entry : searchResult.getSearchEntries()) {
                    results.add(LdapEntryMapper.toGroup(entry));
                }

                SimplePagedResultsControl pagingResponse =
                    SimplePagedResultsControl.get(searchResult);
                cookie = (pagingResponse != null && pagingResponse.moreResultsToReturn())
                    ? pagingResponse.getCookie()
                    : null;

            } while (cookie != null && cookie.getValue().length > 0);

            return results;
        });
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Retrieves a single group by distinguished name.
     *
     * @throws ResourceNotFoundException if no entry exists at {@code dn}
     */
    public LdapGroup getGroup(DirectoryConnection dc, String dn, String... attributes) {
        return connectionFactory.withConnection(dc, conn -> {
            SearchResultEntry entry = (attributes.length > 0)
                ? conn.getEntry(dn, attributes)
                : conn.getEntry(dn);

            if (entry == null) {
                throw new ResourceNotFoundException("LDAP group", dn);
            }
            return LdapEntryMapper.toGroup(entry);
        });
    }

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Creates a new group entry at {@code dn}.
     *
     * @param dc         directory connection
     * @param dn         full DN for the new group
     * @param attributes attribute map for the new entry
     */
    public void createGroup(DirectoryConnection dc,
                            String dn,
                            Map<String, List<String>> attributes) {
        List<Attribute> ldapAttrs = new ArrayList<>();
        attributes.forEach((name, values) ->
            ldapAttrs.add(new Attribute(name, values.toArray(new String[0]))));

        connectionFactory.withConnection(dc, conn -> {
            LDAPResult result = conn.add(new AddRequest(dn, ldapAttrs));
            checkResult(result, "createGroup", dn);
            log.info("Created LDAP group {}", dn);
            return null;
        });
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes the group entry at {@code dn}.
     */
    public void deleteGroup(DirectoryConnection dc, String dn) {
        connectionFactory.withConnection(dc, conn -> {
            LDAPResult result = conn.delete(dn);
            checkResult(result, "deleteGroup", dn);
            log.info("Deleted LDAP group {}", dn);
            return null;
        });
    }

    // ── Member management ─────────────────────────────────────────────────────

    /**
     * Adds {@code memberValue} to the group's {@code memberAttribute}.
     *
     * @param dc              directory connection
     * @param groupDn         group distinguished name
     * @param memberAttribute attribute to modify (e.g. {@code member}, {@code memberUid})
     * @param memberValue     DN or UID of the member to add
     */
    public void addMember(DirectoryConnection dc,
                          String groupDn,
                          String memberAttribute,
                          String memberValue) {
        Modification mod = new Modification(ModificationType.ADD, memberAttribute, memberValue);
        connectionFactory.withConnection(dc, conn -> {
            LDAPResult result = conn.modify(new ModifyRequest(groupDn, mod));
            checkResult(result, "addMember", groupDn);
            log.info("Added member {} to group {}", memberValue, groupDn);
            return null;
        });
    }

    /**
     * Removes {@code memberValue} from the group's {@code memberAttribute}.
     */
    public void removeMember(DirectoryConnection dc,
                             String groupDn,
                             String memberAttribute,
                             String memberValue) {
        Modification mod = new Modification(ModificationType.DELETE, memberAttribute, memberValue);
        connectionFactory.withConnection(dc, conn -> {
            LDAPResult result = conn.modify(new ModifyRequest(groupDn, mod));
            checkResult(result, "removeMember", groupDn);
            log.info("Removed member {} from group {}", memberValue, groupDn);
            return null;
        });
    }

    /**
     * Returns all values of {@code memberAttribute} for the given group.
     */
    public List<String> getMembers(DirectoryConnection dc,
                                   String groupDn,
                                   String memberAttribute) {
        LdapGroup group = getGroup(dc, groupDn, memberAttribute);
        return group.getValues(memberAttribute);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void checkResult(LDAPResult result, String operation, String dn) {
        if (result.getResultCode() != ResultCode.SUCCESS) {
            throw new LdapOperationException(
                operation + " failed for [" + dn + "]: "
                + result.getResultCode() + " — " + result.getDiagnosticMessage());
        }
    }
}
