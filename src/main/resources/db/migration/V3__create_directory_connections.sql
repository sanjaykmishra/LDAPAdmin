-- V3: LDAP directory connection configurations and their base DN lists.
-- Also resolves the deferred FK from superadmin_accounts.

CREATE TABLE directory_connections (
    id                            UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                     UUID          NOT NULL,
    display_name                  VARCHAR(255)  NOT NULL,
    host                          VARCHAR(255)  NOT NULL,
    port                          INTEGER       NOT NULL DEFAULT 389,
    -- Transport security mode for this connection
    ssl_mode                      VARCHAR(10)   NOT NULL DEFAULT 'NONE',
    -- Accept self-signed / untrusted certificates (development/test only)
    trust_all_certs               BOOLEAN       NOT NULL DEFAULT FALSE,
    -- PEM-encoded CA certificate for custom trust anchors
    trusted_certificate_pem       TEXT,
    -- Service account DN used by the application for LDAP operations
    bind_dn                       VARCHAR(1000) NOT NULL,
    -- Bind password encrypted with AES-256; key never stored in DB
    bind_password_encrypted       TEXT          NOT NULL,
    base_dn                       VARCHAR(1000) NOT NULL,
    -- Comma-separated objectClass names to scope schema discovery (§4.2)
    object_classes                VARCHAR(2000),
    -- LDAP paged results control page size
    paging_size                   INTEGER       NOT NULL DEFAULT 500,
    -- Connection pool settings
    pool_min_size                 INTEGER       NOT NULL DEFAULT 2,
    pool_max_size                 INTEGER       NOT NULL DEFAULT 20,
    pool_connect_timeout_seconds  INTEGER       NOT NULL DEFAULT 10,
    pool_response_timeout_seconds INTEGER       NOT NULL DEFAULT 30,
    -- ── Account enable/disable attribute config (§4.1 / OI-001) ────────────
    -- LDAP attribute that represents enabled/disabled state
    enable_disable_attribute      VARCHAR(255),
    -- Whether the attribute is a boolean toggle or a string value
    enable_disable_value_type     VARCHAR(10)   DEFAULT 'STRING',
    -- Value to write when enabling the account (clear/set)
    enable_value                  VARCHAR(500),
    -- Value to write when disabling the account
    disable_value                 VARCHAR(500),
    -- ── Audit / changelog source ─────────────────────────────────────────────
    -- FK constraint added in V4 after audit_data_sources is created
    audit_data_source_id          UUID,
    -- Marks this directory as the LDAP authentication source for superadmins
    is_superadmin_source          BOOLEAN       NOT NULL DEFAULT FALSE,
    enabled                       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at                    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_directory_connections PRIMARY KEY (id),
    CONSTRAINT fk_dir_conn_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT chk_dir_ssl_mode
        CHECK (ssl_mode IN ('NONE', 'LDAPS', 'STARTTLS')),
    CONSTRAINT chk_dir_enable_disable_value_type
        CHECK (enable_disable_value_type IN ('BOOLEAN', 'STRING'))
);

CREATE INDEX idx_dir_conn_tenant  ON directory_connections (tenant_id);
CREATE INDEX idx_dir_conn_enabled ON directory_connections (tenant_id, enabled);
-- Enforce at most one superadmin-source directory across the entire installation
CREATE UNIQUE INDEX uq_dir_conn_superadmin_source
    ON directory_connections (is_superadmin_source)
    WHERE is_superadmin_source = TRUE;

-- ── Resolve deferred FK: superadmin_accounts → directory_connections ─────────
ALTER TABLE superadmin_accounts
    ADD CONSTRAINT fk_superadmin_ldap_source_dir
    FOREIGN KEY (ldap_source_directory_id)
    REFERENCES directory_connections (id)
    ON DELETE SET NULL;

-- ── User base DNs ─────────────────────────────────────────────────────────────
-- OUs / branches that contain user entries for this directory (§4.1)
CREATE TABLE directory_user_base_dns (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    directory_id  UUID          NOT NULL,
    dn            VARCHAR(1000) NOT NULL,
    display_order INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_dir_user_base_dns PRIMARY KEY (id),
    CONSTRAINT fk_user_base_dns_dir
        FOREIGN KEY (directory_id) REFERENCES directory_connections (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_base_dns_dir ON directory_user_base_dns (directory_id);

-- ── Group base DNs ────────────────────────────────────────────────────────────
-- OUs / branches that contain group entries for this directory (§4.1)
CREATE TABLE directory_group_base_dns (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    directory_id  UUID          NOT NULL,
    dn            VARCHAR(1000) NOT NULL,
    display_order INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_dir_group_base_dns PRIMARY KEY (id),
    CONSTRAINT fk_group_base_dns_dir
        FOREIGN KEY (directory_id) REFERENCES directory_connections (id) ON DELETE CASCADE
);

CREATE INDEX idx_group_base_dns_dir ON directory_group_base_dns (directory_id);
