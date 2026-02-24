package com.ldapadmin.dto.tenant;

import com.ldapadmin.entity.enums.AuthType;
import com.ldapadmin.entity.enums.SamlIdpType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Upsert request for a tenant's authentication configuration.
 *
 * <p>Only the fields that correspond to the chosen {@link AuthType} need to be
 * populated; the other fields are ignored and cleared on save.</p>
 */
public record TenantAuthConfigRequest(
        @NotNull AuthType authType,

        // ── LDAP bind fields ──────────────────────────────────────────────────
        UUID ldapDirectoryId,
        String ldapBindDnPattern,

        // ── SAML fields ───────────────────────────────────────────────────────
        SamlIdpType samlIdpType,
        String samlIdpMetadataUrl,
        String samlIdpMetadataXml,
        String samlSpEntityId,
        String samlSpAcsUrl,
        String samlAttributeUsername,
        String samlAttributeEmail,
        String samlAttributeDisplayName,
        Map<String, String> samlExtraAttributeMappings) {
}
