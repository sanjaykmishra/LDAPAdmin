-- V10: Scheduled report job definitions (§9.2).
-- report_params is JSONB so each report type can carry its own parameters
-- without requiring separate tables per type.

CREATE TABLE scheduled_report_jobs (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID          NOT NULL,
    directory_id        UUID          NOT NULL,
    name                VARCHAR(255)  NOT NULL,
    -- One of the seven standard report types defined in §9.1
    report_type         VARCHAR(50)   NOT NULL,
    -- Report-specific parameters, e.g.:
    --   USERS_IN_GROUP      → {"groupDn": "cn=staff,dc=example,dc=com"}
    --   USERS_IN_BRANCH     → {"branchDn": "ou=people,dc=example,dc=com"}
    --   RECENTLY_ADDED etc. → {"lookbackDays": 30}
    report_params       JSONB,
    -- Spring/Quartz cron expression (6 or 7 fields)
    cron_expression     VARCHAR(100)  NOT NULL,
    output_format       VARCHAR(10)   NOT NULL DEFAULT 'CSV',
    delivery_method     VARCHAR(10)   NOT NULL DEFAULT 'EMAIL',
    -- Comma-separated recipient email addresses (delivery_method = 'EMAIL')
    delivery_recipients TEXT,
    -- Object-key prefix written to the tenant S3 bucket (delivery_method = 'S3')
    s3_key_prefix       VARCHAR(500),
    enabled             BOOLEAN       NOT NULL DEFAULT TRUE,
    -- Populated after each execution attempt
    last_run_at         TIMESTAMPTZ,
    last_run_status     VARCHAR(50),
    last_run_message    TEXT,
    -- SET NULL if the creating admin is later deleted
    created_by_admin_id UUID,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_scheduled_report_jobs PRIMARY KEY (id),
    CONSTRAINT fk_report_job_tenant
        FOREIGN KEY (tenant_id)            REFERENCES tenants (id)              ON DELETE CASCADE,
    CONSTRAINT fk_report_job_directory
        FOREIGN KEY (directory_id)         REFERENCES directory_connections (id) ON DELETE CASCADE,
    CONSTRAINT fk_report_job_created_by
        FOREIGN KEY (created_by_admin_id)  REFERENCES admin_accounts (id)       ON DELETE SET NULL,
    CONSTRAINT chk_report_type CHECK (report_type IN (
        'USERS_IN_GROUP',
        'USERS_IN_BRANCH',
        'USERS_WITH_NO_GROUP',
        'RECENTLY_ADDED',
        'RECENTLY_MODIFIED',
        'RECENTLY_DELETED',
        'DISABLED_ACCOUNTS'
    )),
    CONSTRAINT chk_report_output_format
        CHECK (output_format    IN ('CSV', 'PDF')),
    CONSTRAINT chk_report_delivery_method
        CHECK (delivery_method  IN ('EMAIL', 'S3'))
);

CREATE INDEX idx_report_jobs_tenant  ON scheduled_report_jobs (tenant_id);
CREATE INDEX idx_report_jobs_dir     ON scheduled_report_jobs (directory_id);
CREATE INDEX idx_report_jobs_enabled ON scheduled_report_jobs (tenant_id, enabled);
