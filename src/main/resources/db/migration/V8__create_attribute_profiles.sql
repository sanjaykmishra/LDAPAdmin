-- V8: Attribute profiles — define how user attributes are presented and
-- validated within a specific OU/branch (§5.2).
--
-- Each (directory, branch_dn) pair has at most one profile.
-- The default fallback profile for a directory uses branch_dn = '*' and
-- is_default = TRUE.  A partial unique index enforces at most one default
-- per directory.

CREATE TABLE attribute_profiles (
    id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID          NOT NULL,
    directory_id UUID          NOT NULL,
    -- OU DN this profile applies to; '*' is the reserved sentinel for the
    -- directory-level default profile
    branch_dn    VARCHAR(1000) NOT NULL,
    display_name VARCHAR(255),
    is_default   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_attribute_profiles     PRIMARY KEY (id),
    CONSTRAINT uq_profile_dir_branch     UNIQUE (directory_id, branch_dn),
    CONSTRAINT fk_profile_tenant
        FOREIGN KEY (tenant_id)    REFERENCES tenants (id)              ON DELETE CASCADE,
    CONSTRAINT fk_profile_directory
        FOREIGN KEY (directory_id) REFERENCES directory_connections (id) ON DELETE CASCADE
);

CREATE INDEX idx_attr_profile_dir    ON attribute_profiles (directory_id);
CREATE INDEX idx_attr_profile_tenant ON attribute_profiles (tenant_id);
-- At most one default profile per directory
CREATE UNIQUE INDEX uq_attr_profile_one_default_per_dir
    ON attribute_profiles (directory_id)
    WHERE is_default = TRUE;

-- ─────────────────────────────────────────────────────────────────────────────
-- Per-attribute configuration rows within a profile
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE attribute_profile_entries (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    profile_id           UUID         NOT NULL,
    -- LDAP attribute name as returned by schema discovery
    attribute_name       VARCHAR(255) NOT NULL,
    -- Override label shown in the UI (replaces raw attribute name)
    custom_label         VARCHAR(255),
    required_on_create   BOOLEAN      NOT NULL DEFAULT FALSE,
    -- If FALSE the field is shown read-only in edit forms
    enabled_on_edit      BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Presentation hint; default inferred from LDAP syntax, admin may override
    input_type           VARCHAR(20)  NOT NULL DEFAULT 'TEXT',
    -- Controls column order in forms and list views
    display_order        INTEGER      NOT NULL DEFAULT 0,
    -- Whether this attribute appears as a column in user search results
    visible_in_list_view BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_attribute_profile_entries PRIMARY KEY (id),
    CONSTRAINT uq_profile_entry_attr         UNIQUE (profile_id, attribute_name),
    CONSTRAINT fk_entry_profile
        FOREIGN KEY (profile_id) REFERENCES attribute_profiles (id) ON DELETE CASCADE,
    CONSTRAINT chk_input_type CHECK (input_type IN (
        'TEXT', 'TEXTAREA', 'PASSWORD', 'BOOLEAN',
        'DATE', 'DATETIME', 'MULTI_VALUE', 'DN_LOOKUP'
    ))
);

CREATE INDEX idx_profile_entries_profile ON attribute_profile_entries (profile_id);
