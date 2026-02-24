-- V4: Audit data sources — separate LDAP changelog reader connections (§8.1).
-- One audit source may serve multiple directory connections (1:N).
-- Also resolves the deferred FK from directory_connections.

CREATE TABLE audit_data_sources (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID          NOT NULL,
    display_name            VARCHAR(255)  NOT NULL,
    host                    VARCHAR(255)  NOT NULL,
    port                    INTEGER       NOT NULL DEFAULT 389,
    ssl_mode                VARCHAR(10)   NOT NULL DEFAULT 'NONE',
    trust_all_certs         BOOLEAN       NOT NULL DEFAULT FALSE,
    trusted_certificate_pem TEXT,
    bind_dn                 VARCHAR(1000) NOT NULL,
    -- AES-256 encrypted bind password
    bind_password_encrypted TEXT          NOT NULL,
    -- Base DN for changelog reads (e.g. cn=changelog)
    changelog_base_dn       VARCHAR(1000) NOT NULL DEFAULT 'cn=changelog',
    -- Optional: restrict changelog reads to entries under this targetDN subtree
    branch_filter_dn        VARCHAR(1000),
    enabled                 BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_audit_data_sources PRIMARY KEY (id),
    CONSTRAINT fk_audit_source_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT chk_audit_ssl_mode
        CHECK (ssl_mode IN ('NONE', 'LDAPS', 'STARTTLS'))
);

CREATE INDEX idx_audit_source_tenant ON audit_data_sources (tenant_id);

-- ── Resolve deferred FK: directory_connections → audit_data_sources ──────────
ALTER TABLE directory_connections
    ADD CONSTRAINT fk_dir_conn_audit_source
    FOREIGN KEY (audit_data_source_id)
    REFERENCES audit_data_sources (id)
    ON DELETE SET NULL;
