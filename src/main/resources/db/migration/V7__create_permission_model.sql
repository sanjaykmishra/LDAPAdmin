-- V7: Four-dimensional permission model (§3.2).
--
--  Dimension 1 + 2 — admin_directory_roles
--    Base role per admin per directory. The role type (Dimension 1) is the
--    base_role column; the per-directory scope is the FK to directory_connections.
--    No row for a given (admin, directory) pair = access denied to that directory.
--
--  Dimension 3 — admin_branch_restrictions
--    Zero or more OU/branch DN restrictions per admin per directory.
--    No rows = full directory access within the admin's assigned role.
--
--  Dimension 4 — admin_feature_permissions
--    Per-feature enable/disable overrides per admin account.
--    Presence of a row overrides the capability implied by the base role.

-- ─────────────────────────────────────────────────────────────────────────────
-- Dimensions 1 + 2
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE admin_directory_roles (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    admin_account_id UUID        NOT NULL,
    directory_id     UUID        NOT NULL,
    base_role        VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_admin_directory_roles PRIMARY KEY (id),
    CONSTRAINT uq_admin_dir_role        UNIQUE (admin_account_id, directory_id),
    CONSTRAINT fk_adr_admin
        FOREIGN KEY (admin_account_id) REFERENCES admin_accounts (id)        ON DELETE CASCADE,
    CONSTRAINT fk_adr_directory
        FOREIGN KEY (directory_id)     REFERENCES directory_connections (id)  ON DELETE CASCADE,
    CONSTRAINT chk_admin_base_role
        CHECK (base_role IN ('ADMIN', 'READ_ONLY'))
);

CREATE INDEX idx_adr_admin     ON admin_directory_roles (admin_account_id);
CREATE INDEX idx_adr_directory ON admin_directory_roles (directory_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Dimension 3
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE admin_branch_restrictions (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    admin_account_id UUID          NOT NULL,
    directory_id     UUID          NOT NULL,
    branch_dn        VARCHAR(1000) NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_admin_branch_restrictions PRIMARY KEY (id),
    CONSTRAINT uq_admin_branch              UNIQUE (admin_account_id, directory_id, branch_dn),
    CONSTRAINT fk_abr_admin
        FOREIGN KEY (admin_account_id) REFERENCES admin_accounts (id)        ON DELETE CASCADE,
    CONSTRAINT fk_abr_directory
        FOREIGN KEY (directory_id)     REFERENCES directory_connections (id)  ON DELETE CASCADE
);

CREATE INDEX idx_abr_admin_dir ON admin_branch_restrictions (admin_account_id, directory_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Dimension 4
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE admin_feature_permissions (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    admin_account_id UUID         NOT NULL,
    -- One of the 12 feature keys defined in §3.2
    feature_key      VARCHAR(100) NOT NULL,
    -- TRUE = feature enabled; FALSE = feature explicitly disabled
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_admin_feature_permissions PRIMARY KEY (id),
    CONSTRAINT uq_admin_feature             UNIQUE (admin_account_id, feature_key),
    CONSTRAINT fk_afp_admin
        FOREIGN KEY (admin_account_id) REFERENCES admin_accounts (id) ON DELETE CASCADE,
    CONSTRAINT chk_feature_key CHECK (feature_key IN (
        'user.create',
        'user.edit',
        'user.delete',
        'user.enable_disable',
        'user.move',
        'group.manage_members',
        'group.create_delete',
        'bulk.import',
        'bulk.export',
        'reports.run',
        'reports.export',
        'reports.schedule'
    ))
);

CREATE INDEX idx_afp_admin ON admin_feature_permissions (admin_account_id);
