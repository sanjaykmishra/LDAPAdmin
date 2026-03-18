package com.ldapadmin.controller.superadmin;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.dto.ldap.AttributeModification;
import com.ldapadmin.dto.ldap.CreateEntryRequest;
import com.ldapadmin.dto.ldap.UpdateEntryRequest;
import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.AuditAction;
import com.ldapadmin.exception.ResourceNotFoundException;
import com.ldapadmin.ldap.LdapBrowseService;
import com.ldapadmin.ldap.LdapBrowseService.BrowseResult;
import com.ldapadmin.ldap.LdapSchemaService;
import com.ldapadmin.ldap.LdapSchemaService.ObjectClassAttributes;
import com.ldapadmin.repository.DirectoryConnectionRepository;
import com.ldapadmin.service.AuditService;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.ModificationType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Superadmin-only DIT browser — lists children of a DN, returns
 * the entry's attributes, and supports creating new entries.
 *
 * <pre>
 *   GET  /api/v1/superadmin/directories/{directoryId}/browse?dn=...
 *   POST /api/v1/superadmin/directories/{directoryId}/browse
 *   GET  /api/v1/superadmin/directories/{directoryId}/browse/schema/object-classes
 *   GET  /api/v1/superadmin/directories/{directoryId}/browse/schema/object-classes/bulk?names=...
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/superadmin/directories/{directoryId}/browse")
@PreAuthorize("hasRole('SUPERADMIN')")
@RequiredArgsConstructor
public class BrowseController {

    private final LdapBrowseService browseService;
    private final LdapSchemaService schemaService;
    private final AuditService auditService;
    private final DirectoryConnectionRepository dirRepo;

    @GetMapping
    public BrowseResult browse(@PathVariable UUID directoryId,
                               @RequestParam(required = false) String dn) {
        DirectoryConnection dc = loadDirectory(directoryId);
        return browseService.browse(dc, dn);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BrowseResult createEntry(@PathVariable UUID directoryId,
                                    @AuthenticationPrincipal AuthPrincipal principal,
                                    @Valid @RequestBody CreateEntryRequest req) {
        DirectoryConnection dc = loadDirectory(directoryId);
        browseService.createEntry(dc, req.dn(), req.attributes());

        // Audit the creation
        auditService.record(principal, directoryId, AuditAction.ENTRY_CREATE, req.dn(),
                Map.of("attributes", req.attributes().keySet()));

        // Return the parent's browse result so the UI can refresh the tree
        String parentDn = extractParentDn(req.dn(), dc.getBaseDn());
        return browseService.browse(dc, parentDn);
    }

    @PutMapping
    public BrowseResult updateEntry(@PathVariable UUID directoryId,
                                    @AuthenticationPrincipal AuthPrincipal principal,
                                    @RequestParam String dn,
                                    @Valid @RequestBody UpdateEntryRequest req) {
        DirectoryConnection dc = loadDirectory(directoryId);

        List<Modification> mods = req.modifications().stream()
                .map(m -> new Modification(
                        toModificationType(m.operation()),
                        m.attribute(),
                        m.values() != null ? m.values().toArray(new String[0]) : new String[0]))
                .toList();

        browseService.updateEntry(dc, dn, mods);

        auditService.record(principal, directoryId, AuditAction.ENTRY_UPDATE, dn,
                Map.of("modifications", req.modifications().size()));

        return browseService.browse(dc, dn);
    }

    @DeleteMapping
    public BrowseResult deleteEntry(@PathVariable UUID directoryId,
                                    @AuthenticationPrincipal AuthPrincipal principal,
                                    @RequestParam String dn,
                                    @RequestParam(defaultValue = "false") boolean recursive) {
        DirectoryConnection dc = loadDirectory(directoryId);
        String parentDn = extractParentDn(dn, dc.getBaseDn());

        browseService.deleteEntry(dc, dn, recursive);

        auditService.record(principal, directoryId, AuditAction.ENTRY_DELETE, dn,
                Map.of("recursive", recursive));

        return browseService.browse(dc, parentDn);
    }

    private ModificationType toModificationType(AttributeModification.Operation op) {
        return switch (op) {
            case ADD -> ModificationType.ADD;
            case REPLACE -> ModificationType.REPLACE;
            case DELETE -> ModificationType.DELETE;
        };
    }

    // ── Schema endpoints (superadmin bypass — no realm/feature checks) ────────

    @GetMapping("/schema/object-classes")
    public List<String> listObjectClasses(@PathVariable UUID directoryId) {
        DirectoryConnection dc = loadDirectory(directoryId);
        return schemaService.getObjectClassNames(dc);
    }

    @GetMapping("/schema/object-classes/bulk")
    public ObjectClassAttributes getObjectClassesBulk(
            @PathVariable UUID directoryId,
            @RequestParam List<String> names) {
        DirectoryConnection dc = loadDirectory(directoryId);
        return schemaService.getAttributesForObjectClasses(dc, names);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DirectoryConnection loadDirectory(UUID directoryId) {
        return dirRepo.findById(directoryId)
                .orElseThrow(() -> new ResourceNotFoundException("DirectoryConnection", directoryId));
    }

    /**
     * Extracts the parent DN from a full DN string.
     * e.g. "ou=People,dc=example,dc=com" → "dc=example,dc=com"
     */
    private String extractParentDn(String dn, String baseDn) {
        int idx = dn.indexOf(',');
        if (idx < 0 || idx + 1 >= dn.length()) {
            return baseDn;
        }
        return dn.substring(idx + 1);
    }
}
