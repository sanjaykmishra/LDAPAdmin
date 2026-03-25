-- HR system integration tables (Feature 3.1: BambooHR)

-- HR connector configuration (per directory)
CREATE TABLE hr_connections (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id      UUID NOT NULL REFERENCES directory_connections(id) ON DELETE CASCADE,
    provider          VARCHAR(50) NOT NULL DEFAULT 'BAMBOOHR',
    display_name      VARCHAR(200) NOT NULL,
    enabled           BOOLEAN NOT NULL DEFAULT false,

    -- BambooHR-specific config
    subdomain         VARCHAR(200),
    api_key_encrypted TEXT,

    -- Identity matching config
    match_attribute   VARCHAR(100) NOT NULL DEFAULT 'mail',
    match_field       VARCHAR(100) NOT NULL DEFAULT 'workEmail',

    -- Sync schedule
    sync_cron         VARCHAR(50) NOT NULL DEFAULT '0 0 * * * ?',
    last_sync_at      TIMESTAMPTZ,
    last_sync_status  VARCHAR(20),
    last_sync_message TEXT,
    last_sync_employee_count INTEGER,

    created_by        UUID REFERENCES accounts(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(directory_id, provider)
);

CREATE INDEX idx_hr_connections_directory ON hr_connections(directory_id);

-- Cached HR employee records (refreshed each sync)
CREATE TABLE hr_employees (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hr_connection_id  UUID NOT NULL REFERENCES hr_connections(id) ON DELETE CASCADE,
    employee_id       VARCHAR(100) NOT NULL,
    work_email        VARCHAR(500),
    first_name        VARCHAR(200),
    last_name         VARCHAR(200),
    display_name      VARCHAR(500),
    department        VARCHAR(200),
    job_title         VARCHAR(200),
    status            VARCHAR(50) NOT NULL,
    hire_date         DATE,
    termination_date  DATE,
    supervisor_id     VARCHAR(100),
    supervisor_email  VARCHAR(500),

    -- Identity matching result
    matched_ldap_dn   VARCHAR(1000),
    match_confidence  VARCHAR(20),

    last_synced_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE(hr_connection_id, employee_id)
);

CREATE INDEX idx_hr_employees_connection ON hr_employees(hr_connection_id);
CREATE INDEX idx_hr_employees_status ON hr_employees(hr_connection_id, status);
CREATE INDEX idx_hr_employees_matched ON hr_employees(hr_connection_id, matched_ldap_dn);

-- Sync run history (audit trail)
CREATE TABLE hr_sync_runs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hr_connection_id  UUID NOT NULL REFERENCES hr_connections(id) ON DELETE CASCADE,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    status            VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    total_employees   INTEGER,
    new_employees     INTEGER DEFAULT 0,
    updated_employees INTEGER DEFAULT 0,
    terminated_count  INTEGER DEFAULT 0,
    matched_count     INTEGER DEFAULT 0,
    unmatched_count   INTEGER DEFAULT 0,
    orphaned_count    INTEGER DEFAULT 0,
    error_message     TEXT,
    triggered_by      VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED'
);

CREATE INDEX idx_hr_sync_runs_connection ON hr_sync_runs(hr_connection_id, started_at DESC);
