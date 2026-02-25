-- V14: Unify superadmin_accounts and admin_accounts into a single accounts table.
--
-- The new table carries a role column (SUPERADMIN | ADMIN) and an auth_type
-- column (LOCAL | LDAP) so every account in the application is represented
-- by exactly one row.  Permission tables are re-targeted to accounts(id).

-- ── 1. Create unified accounts table ───────────────────────────────────────────
CREATE TABLE accounts (
    id            UUID          NOT NULL DEFAULT gen_random_uuid(),
    username      VARCHAR(255)  NOT NULL,
    display_name  VARCHAR(255),
    email         VARCHAR(255),
    -- Application-level role
    role          VARCHAR(20)   NOT NULL,
    -- How this account authenticates
    --   LOCAL — bcrypt password_hash stored here
    --   LDAP  — bind performed against the LDAP auth server in application_settings
    auth_type     VARCHAR(10)   NOT NULL DEFAULT 'LOCAL',
    -- bcrypt hash; NULL for LDAP-sourced accounts or admin accounts pending
    -- first-login password setup
    password_hash VARCHAR(255),
    -- Distinguished name in the LDAP directory (LDAP auth_type only)
    ldap_dn       VARCHAR(1000),
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_accounts          PRIMARY KEY (id),
    CONSTRAINT uq_account_username  UNIQUE (username),
    CONSTRAINT chk_account_role     CHECK (role     IN ('SUPERADMIN', 'ADMIN')),
    CONSTRAINT chk_account_auth_type CHECK (auth_type IN ('LOCAL', 'LDAP'))
);

CREATE INDEX idx_accounts_role   ON accounts (role);
CREATE INDEX idx_accounts_active ON accounts (active);

-- ── 2. Migrate superadmin_accounts → accounts ──────────────────────────────────
INSERT INTO accounts
    (id, username, display_name, email, role, auth_type, password_hash, ldap_dn,
     active, last_login_at, created_at, updated_at)
SELECT
    id,
    username,
    display_name,
    email,
    'SUPERADMIN'  AS role,
    account_type  AS auth_type,   -- already 'LOCAL' or 'LDAP'
    password_hash,
    ldap_dn,
    active,
    last_login_at,
    created_at,
    updated_at
FROM superadmin_accounts;

-- ── 3. Migrate admin_accounts → accounts ──────────────────────────────────────
-- Admin accounts had no stored credentials (they authenticated via
-- tenant_auth_configs which is now gone).  They are migrated as LOCAL accounts
-- with a NULL password_hash.  A superadmin must set each admin's password, or
-- change their auth_type to LDAP, before they can log in.
INSERT INTO accounts
    (id, username, display_name, email, role, auth_type, password_hash,
     active, last_login_at, created_at, updated_at)
SELECT
    id,
    username,
    display_name,
    email,
    'ADMIN'  AS role,
    'LOCAL'  AS auth_type,
    NULL     AS password_hash,
    active,
    last_login_at,
    created_at,
    updated_at
FROM admin_accounts;

-- ── 4. Re-target admin_directory_roles → accounts ─────────────────────────────
ALTER TABLE admin_directory_roles
    DROP CONSTRAINT fk_adr_admin,
    ADD CONSTRAINT fk_adr_account
        FOREIGN KEY (admin_account_id) REFERENCES accounts (id) ON DELETE CASCADE;

-- ── 5. Re-target admin_branch_restrictions → accounts ─────────────────────────
ALTER TABLE admin_branch_restrictions
    DROP CONSTRAINT fk_abr_admin,
    ADD CONSTRAINT fk_abr_account
        FOREIGN KEY (admin_account_id) REFERENCES accounts (id) ON DELETE CASCADE;

-- ── 6. Re-target admin_feature_permissions → accounts ─────────────────────────
ALTER TABLE admin_feature_permissions
    DROP CONSTRAINT fk_afp_admin,
    ADD CONSTRAINT fk_afp_account
        FOREIGN KEY (admin_account_id) REFERENCES accounts (id) ON DELETE CASCADE;

-- ── 7. Re-target scheduled_report_jobs.created_by_admin_id → accounts ─────────
ALTER TABLE scheduled_report_jobs
    DROP CONSTRAINT fk_report_job_created_by,
    ADD CONSTRAINT fk_report_job_created_by
        FOREIGN KEY (created_by_admin_id) REFERENCES accounts (id) ON DELETE SET NULL;

-- ── 8. Drop superadmin_accounts ────────────────────────────────────────────────
-- The FK from superadmin_accounts → directory_connections was added in V3;
-- drop it so the table can be removed.
ALTER TABLE superadmin_accounts
    DROP CONSTRAINT fk_superadmin_ldap_source_dir;

DROP TABLE superadmin_accounts;

-- ── 9. Drop admin_accounts ─────────────────────────────────────────────────────
ALTER TABLE admin_accounts
    DROP CONSTRAINT uq_admin_username;

DROP TABLE admin_accounts;

-- ── 10. Remove is_superadmin_source from directory_connections ─────────────────
-- LDAP authentication config is now global (application_settings, V17).
DROP INDEX uq_dir_conn_superadmin_source;

ALTER TABLE directory_connections
    DROP COLUMN is_superadmin_source;
