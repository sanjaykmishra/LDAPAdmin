package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PermissionService;
import com.ldapadmin.dto.ldap.AttributeModification;
import com.ldapadmin.dto.ldap.CreateEntryRequest;
import com.ldapadmin.dto.ldap.LdapEntryResponse;
import com.ldapadmin.dto.ldap.MoveUserRequest;
import com.ldapadmin.dto.ldap.UpdateEntryRequest;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.ldap.LdapSchemaService;
import com.ldapadmin.ldap.LdapSchemaService.AttributeTypeInfo;
import com.ldapadmin.ldap.LdapSchemaService.ObjectClassAttributes;
import com.ldapadmin.ldap.LdapUserService;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Permission-checked façade over the raw LDAP services.
 *
 * <p>This service is the single entry-point for all LDAP directory operations
 * in the REST layer.  Each method:</p>
 * <ol>
 *   <li>Loads the {@link DirectoryConnection}, scoped to the principal's tenant
 *       (or unscoped for superadmins).</li>
 *   <li>Enforces branch access (dimension 3) for entry-level operations.</li>
 *   <li>Delegates to the underlying LDAP service.</li>
 * </ol>
 *
 * <p>Feature permission checks (dimensions 1, 2, 4) are enforced by the
 * {@link com.ldapadmin.auth.FeaturePermissionAspect} via
 * {@link com.ldapadmin.auth.RequiresFeature} annotations on the calling
 * controller methods. Directory-access checks for read-only operations
 * (which carry no feature annotation) are performed here directly.</p>
 */
@Service
@RequiredArgsConstructor
public class LdapOperationService {

    private final DirectoryConnectionRepository dirRepo;
    private final PermissionService             permissionService;
    private final LdapUserService               userService;
    private final LdapGroupService              groupService;
    private final LdapSchemaService             schemaService;

    // ── Schema ────────────────────────────────────────────────────────────────

    public List<String> getObjectClassNames(UUID directoryId, AuthPrincipal principal) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);
        return schemaService.getObjectClassNames(dc);
    }

    public List<String> getAttributeTypeNames(UUID directoryId, AuthPrincipal principal) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);
        return schemaService.getAttributeTypeNames(dc);
    }

    public ObjectClassAttributes getObjectClassAttributes(UUID directoryId, AuthPrincipal principal,
                                                          String objectClass) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);
        return schemaService.getAttributesForObjectClass(dc, objectClass);
    }

    public AttributeTypeInfo getAttributeTypeInfo(UUID directoryId, AuthPrincipal principal,
                                                   String attributeName) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);
        return schemaService.getAttributeTypeInfo(dc, attributeName);
    }

    // ── Users — read ──────────────────────────────────────────────────────────

    /**
     * Searches users. Enforces directory access (dim 1+2); branch access is
     * not checked on searches — admins see matching entries, the view is not
     * further restricted by branch.
     *
     * @param filter      LDAP filter string (defaults to {@code (objectClass=*)} if blank)
     * @param baseDn      search base DN (null uses the directory's baseDn)
     * @param limit       maximum number of results to return (1–2000)
     * @param attributes  specific attributes to retrieve; empty = all
     */
    public List<LdapEntryResponse> searchUsers(UUID directoryId, AuthPrincipal principal,
                                               String filter, String baseDn,
                                               int limit, String[] attributes) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);
        String effectiveFilter = (filter == null || filter.isBlank()) ? "(objectClass=*)" : filter;
        return userService.searchUsers(dc, effectiveFilter, baseDn, attributes)
                .stream().limit(limit).map(LdapEntryResponse::from).toList();
    }

    /**
     * Retrieves a single user by DN.
     * Enforces directory access + branch access (dim 3).
     */
    public LdapEntryResponse getUser(UUID directoryId, AuthPrincipal principal,
                                     String dn, String[] attributes) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);
        permissionService.requireBranchAccess(principal, directoryId, dn);
        return LdapEntryResponse.from(userService.getUser(dc, dn, attributes));
    }

    // ── Users — write ─────────────────────────────────────────────────────────

    /**
     * Creates a new user entry.
     * Feature check (USER_CREATE) is enforced by the calling controller via @RequiresFeature.
     * Branch access is checked here before delegation.
     */
    public LdapEntryResponse createUser(UUID directoryId, AuthPrincipal principal,
                                        CreateEntryRequest req) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccess(principal, directoryId, req.dn());
        userService.createUser(dc, req.dn(), req.attributes());
        return LdapEntryResponse.from(userService.getUser(dc, req.dn()));
    }

    /**
     * Applies attribute modifications to an existing user entry.
     */
    public LdapEntryResponse updateUser(UUID directoryId, AuthPrincipal principal,
                                        String dn, UpdateEntryRequest req) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccess(principal, directoryId, dn);
        List<Modification> mods = toModifications(req);
        userService.updateUser(dc, dn, mods);
        return LdapEntryResponse.from(userService.getUser(dc, dn));
    }

    /** Deletes a user entry. */
    public void deleteUser(UUID directoryId, AuthPrincipal principal, String dn) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccess(principal, directoryId, dn);
        userService.deleteUser(dc, dn);
    }

    /** Enables a user account via the directory's enable/disable attribute. */
    public void enableUser(UUID directoryId, AuthPrincipal principal, String dn) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccess(principal, directoryId, dn);
        userService.enableUser(dc, dn);
    }

    /** Disables a user account. */
    public void disableUser(UUID directoryId, AuthPrincipal principal, String dn) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccess(principal, directoryId, dn);
        userService.disableUser(dc, dn);
    }

    /** Moves a user entry to a new parent DN. */
    public void moveUser(UUID directoryId, AuthPrincipal principal,
                         String dn, MoveUserRequest req) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccess(principal, directoryId, dn);
        // Also verify the target branch is accessible
        permissionService.requireBranchAccess(principal, directoryId, req.newParentDn());
        userService.moveUser(dc, dn, req.newParentDn());
    }

    // ── Groups — read ─────────────────────────────────────────────────────────

    public List<LdapEntryResponse> searchGroups(UUID directoryId, AuthPrincipal principal,
                                                String filter, String baseDn,
                                                int limit, String[] attributes) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);
        String effectiveFilter = (filter == null || filter.isBlank()) ? "(objectClass=*)" : filter;
        return groupService.searchGroups(dc, effectiveFilter, baseDn, attributes)
                .stream().limit(limit).map(LdapEntryResponse::from).toList();
    }

    public LdapEntryResponse getGroup(UUID directoryId, AuthPrincipal principal,
                                      String dn, String[] attributes) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);
        permissionService.requireBranchAccess(principal, directoryId, dn);
        return LdapEntryResponse.from(groupService.getGroup(dc, dn, attributes));
    }

    public List<String> getGroupMembers(UUID directoryId, AuthPrincipal principal,
                                        String dn, String memberAttribute) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);
        permissionService.requireBranchAccess(principal, directoryId, dn);
        return groupService.getMembers(dc, dn, memberAttribute);
    }

    // ── Groups — write ────────────────────────────────────────────────────────

    public LdapEntryResponse createGroup(UUID directoryId, AuthPrincipal principal,
                                         CreateEntryRequest req) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccess(principal, directoryId, req.dn());
        groupService.createGroup(dc, req.dn(), req.attributes());
        return LdapEntryResponse.from(groupService.getGroup(dc, req.dn()));
    }

    public void deleteGroup(UUID directoryId, AuthPrincipal principal, String dn) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccess(principal, directoryId, dn);
        groupService.deleteGroup(dc, dn);
    }

    public void addGroupMember(UUID directoryId, AuthPrincipal principal,
                               String groupDn, String memberAttribute, String memberValue) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccess(principal, directoryId, groupDn);
        groupService.addMember(dc, groupDn, memberAttribute, memberValue);
    }

    public void removeGroupMember(UUID directoryId, AuthPrincipal principal,
                                  String groupDn, String memberAttribute, String memberValue) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccess(principal, directoryId, groupDn);
        groupService.removeMember(dc, groupDn, memberAttribute, memberValue);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Loads the {@link DirectoryConnection}, scoping by tenant for admin principals.
     * Verifies the connection is enabled.
     */
    private DirectoryConnection loadDirectory(UUID directoryId, AuthPrincipal principal) {
        DirectoryConnection dc;
        if (principal.isSuperadmin()) {
            dc = dirRepo.findById(directoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
        } else {
            dc = dirRepo.findByIdAndTenantId(directoryId, principal.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
        }
        if (!dc.isEnabled()) {
            throw new ResourceNotFoundException("DirectoryConnection", directoryId);
        }
        return dc;
    }

    private List<Modification> toModifications(UpdateEntryRequest req) {
        return req.modifications().stream()
                .map(m -> new Modification(
                        toModType(m.operation()),
                        m.attribute(),
                        m.values() == null ? new String[0]
                                : m.values().toArray(new String[0])))
                .toList();
    }

    private ModificationType toModType(AttributeModification.Operation op) {
        return switch (op) {
            case ADD     -> ModificationType.ADD;
            case REPLACE -> ModificationType.REPLACE;
            case DELETE  -> ModificationType.DELETE;
        };
    }
}
