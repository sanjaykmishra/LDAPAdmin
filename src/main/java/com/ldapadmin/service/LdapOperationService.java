package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PermissionService;
import com.ldapadmin.dto.csv.BulkImportRequest;
import com.ldapadmin.dto.csv.BulkImportResult;
import com.ldapadmin.dto.csv.CsvColumnMappingDto;
import com.ldapadmin.dto.ldap.AttributeModification;
import com.ldapadmin.dto.ldap.CreateEntryRequest;
import com.ldapadmin.dto.ldap.LdapEntryResponse;
import com.ldapadmin.dto.ldap.MoveUserRequest;
import com.ldapadmin.dto.ldap.UpdateEntryRequest;
import com.ldapadmin.entity.CsvMappingTemplate;
import com.ldapadmin.entity.CsvMappingTemplateEntry;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.entity.enums.ConflictHandling;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 *   <li>Fires an async audit event via {@link AuditService} for write ops.</li>
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
    private final AuditService                  auditService;
    private final BulkUserService               bulkUserService;
    private final CsvMappingTemplateService     csvTemplateService;

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

    public List<LdapEntryResponse> searchUsers(UUID directoryId, AuthPrincipal principal,
                                               String filter, String baseDn,
                                               int limit, String[] attributes) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);
        String effectiveFilter = (filter == null || filter.isBlank()) ? "(objectClass=*)" : filter;
        return userService.searchUsers(dc, effectiveFilter, baseDn, limit, attributes)
                .stream().map(LdapEntryResponse::from).toList();
    }

    public LdapEntryResponse getUser(UUID directoryId, AuthPrincipal principal,
                                     String dn, String[] attributes) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);
        permissionService.requireBranchAccessForDirectory(principal, directoryId, dn);
        return LdapEntryResponse.from(userService.getUser(dc, dn, attributes));
    }

    // ── Users — write ─────────────────────────────────────────────────────────

    public LdapEntryResponse createUser(UUID directoryId, AuthPrincipal principal,
                                        CreateEntryRequest req) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccessForDirectory(principal, directoryId, req.dn());
        userService.createUser(dc, req.dn(), req.attributes());
        LdapEntryResponse result = LdapEntryResponse.from(userService.getUser(dc, req.dn()));
        auditService.record(principal, directoryId, AuditAction.USER_CREATE, req.dn(),
                Map.of("attributes", req.attributes().keySet()));
        return result;
    }

    public LdapEntryResponse updateUser(UUID directoryId, AuthPrincipal principal,
                                        String dn, UpdateEntryRequest req) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccessForDirectory(principal, directoryId, dn);
        List<Modification> mods = toModifications(req);
        userService.updateUser(dc, dn, mods);
        LdapEntryResponse result = LdapEntryResponse.from(userService.getUser(dc, dn));
        auditService.record(principal, directoryId, AuditAction.USER_UPDATE, dn,
                Map.of("modifiedAttributes", req.modifications().stream()
                        .map(AttributeModification::attribute).toList()));
        return result;
    }

    public void deleteUser(UUID directoryId, AuthPrincipal principal, String dn) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccessForDirectory(principal, directoryId, dn);
        userService.deleteUser(dc, dn);
        auditService.record(principal, directoryId, AuditAction.USER_DELETE, dn, null);
    }

    public void enableUser(UUID directoryId, AuthPrincipal principal, String dn) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccessForDirectory(principal, directoryId, dn);
        userService.enableUser(dc, dn);
        auditService.record(principal, directoryId, AuditAction.USER_ENABLE, dn, null);
    }

    public void disableUser(UUID directoryId, AuthPrincipal principal, String dn) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccessForDirectory(principal, directoryId, dn);
        userService.disableUser(dc, dn);
        auditService.record(principal, directoryId, AuditAction.USER_DISABLE, dn, null);
    }

    public void moveUser(UUID directoryId, AuthPrincipal principal,
                         String dn, MoveUserRequest req) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccessForDirectory(principal, directoryId, dn);
        permissionService.requireBranchAccessForDirectory(principal, directoryId, req.newParentDn());
        userService.moveUser(dc, dn, req.newParentDn());
        auditService.record(principal, directoryId, AuditAction.USER_MOVE, dn,
                Map.of("newParentDn", req.newParentDn()));
    }

    // ── Groups — read ─────────────────────────────────────────────────────────

    public List<LdapEntryResponse> searchGroups(UUID directoryId, AuthPrincipal principal,
                                                String filter, String baseDn,
                                                int limit, String[] attributes) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);
        String effectiveFilter = (filter == null || filter.isBlank()) ? "(objectClass=*)" : filter;
        return groupService.searchGroups(dc, effectiveFilter, baseDn, limit, attributes)
                .stream().map(LdapEntryResponse::from).toList();
    }

    public LdapEntryResponse getGroup(UUID directoryId, AuthPrincipal principal,
                                      String dn, String[] attributes) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);
        permissionService.requireBranchAccessForDirectory(principal, directoryId, dn);
        return LdapEntryResponse.from(groupService.getGroup(dc, dn, attributes));
    }

    public List<String> getGroupMembers(UUID directoryId, AuthPrincipal principal,
                                        String dn, String memberAttribute) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);
        permissionService.requireBranchAccessForDirectory(principal, directoryId, dn);
        return groupService.getMembers(dc, dn, memberAttribute);
    }

    // ── Groups — write ────────────────────────────────────────────────────────

    public LdapEntryResponse createGroup(UUID directoryId, AuthPrincipal principal,
                                         CreateEntryRequest req) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccessForDirectory(principal, directoryId, req.dn());
        groupService.createGroup(dc, req.dn(), req.attributes());
        LdapEntryResponse result = LdapEntryResponse.from(groupService.getGroup(dc, req.dn()));
        auditService.record(principal, directoryId, AuditAction.GROUP_CREATE, req.dn(),
                Map.of("attributes", req.attributes().keySet()));
        return result;
    }

    public void deleteGroup(UUID directoryId, AuthPrincipal principal, String dn) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccessForDirectory(principal, directoryId, dn);
        groupService.deleteGroup(dc, dn);
        auditService.record(principal, directoryId, AuditAction.GROUP_DELETE, dn, null);
    }

    public void addGroupMember(UUID directoryId, AuthPrincipal principal,
                               String groupDn, String memberAttribute, String memberValue) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccessForDirectory(principal, directoryId, groupDn);
        groupService.addMember(dc, groupDn, memberAttribute, memberValue);
        auditService.record(principal, directoryId, AuditAction.GROUP_MEMBER_ADD, groupDn,
                Map.of("attribute", memberAttribute, "member", memberValue));
    }

    public void removeGroupMember(UUID directoryId, AuthPrincipal principal,
                                  String groupDn, String memberAttribute, String memberValue) {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireBranchAccessForDirectory(principal, directoryId, groupDn);
        groupService.removeMember(dc, groupDn, memberAttribute, memberValue);
        auditService.record(principal, directoryId, AuditAction.GROUP_MEMBER_REMOVE, groupDn,
                Map.of("attribute", memberAttribute, "member", memberValue));
    }

    // ── Bulk import / export ──────────────────────────────────────────────────

    /**
     * Imports users from a CSV stream into the LDAP directory.
     *
     * <p>Column mappings are resolved from the referenced template (if any),
     * then overridden by any fields set directly on {@code req}.</p>
     */
    public BulkImportResult bulkImportUsers(UUID directoryId, AuthPrincipal principal,
                                            InputStream csvInput,
                                            BulkImportRequest req) throws IOException {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);

        // Defaults — may be overridden by template or request fields
        String            targetKeyAttr    = "uid";
        ConflictHandling  conflictHandling = ConflictHandling.SKIP;
        List<CsvColumnMappingDto> mappings = req.columnMappings() != null
                ? new ArrayList<>(req.columnMappings()) : new ArrayList<>();

        if (req.templateId() != null) {
            CsvMappingTemplate template =
                    csvTemplateService.loadTemplate(req.templateId(), directoryId, principal);
            List<CsvMappingTemplateEntry> entries =
                    csvTemplateService.loadEntries(req.templateId());
            targetKeyAttr    = template.getTargetKeyAttribute();
            conflictHandling = template.getConflictHandling();
            // Template entries are used only when the request carries no ad-hoc mappings
            if (mappings.isEmpty()) {
                mappings = entries.stream()
                        .map(e -> new CsvColumnMappingDto(
                                e.getCsvColumnName(), e.getLdapAttribute(), e.isIgnored()))
                        .toList();
            }
        }

        // Request-level fields take final precedence
        if (req.targetKeyAttribute() != null)  targetKeyAttr    = req.targetKeyAttribute();
        if (req.conflictHandling()    != null)  conflictHandling = req.conflictHandling();

        BulkImportResult result = bulkUserService.importCsv(
                dc, csvInput, req.parentDn(), targetKeyAttr, conflictHandling, mappings);

        auditService.record(principal, directoryId, AuditAction.USER_CREATE, req.parentDn(),
                Map.of("operation", "bulkImport",
                       "created",   result.created(),
                       "updated",   result.updated(),
                       "skipped",   result.skipped(),
                       "errors",    result.errors()));
        return result;
    }

    /**
     * Exports directory users to a CSV {@code byte[]} with a header row.
     *
     * <p>When {@code templateId} is supplied and {@code attributes} is empty,
     * the attribute list is derived from the template's non-ignored entries.</p>
     */
    public byte[] bulkExportUsers(UUID directoryId, AuthPrincipal principal,
                                  String filter, String baseDn,
                                  List<String> attributes, UUID templateId) throws IOException {
        DirectoryConnection dc = loadDirectory(directoryId, principal);
        permissionService.requireDirectoryAccess(principal, directoryId);

        List<String> effectiveAttrs = new ArrayList<>(attributes);
        if (effectiveAttrs.isEmpty() && templateId != null) {
            csvTemplateService.loadEntries(templateId).stream()
                    .filter(e -> !e.isIgnored() && e.getLdapAttribute() != null)
                    .map(CsvMappingTemplateEntry::getLdapAttribute)
                    .forEach(effectiveAttrs::add);
        }

        return bulkUserService.exportCsv(dc, filter, baseDn, effectiveAttrs);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private DirectoryConnection loadDirectory(UUID directoryId, AuthPrincipal principal) {
        DirectoryConnection dc = dirRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
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
