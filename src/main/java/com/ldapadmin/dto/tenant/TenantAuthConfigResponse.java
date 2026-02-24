package com.ldapadmin.dto.tenant;

import com.ldapadmin.entity.TenantAuthConfig;
import com.ldapadmin.entity.enums.AuthType;
import com.ldapadmin.entity.enums.SamlIdpType;

import java.util.Map;
import java.util.UUID;

public record TenantAuthConfigResponse(
        UUID id,
        AuthType authType,

        // LDAP bind
        UUID ldapDirectoryId,
        String ldapBindDnPattern,

        // SAML
        SamlIdpType samlIdpType,
        String samlIdpMetadataUrl,
        String samlSpEntityId,
        String samlSpAcsUrl,
        String samlAttributeUsername,
        String samlAttributeEmail,
        String samlAttributeDisplayName,
        Map<String, String> samlExtraAttributeMappings) {

    public static TenantAuthConfigResponse from(TenantAuthConfig c) {
        return new TenantAuthConfigResponse(
                c.getId(),
                c.getAuthType(),
                c.getLdapDirectory() != null ? c.getLdapDirectory().getId() : null,
                c.getLdapBindDnPattern(),
                c.getSamlIdpType(),
                c.getSamlIdpMetadataUrl(),
                c.getSamlSpEntityId(),
                c.getSamlSpAcsUrl(),
                c.getSamlAttributeUsername(),
                c.getSamlAttributeEmail(),
                c.getSamlAttributeDisplayName(),
                c.getSamlExtraAttributeMappings());
    }
}
