-- V6: Tenant-scoped administrator accounts (§3.2).
-- No credentials are stored here; authentication is handled entirely by the
-- mechanism configured in tenant_auth_configs (LDAP bind or SAML SSO).

CREATE TABLE admin_accounts (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID         NOT NULL,
    username      VARCHAR(255) NOT NULL,
    display_name  VARCHAR(255),
    email         VARCHAR(255),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_admin_accounts         PRIMARY KEY (id),
    CONSTRAINT uq_admin_tenant_username  UNIQUE (tenant_id, username),
    CONSTRAINT fk_admin_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE
);

CREATE INDEX idx_admin_accounts_tenant ON admin_accounts (tenant_id);
CREATE INDEX idx_admin_accounts_active ON admin_accounts (tenant_id, active);
