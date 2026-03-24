-- Campaign templates: reusable campaign configurations
CREATE TABLE campaign_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id    UUID        NOT NULL REFERENCES directory_connections(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    config          JSONB       NOT NULL,
    created_by      UUID        NOT NULL REFERENCES accounts(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ct_directory ON campaign_templates (directory_id);
