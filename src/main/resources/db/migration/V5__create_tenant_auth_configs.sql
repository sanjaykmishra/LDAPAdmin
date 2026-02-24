-- V5: Per-tenant authentication configuration (§3.1).
-- auth_type is mutually exclusive: LDAP_BIND or SAML.
-- Exactly one row per tenant.

CREATE TABLE tenant_auth_configs (
    id                            UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                     UUID          NOT NULL,
    -- 'LDAP_BIND' or 'SAML' — mutually exclusive per tenant
    auth_type                     VARCHAR(20)   NOT NULL,

    -- ── LDAP Bind fields (populated when auth_type = 'LDAP_BIND') ────────────
    -- The directory connection against which admin credentials are validated
    ldap_directory_id             UUID,
    -- Bind DN pattern; {username} is substituted at authentication time.
    -- Example: uid={username},ou=people,dc=example,dc=com
    ldap_bind_dn_pattern          VARCHAR(500),

    -- ── SAML 2.0 fields (populated when auth_type = 'SAML') ──────────────────
    -- 'OKTA', 'IBM_VERIFY', or 'GENERIC'
    saml_idp_type                 VARCHAR(20),
    -- IdP metadata can be supplied as a URL or as inline XML (one or both)
    saml_idp_metadata_url         VARCHAR(1000),
    saml_idp_metadata_xml         TEXT,
    -- Service Provider entity ID registered with the IdP
    saml_sp_entity_id             VARCHAR(500),
    -- Assertion Consumer Service URL for this tenant
    saml_sp_acs_url               VARCHAR(1000),
    -- SAML attribute names that map to portal identity fields
    saml_attribute_username       VARCHAR(255),
    saml_attribute_email          VARCHAR(255),
    saml_attribute_display_name   VARCHAR(255),
    -- Any extra IdP attribute → local field mappings; stored as JSON object
    -- Example: {"department": "samlDepartmentAttr"}
    saml_extra_attribute_mappings JSONB,

    created_at                    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_tenant_auth_configs    PRIMARY KEY (id),
    CONSTRAINT uq_tenant_auth_config     UNIQUE (tenant_id),
    CONSTRAINT fk_auth_config_tenant
        FOREIGN KEY (tenant_id)        REFERENCES tenants (id)              ON DELETE CASCADE,
    CONSTRAINT fk_auth_config_ldap_dir
        FOREIGN KEY (ldap_directory_id) REFERENCES directory_connections (id) ON DELETE SET NULL,
    CONSTRAINT chk_auth_type
        CHECK (auth_type IN ('LDAP_BIND', 'SAML')),
    CONSTRAINT chk_saml_idp_type
        CHECK (saml_idp_type IS NULL OR saml_idp_type IN ('OKTA', 'IBM_VERIFY', 'GENERIC'))
);
