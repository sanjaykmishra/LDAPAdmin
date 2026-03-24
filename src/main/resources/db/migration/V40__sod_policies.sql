-- V40: Separation of Duties (SoD) policy engine tables

-- SoD policies define mutually exclusive group pairs
CREATE TABLE sod_policies (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    directory_id    UUID         NOT NULL REFERENCES directory_connections(id) ON DELETE CASCADE,
    group_a_dn      VARCHAR(1024) NOT NULL,
    group_b_dn      VARCHAR(1024) NOT NULL,
    group_a_name    VARCHAR(255),
    group_b_name    VARCHAR(255),
    severity        VARCHAR(10)  NOT NULL CHECK (severity IN ('HIGH', 'MEDIUM', 'LOW')),
    action          VARCHAR(10)  NOT NULL CHECK (action IN ('ALERT', 'BLOCK')),
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    created_by      UUID         NOT NULL REFERENCES accounts(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_sod_policies_directory ON sod_policies(directory_id);
CREATE INDEX idx_sod_policies_enabled   ON sod_policies(directory_id) WHERE enabled = true;

-- SoD violations detected by policy scans or real-time checks
CREATE TABLE sod_violations (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id           UUID         NOT NULL REFERENCES sod_policies(id) ON DELETE CASCADE,
    user_dn             VARCHAR(1024) NOT NULL,
    user_display_name   VARCHAR(255),
    detected_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ,
    status              VARCHAR(10)  NOT NULL CHECK (status IN ('OPEN', 'RESOLVED', 'EXEMPTED')),
    exempted_by         UUID         REFERENCES accounts(id),
    exemption_reason    TEXT
);

CREATE INDEX idx_sod_violations_policy ON sod_violations(policy_id);
CREATE INDEX idx_sod_violations_status ON sod_violations(status);
CREATE INDEX idx_sod_violations_user   ON sod_violations(policy_id, user_dn);
