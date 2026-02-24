-- V2: Superadmin accounts.
-- Superadmins are independent of any tenant and always authenticate against
-- this table (local bcrypt) or a designated LDAP directory (LDAP-sourced).
-- The FK to directory_connections is deferred and added in V3 once that
-- table exists.

CREATE TABLE superadmin_accounts (
    id                       UUID         NOT NULL DEFAULT gen_random_uuid(),
    username                 VARCHAR(255) NOT NULL,
    display_name             VARCHAR(255),
    email                    VARCHAR(255),
    -- 'LOCAL'  — bcrypt password stored in password_hash
    -- 'LDAP'   — authentication delegated to ldap_source_directory_id
    account_type             VARCHAR(10)  NOT NULL DEFAULT 'LOCAL',
    -- bcrypt-hashed password; NULL for LDAP-sourced accounts
    password_hash            VARCHAR(255),
    -- FK to directory_connections added in V3
    ldap_source_directory_id UUID,
    -- Distinguished name in the source LDAP directory
    ldap_dn                  VARCHAR(1000),
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at            TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_superadmin_accounts PRIMARY KEY (id),
    CONSTRAINT chk_superadmin_account_type
        CHECK (account_type IN ('LOCAL', 'LDAP')),
    -- LOCAL accounts must have a password hash
    CONSTRAINT chk_superadmin_local_requires_password
        CHECK (account_type = 'LDAP' OR password_hash IS NOT NULL)
);

CREATE UNIQUE INDEX uq_superadmin_username ON superadmin_accounts (username);
CREATE INDEX        idx_superadmin_active  ON superadmin_accounts (active);
