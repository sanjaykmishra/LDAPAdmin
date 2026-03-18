package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.ldap.LdapSchemaService.AttributeTypeInfo;
import com.ldapadmin.ldap.LdapSchemaService.ObjectClassAttributes;
import com.ldapadmin.service.LdapOperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Exposes the LDAP schema (objectClasses and attributeTypes) for a directory.
 *
 * <pre>
 *   GET /api/directories/{directoryId}/schema/object-classes
 *   GET /api/directories/{directoryId}/schema/object-classes/{name}
 *   GET /api/directories/{directoryId}/schema/attribute-types
 *   GET /api/directories/{directoryId}/schema/attribute-types/{name}
 * </pre>
 *
 * <p>Requires any authenticated principal with directory access (dim 1+2).
 * No specific feature permission is needed for schema discovery.</p>
 */
@RestController
@RequestMapping("/api/v1/directories/{directoryId}/schema")
@RequiredArgsConstructor
public class SchemaController {

    private final LdapOperationService service;

    @GetMapping("/object-classes")
    public List<String> listObjectClasses(
            @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.getObjectClassNames(directoryId, principal);
    }

    @GetMapping("/object-classes/{name}")
    public ObjectClassAttributes getObjectClass(
            @PathVariable UUID directoryId,
            @PathVariable String name,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.getObjectClassAttributes(directoryId, principal, name);
    }

    @GetMapping("/object-classes/bulk")
    public ObjectClassAttributes getObjectClassesBulk(
            @PathVariable UUID directoryId,
            @RequestParam List<String> names,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.getObjectClassAttributesBulk(directoryId, principal, names);
    }

    @GetMapping("/attribute-types")
    public List<String> listAttributeTypes(
            @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.getAttributeTypeNames(directoryId, principal);
    }

    @GetMapping("/attribute-types/{name}")
    public AttributeTypeInfo getAttributeType(
            @PathVariable UUID directoryId,
            @PathVariable String name,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.getAttributeTypeInfo(directoryId, principal, name);
    }
}
