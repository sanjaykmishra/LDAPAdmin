-- V18: Introduce the Realm model as the primary container for users and groups
--      within a directory, and re-scope objectclass configuration and admin
--      permissions from directory-level to realm-level.
--
-- Changes:
--   directory_connections  — add is_user_repository flag and user_creation_base_dn
--   realms                 — new table; one directory → many realms
--   realm_auxiliary_objectclasses — auxiliary objectclasses per realm
--   realm_objectclasses    — replaces directory_objectclasses (scoped to realm)
--   objectclass_attribute_configs — FK re-targeted to realm_objectclasses
--   admin_realm_roles      — replaces admin_directory_roles (realm-level base role)
--   admin_branch_restrictions — directory_id replaced by realm_id

-- ── 1. Extend directory_connections ──────────────────────────────────────────

ALTER TABLE directory_connections
    ADD COLUMN is_user_repository    BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN user_creation_base_dn VARCHAR(1000);

COMMENT ON COLUMN directory_connections.is_user_repository IS
    'TRUE when this directory is the authoritative store for application user accounts.';
COMMENT ON COLUMN directory_connections.user_creation_base_dn IS
    'DN of the container in which new application user entries are created.  '
    'Required when is_user_repository = TRUE.';

-- ── 2. Create realms ──────────────────────────────────────────────────────────
-- A realm is a logical partition of a directory that groups a user OU, a group
-- OU, and the objectclass model used for user entries.

CREATE TABLE realms (
    id                       UUID          NOT NULL DEFAULT gen_random_uuid(),
    directory_id             UUID          NOT NULL,
    name                     VARCHAR(255)  NOT NULL,
    -- The LDAP DN that is the base for user entry searches in this realm
    user_base_dn             VARCHAR(1000) NOT NULL,
    -- The LDAP DN that is the base for group entry searches in this realm
    group_base_dn            VARCHAR(1000) NOT NULL,
    -- The structural (primary) objectClass applied to new user entries
    primary_user_objectclass VARCHAR(255)  NOT NULL,
    display_order            INTEGER       NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_realms PRIMARY KEY (id),
    CONSTRAINT fk_realms_directory
        FOREIGN KEY (directory_id) REFERENCES directory_connections (id) ON DELETE CASCADE
);

CREATE INDEX idx_realms_directory ON realms (directory_id);

-- ── 3. Create realm_auxiliary_objectclasses ───────────────────────────────────
-- Additional (auxiliary) objectClasses applied alongside the primary objectClass
-- when creating new user entries in a realm.

CREATE TABLE realm_auxiliary_objectclasses (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    realm_id         UUID         NOT NULL,
    objectclass_name VARCHAR(255) NOT NULL,
    display_order    INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_realm_aux_objectclasses PRIMARY KEY (id),
    CONSTRAINT uq_realm_aux_objectclass   UNIQUE (realm_id, objectclass_name),
    CONSTRAINT fk_realm_aux_oc_realm
        FOREIGN KEY (realm_id) REFERENCES realms (id) ON DELETE CASCADE
);

-- ── 4. Create realm_objectclasses (replaces directory_objectclasses) ──────────
-- Defines the form used for user entries in a realm.  Each row represents one
-- LDAP objectClass whose attributes are configured via objectclass_attribute_configs.

CREATE TABLE realm_objectclasses (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    realm_id          UUID         NOT NULL,
    object_class_name VARCHAR(255) NOT NULL,
    display_name      VARCHAR(255),
    display_order     INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_realm_objectclasses PRIMARY KEY (id),
    CONSTRAINT uq_realm_objectclass   UNIQUE (realm_id, object_class_name),
    CONSTRAINT fk_realm_objectclass_realm
        FOREIGN KEY (realm_id) REFERENCES realms (id) ON DELETE CASCADE
);

CREATE INDEX idx_realm_objectclasses_realm ON realm_objectclasses (realm_id);

-- ── 5. Re-target objectclass_attribute_configs → realm_objectclasses ──────────
-- Existing configs are tied to directory_objectclasses rows that have no realm
-- mapping; clear them before switching the FK.

DELETE FROM objectclass_attribute_configs;

ALTER TABLE objectclass_attribute_configs
    DROP CONSTRAINT fk_attr_config_objectclass,
    ADD  CONSTRAINT fk_attr_config_objectclass
        FOREIGN KEY (objectclass_id) REFERENCES realm_objectclasses (id) ON DELETE CASCADE;

-- ── 6. Drop directory_objectclasses ───────────────────────────────────────────
-- objectclass_attribute_configs FK has been re-targeted above; safe to drop.

DROP TABLE directory_objectclasses;

-- ── 7. Create admin_realm_roles (replaces admin_directory_roles) ───────────────
-- Assigns a base role (ADMIN | READ_ONLY) to an account for a specific realm.
-- Absence of a row for a given (account, realm) pair = access denied to that realm.

CREATE TABLE admin_realm_roles (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    admin_account_id UUID        NOT NULL,
    realm_id         UUID        NOT NULL,
    base_role        VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_admin_realm_roles          PRIMARY KEY (id),
    CONSTRAINT uq_admin_realm_role           UNIQUE (admin_account_id, realm_id),
    CONSTRAINT chk_admin_realm_role_base_role CHECK (base_role IN ('ADMIN', 'READ_ONLY')),
    CONSTRAINT fk_arr_account
        FOREIGN KEY (admin_account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_arr_realm
        FOREIGN KEY (realm_id) REFERENCES realms (id) ON DELETE CASCADE
);

CREATE INDEX idx_arr_account ON admin_realm_roles (admin_account_id);
CREATE INDEX idx_arr_realm   ON admin_realm_roles (realm_id);

-- ── 8. Drop admin_directory_roles ─────────────────────────────────────────────

DROP TABLE admin_directory_roles;

-- ── 9. Re-scope admin_branch_restrictions from directory to realm ──────────────
-- Permissions are now granted at realm level; the directory_id scope is
-- replaced by realm_id.  Existing rows cannot be automatically migrated
-- (no realm assignments exist), so they are cleared first.

DELETE FROM admin_branch_restrictions;

DROP INDEX idx_abr_admin_dir;

ALTER TABLE admin_branch_restrictions
    DROP CONSTRAINT uq_admin_branch,
    DROP CONSTRAINT fk_abr_directory,
    DROP COLUMN directory_id,
    ADD  COLUMN realm_id UUID NOT NULL;

ALTER TABLE admin_branch_restrictions
    ADD CONSTRAINT uq_admin_realm_branch UNIQUE (admin_account_id, realm_id, branch_dn),
    ADD CONSTRAINT fk_abr_realm
        FOREIGN KEY (realm_id) REFERENCES realms (id) ON DELETE CASCADE;

CREATE INDEX idx_abr_admin_realm ON admin_branch_restrictions (admin_account_id, realm_id);
