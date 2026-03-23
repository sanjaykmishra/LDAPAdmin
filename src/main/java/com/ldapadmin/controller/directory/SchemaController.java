package com.ldapadmin.controller.directory;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.DirectoryId;
import com.ldapadmin.auth.RequiresFeature;
import com.ldapadmin.entity.enums.FeatureKey;
import com.ldapadmin.ldap.LdapSchemaService.AttributeTypeInfo;
import com.ldapadmin.ldap.LdapSchemaService.ObjectClassAttributes;
import com.ldapadmin.ldap.LdapSchemaService.SchemaListItem;
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
 */
@RestController
@RequestMapping("/api/v1/directories/{directoryId}/schema")
@RequiredArgsConstructor
public class SchemaController {

    private final LdapOperationService service;

    @GetMapping("/object-classes")
    @RequiresFeature(FeatureKey.SCHEMA_READ)
    public List<SchemaListItem> listObjectClasses(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.getObjectClassNames(directoryId, principal);
    }

    @GetMapping("/object-classes/{name}")
    @RequiresFeature(FeatureKey.SCHEMA_READ)
    public ObjectClassAttributes getObjectClass(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable String name,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.getObjectClassAttributes(directoryId, principal, name);
    }

    @GetMapping("/object-classes/bulk")
    @RequiresFeature(FeatureKey.SCHEMA_READ)
    public ObjectClassAttributes getObjectClassesBulk(
            @DirectoryId @PathVariable UUID directoryId,
            @RequestParam List<String> names,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.getObjectClassAttributesBulk(directoryId, principal, names);
    }

    @GetMapping("/attribute-types")
    @RequiresFeature(FeatureKey.SCHEMA_READ)
    public List<SchemaListItem> listAttributeTypes(
            @DirectoryId @PathVariable UUID directoryId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.getAttributeTypeNames(directoryId, principal);
    }

    @GetMapping("/attribute-types/{name}")
    @RequiresFeature(FeatureKey.SCHEMA_READ)
    public AttributeTypeInfo getAttributeType(
            @DirectoryId @PathVariable UUID directoryId,
            @PathVariable String name,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return service.getAttributeTypeInfo(directoryId, principal, name);
    }
}
