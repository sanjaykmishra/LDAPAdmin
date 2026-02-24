package com.ldapadmin.ldap;

import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.exception.LdapOperationException;
import com.unboundid.ldap.sdk.schema.AttributeTypeDefinition;
import com.unboundid.ldap.sdk.schema.ObjectClassDefinition;
import com.unboundid.ldap.sdk.schema.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Discovers the LDAP schema (objectClasses and attributeTypes) from the
 * directory server's subschema subentry.
 *
 * <p>The schema is fetched on each call — caching is intentionally left to
 * the REST layer (Phase 3) where it can be tied to cache eviction events
 * (e.g. user-triggered "refresh schema").</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LdapSchemaService {

    private final LdapConnectionFactory connectionFactory;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns all objectClass names defined in the directory schema,
     * sorted alphabetically.
     */
    public List<String> getObjectClassNames(DirectoryConnection dc) {
        Schema schema = fetchSchema(dc);
        return schema.getObjectClasses().stream()
            .map(ObjectClassDefinition::getNameOrOID)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
    }

    /**
     * Returns all attributeType names defined in the directory schema,
     * sorted alphabetically.
     */
    public List<String> getAttributeTypeNames(DirectoryConnection dc) {
        Schema schema = fetchSchema(dc);
        return schema.getAttributeTypes().stream()
            .map(AttributeTypeDefinition::getNameOrOID)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
    }

    /**
     * Returns the required and optional attributes for a given objectClass.
     *
     * @param dc          directory connection
     * @param objectClass exact objectClass name as returned by {@link #getObjectClassNames}
     * @return {@link ObjectClassAttributes} containing required and optional attribute names
     * @throws LdapOperationException if the objectClass does not exist in the schema
     */
    public ObjectClassAttributes getAttributesForObjectClass(DirectoryConnection dc,
                                                             String objectClass) {
        Schema schema = fetchSchema(dc);
        ObjectClassDefinition ocd = schema.getObjectClass(objectClass);
        if (ocd == null) {
            throw new LdapOperationException(
                "ObjectClass '" + objectClass + "' not found in schema for ["
                + dc.getDisplayName() + "]");
        }

        Set<String> required = collectAttributeNames(schema, ocd, true);
        Set<String> optional = collectAttributeNames(schema, ocd, false);

        return new ObjectClassAttributes(objectClass, required, optional);
    }

    /**
     * Returns detailed information about a specific attributeType.
     *
     * @throws LdapOperationException if the attribute does not exist in the schema
     */
    public AttributeTypeInfo getAttributeTypeInfo(DirectoryConnection dc, String attributeName) {
        Schema schema = fetchSchema(dc);
        AttributeTypeDefinition atd = schema.getAttributeType(attributeName);
        if (atd == null) {
            throw new LdapOperationException(
                "AttributeType '" + attributeName + "' not found in schema for ["
                + dc.getDisplayName() + "]");
        }
        return new AttributeTypeInfo(
            atd.getNameOrOID(),
            atd.getOID(),
            atd.getSyntaxOID(),
            atd.isSingleValued());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Schema fetchSchema(DirectoryConnection dc) {
        return connectionFactory.withConnection(dc, conn -> {
            try {
                Schema schema = Schema.getSchema(conn);
                if (schema == null) {
                    throw new LdapOperationException(
                        "Server did not return a schema for [" + dc.getDisplayName() + "]");
                }
                log.debug("Fetched schema from [{}]: {} objectClasses, {} attributeTypes",
                    dc.getDisplayName(),
                    schema.getObjectClasses().size(),
                    schema.getAttributeTypes().size());
                return schema;
            } catch (LdapOperationException e) {
                throw e;
            } catch (Exception e) {
                throw new LdapOperationException(
                    "Failed to fetch schema from [" + dc.getDisplayName() + "]: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Collects required or optional attribute names for an objectClass,
     * walking the superclass chain to include inherited attributes.
     */
    private Set<String> collectAttributeNames(Schema schema,
                                               ObjectClassDefinition ocd,
                                               boolean required) {
        Set<String> names = new LinkedHashSet<>();
        collectRecursive(schema, ocd, required, names, new HashSet<>());
        return Collections.unmodifiableSet(names);
    }

    private void collectRecursive(Schema schema,
                                  ObjectClassDefinition ocd,
                                  boolean required,
                                  Set<String> accumulator,
                                  Set<String> visited) {
        String key = ocd.getNameOrOID();
        if (!visited.add(key)) {
            return;
        }

        String[] attrNames = required
            ? ocd.getRequiredAttributes()
            : ocd.getOptionalAttributes();
        if (attrNames != null) {
            Collections.addAll(accumulator, attrNames);
        }

        // Walk superclasses
        String[] superNames = ocd.getSuperiorClasses();
        if (superNames != null) {
            for (String superName : superNames) {
                ObjectClassDefinition superOcd = schema.getObjectClass(superName);
                if (superOcd != null) {
                    collectRecursive(schema, superOcd, required, accumulator, visited);
                }
            }
        }
    }

    // ── Value objects ─────────────────────────────────────────────────────────

    /**
     * Required and optional attribute names for an objectClass.
     */
    public record ObjectClassAttributes(
        String objectClassName,
        Set<String> required,
        Set<String> optional
    ) {}

    /**
     * Summary metadata for a single attributeType.
     */
    public record AttributeTypeInfo(
        String name,
        String oid,
        String syntaxOid,
        boolean singleValued
    ) {}
}
