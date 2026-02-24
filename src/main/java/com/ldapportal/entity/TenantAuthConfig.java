package com.ldapportal.entity;

import com.ldapportal.entity.enums.AuthType;
import com.ldapportal.entity.enums.SamlIdpType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "tenant_auth_configs")
@Getter
@Setter
@NoArgsConstructor
public class TenantAuthConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    /** Authentication method for this tenant. Mutually exclusive per tenant (§3.1). */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 20)
    private AuthType authType;

    // ── LDAP Bind fields ──────────────────────────────────────────────────────

    /** Directory connection against which admin credentials are validated. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ldap_directory_id")
    private DirectoryConnection ldapDirectory;

    /**
     * Bind DN pattern; {@code {username}} is substituted at authentication time.
     * Example: {@code uid={username},ou=people,dc=example,dc=com}
     */
    @Column(name = "ldap_bind_dn_pattern")
    private String ldapBindDnPattern;

    // ── SAML 2.0 fields ───────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "saml_idp_type", length = 20)
    private SamlIdpType samlIdpType;

    @Column(name = "saml_idp_metadata_url")
    private String samlIdpMetadataUrl;

    @Column(name = "saml_idp_metadata_xml", columnDefinition = "TEXT")
    private String samlIdpMetadataXml;

    @Column(name = "saml_sp_entity_id")
    private String samlSpEntityId;

    /** Assertion Consumer Service URL for this tenant's Service Provider. */
    @Column(name = "saml_sp_acs_url")
    private String samlSpAcsUrl;

    @Column(name = "saml_attribute_username")
    private String samlAttributeUsername;

    @Column(name = "saml_attribute_email")
    private String samlAttributeEmail;

    @Column(name = "saml_attribute_display_name")
    private String samlAttributeDisplayName;

    /**
     * Additional IdP attribute → local field mappings.
     * Stored as a JSONB object, e.g. {@code {"department": "samlDeptAttr"}}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "saml_extra_attribute_mappings", columnDefinition = "jsonb")
    private Map<String, String> samlExtraAttributeMappings;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
