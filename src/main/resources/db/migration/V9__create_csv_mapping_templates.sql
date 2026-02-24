-- V9: Named CSV column-to-attribute mapping templates (§7.1, §10.2).
-- Templates are scoped per directory and can be selected on future imports
-- to avoid re-mapping columns each time.

CREATE TABLE csv_mapping_templates (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID         NOT NULL,
    directory_id         UUID         NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    -- LDAP attribute used to match CSV rows against existing directory entries
    target_key_attribute VARCHAR(255) NOT NULL DEFAULT 'uid',
    -- Default conflict resolution applied when a matching entry already exists
    conflict_handling    VARCHAR(20)  NOT NULL DEFAULT 'PROMPT',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_csv_mapping_templates  PRIMARY KEY (id),
    CONSTRAINT uq_csv_template_dir_name  UNIQUE (directory_id, name),
    CONSTRAINT fk_csv_template_tenant
        FOREIGN KEY (tenant_id)    REFERENCES tenants (id)              ON DELETE CASCADE,
    CONSTRAINT fk_csv_template_directory
        FOREIGN KEY (directory_id) REFERENCES directory_connections (id) ON DELETE CASCADE,
    CONSTRAINT chk_conflict_handling
        CHECK (conflict_handling IN ('PROMPT', 'SKIP', 'OVERWRITE'))
);

CREATE INDEX idx_csv_templates_dir    ON csv_mapping_templates (directory_id);
CREATE INDEX idx_csv_templates_tenant ON csv_mapping_templates (tenant_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- Individual column mappings within a template
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE csv_mapping_template_entries (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    template_id      UUID         NOT NULL,
    -- Header name from the CSV file
    csv_column_name  VARCHAR(255) NOT NULL,
    -- 0-based column index; optional fallback when no header row is present
    csv_column_index INTEGER,
    -- NULL when ignored = TRUE; the column is present but discarded on import
    ldap_attribute   VARCHAR(255),
    ignored          BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_csv_mapping_template_entries PRIMARY KEY (id),
    CONSTRAINT uq_csv_entry_template_col        UNIQUE (template_id, csv_column_name),
    CONSTRAINT fk_csv_entry_template
        FOREIGN KEY (template_id) REFERENCES csv_mapping_templates (id) ON DELETE CASCADE
);

CREATE INDEX idx_csv_entries_template ON csv_mapping_template_entries (template_id);
