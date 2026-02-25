-- V16: Replace branch-scoped attribute profiles with per-objectclass attribute
--      configuration, and replace the free-text object_classes column on
--      directory_connections with a proper relational table.
--
-- Before (V8 model):
--   attribute_profiles      — one profile per (directory, branch_dn)
--   attribute_profile_entries — attribute rows within a profile
--
-- After (V16 model):
--   directory_objectclasses        — objectclasses permitted for a directory
--   objectclass_attribute_configs  — per-attribute behaviour within an objectclass
--
-- The two new tables compose: one directory → many objectclasses → many attributes.

-- ── 1. Drop old attribute profile tables ───────────────────────────────────────
-- Child table first to satisfy the FK constraint.
DROP TABLE attribute_profile_entries;
DROP TABLE attribute_profiles;

-- ── 2. Drop free-text object_classes from directory_connections ────────────────
ALTER TABLE directory_connections
    DROP COLUMN object_classes;

-- ── 3. Create directory_objectclasses ─────────────────────────────────────────
-- Defines which LDAP objectclasses may be used for user entries in a given
-- directory.  Each row represents one selectable objectclass in the user
-- creation form.

CREATE TABLE directory_objectclasses (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    directory_id      UUID         NOT NULL,
    -- Exact objectClass name as it appears in the LDAP schema
    object_class_name VARCHAR(255) NOT NULL,
    -- Human-readable label shown in the UI instead of the raw objectClass name
    display_name      VARCHAR(255),
    -- Controls the order of objectclasses in the creation form dropdown
    display_order     INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT pk_directory_objectclasses     PRIMARY KEY (id),
    CONSTRAINT uq_dir_objectclass_name        UNIQUE (directory_id, object_class_name),
    CONSTRAINT fk_objectclass_directory
        FOREIGN KEY (directory_id) REFERENCES directory_connections (id) ON DELETE CASCADE
);

CREATE INDEX idx_objectclass_directory ON directory_objectclasses (directory_id);

-- ── 4. Create objectclass_attribute_configs ────────────────────────────────────
-- Specifies how each LDAP attribute within an objectclass is presented and
-- validated in the user creation and edit forms.

CREATE TABLE objectclass_attribute_configs (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    objectclass_id     UUID         NOT NULL,
    -- LDAP attribute name as returned by schema discovery
    attribute_name     VARCHAR(255) NOT NULL,
    -- Override label shown in the UI; falls back to attribute_name when NULL
    custom_label       VARCHAR(255),
    -- Whether the attribute must be supplied when creating a new user entry
    required_on_create BOOLEAN      NOT NULL DEFAULT FALSE,
    -- When FALSE the field is rendered read-only in the edit form
    editable_on_edit   BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Presentation hint that drives the input widget rendered by the frontend
    input_type         VARCHAR(20)  NOT NULL DEFAULT 'TEXT',
    -- Controls the order of attributes within a form or list view
    display_order      INTEGER      NOT NULL DEFAULT 0,
    -- When TRUE the attribute appears as a column in the user search results list
    visible_in_list    BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_objectclass_attribute_configs PRIMARY KEY (id),
    CONSTRAINT uq_objectclass_attribute         UNIQUE (objectclass_id, attribute_name),
    CONSTRAINT fk_attr_config_objectclass
        FOREIGN KEY (objectclass_id) REFERENCES directory_objectclasses (id) ON DELETE CASCADE,
    CONSTRAINT chk_attr_input_type CHECK (input_type IN (
        'TEXT', 'TEXTAREA', 'PASSWORD', 'BOOLEAN',
        'DATE', 'DATETIME', 'MULTI_VALUE', 'DN_LOOKUP'
    ))
);

CREATE INDEX idx_attr_config_objectclass ON objectclass_attribute_configs (objectclass_id);
