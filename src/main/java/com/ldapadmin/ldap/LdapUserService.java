package com.ldapadmin.ldap;

import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.EnableDisableValueType;
import com.ldapadmin.exception.LdapOperationException;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.model.LdapUser;
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
 * LDAP user operations — search, read, create, update, delete,
 * enable/disable, and move.
 *
 * <p>All operations borrow a connection from the {@link LdapConnectionFactory}
 * pool and return it when done.  Pagination is applied automatically on
 * searches using the Simple Paged Results control (RFC 2696).</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LdapUserService {

    private final LdapConnectionFactory connectionFactory;

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Searches for users using the provided LDAP filter string.
     *
     * @param dc         directory connection to query
     * @param filter     LDAP filter, e.g. {@code (&(objectClass=inetOrgPerson)(cn=jo*))}
     * @param baseDn     search base (null falls back to the connection's base DN)
     * @param attributes attributes to retrieve; empty array retrieves all user attributes
     * @return matching users, paged using the connection's configured page size
     */
    public List<LdapUser> searchUsers(DirectoryConnection dc,
                                      String filter,
                                      String baseDn,
                                      String... attributes) {
        String searchBase = baseDn != null ? baseDn : dc.getBaseDn();
        int pageSize = dc.getPagingSize();
        List<LdapUser> results = new ArrayList<>();

        return connectionFactory.withConnection(dc, conn -> {
            ASN1OctetString cookie = null;
            do {
                SimplePagedResultsControl pagingRequest =
                    new SimplePagedResultsControl(pageSize, cookie);

                SearchRequest request = new SearchRequest(
                    searchBase,
                    SearchScope.SUB,
                    Filter.create(filter),
                    attributes);
                request.addControl(pagingRequest);

                SearchResult searchResult = conn.search(request);
                for (SearchResultEntry entry : searchResult.getSearchEntries()) {
                    results.add(LdapEntryMapper.toUser(entry));
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
     * Retrieves a single user by distinguished name.
     *
     * @throws ResourceNotFoundException if no entry exists at {@code dn}
     */
    public LdapUser getUser(DirectoryConnection dc, String dn, String... attributes) {
        return connectionFactory.withConnection(dc, conn -> {
            SearchResultEntry entry = (attributes.length > 0)
                ? conn.getEntry(dn, attributes)
                : conn.getEntry(dn);

            if (entry == null) {
                throw new ResourceNotFoundException("LDAP user", dn);
            }
            return LdapEntryMapper.toUser(entry);
        });
    }

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Creates a new user entry at {@code dn} with the given attributes.
     *
     * @param dc         directory connection
     * @param dn         full distinguished name for the new entry
     * @param attributes attribute map — keys are LDAP attribute names,
     *                   values are lists of string values
     */
    public void createUser(DirectoryConnection dc,
                           String dn,
                           Map<String, List<String>> attributes) {
        List<Attribute> ldapAttrs = new ArrayList<>();
        attributes.forEach((name, values) ->
            ldapAttrs.add(new Attribute(name, values.toArray(new String[0]))));

        connectionFactory.withConnection(dc, conn -> {
            LDAPResult result = conn.add(new AddRequest(dn, ldapAttrs));
            checkResult(result, "createUser", dn);
            log.info("Created LDAP user {}", dn);
            return null;
        });
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * Applies the given modifications to an existing user entry.
     *
     * @param dc            directory connection
     * @param dn            distinguished name of the entry to modify
     * @param modifications list of LDAP modifications to apply
     */
    public void updateUser(DirectoryConnection dc,
                           String dn,
                           List<Modification> modifications) {
        connectionFactory.withConnection(dc, conn -> {
            LDAPResult result = conn.modify(new ModifyRequest(dn, modifications));
            checkResult(result, "updateUser", dn);
            log.info("Updated LDAP user {}", dn);
            return null;
        });
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes the user entry at {@code dn}.
     */
    public void deleteUser(DirectoryConnection dc, String dn) {
        connectionFactory.withConnection(dc, conn -> {
            LDAPResult result = conn.delete(dn);
            checkResult(result, "deleteUser", dn);
            log.info("Deleted LDAP user {}", dn);
            return null;
        });
    }

    // ── Enable / Disable ──────────────────────────────────────────────────────

    /**
     * Enables the user account by writing the configured enable value to the
     * directory connection's enable/disable attribute.
     *
     * @throws LdapOperationException if the connection has no enable/disable
     *                                attribute configured
     */
    public void enableUser(DirectoryConnection dc, String dn) {
        applyEnableDisable(dc, dn, true);
    }

    /**
     * Disables the user account.
     *
     * @throws LdapOperationException if the connection has no enable/disable
     *                                attribute configured
     */
    public void disableUser(DirectoryConnection dc, String dn) {
        applyEnableDisable(dc, dn, false);
    }

    private void applyEnableDisable(DirectoryConnection dc, String dn, boolean enable) {
        String attr = dc.getEnableDisableAttribute();
        if (attr == null || attr.isBlank()) {
            throw new LdapOperationException(
                "No enable/disable attribute configured for directory [" + dc.getDisplayName() + "]");
        }

        String value;
        if (dc.getEnableDisableValueType() == EnableDisableValueType.BOOLEAN) {
            value = enable ? "TRUE" : "FALSE";
        } else {
            value = enable ? dc.getEnableValue() : dc.getDisableValue();
        }

        Modification mod = new Modification(ModificationType.REPLACE, attr, value);
        updateUser(dc, dn, List.of(mod));
        log.info("{} LDAP user {}", enable ? "Enabled" : "Disabled", dn);
    }

    // ── Move ──────────────────────────────────────────────────────────────────

    /**
     * Moves a user to a new parent DN via the LDAP ModifyDN operation.
     *
     * @param dc          directory connection
     * @param dn          current distinguished name of the user
     * @param newParentDn target container DN (e.g. {@code ou=Staff,dc=example,dc=com})
     */
    public void moveUser(DirectoryConnection dc, String dn, String newParentDn) {
        // Extract the RDN from the current DN (everything before the first comma)
        String rdn = dn.contains(",") ? dn.substring(0, dn.indexOf(',')) : dn;

        connectionFactory.withConnection(dc, conn -> {
            LDAPResult result = conn.modifyDN(new ModifyDNRequest(dn, rdn, true, newParentDn));
            checkResult(result, "moveUser", dn);
            log.info("Moved LDAP user {} to {}", dn, newParentDn);
            return null;
        });
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
