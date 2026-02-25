-- V13: Remove multi-tenancy.
--
-- Drops the tenants root table and all tenant_id foreign keys / columns from
-- every dependent table.  tenant_auth_configs is dropped entirely; its
-- replacement (global admin auth config) is added to application_settings in V17.

-- ── 1. Drop tenant_auth_configs (entirely replaced by application_settings auth config) ──
DROP TABLE tenant_auth_configs;

-- ── 2. audit_events ────────────────────────────────────────────────────────────
DROP INDEX idx_audit_tenant_occurred;

ALTER TABLE audit_events
    DROP CONSTRAINT fk_audit_event_tenant,
    DROP COLUMN tenant_id;

-- Recreate the primary time-ordered access index without the tenant prefix.
CREATE INDEX idx_audit_occurred ON audit_events (occurred_at DESC);

-- ── 3. scheduled_report_jobs ───────────────────────────────────────────────────
DROP INDEX idx_report_jobs_tenant;
DROP INDEX idx_report_jobs_enabled;

ALTER TABLE scheduled_report_jobs
    DROP CONSTRAINT fk_report_job_tenant,
    DROP COLUMN tenant_id;

CREATE INDEX idx_report_jobs_enabled ON scheduled_report_jobs (enabled);

-- ── 4. csv_mapping_templates ───────────────────────────────────────────────────
DROP INDEX idx_csv_templates_tenant;

ALTER TABLE csv_mapping_templates
    DROP CONSTRAINT fk_csv_template_tenant,
    DROP COLUMN tenant_id;

-- ── 5. attribute_profiles ──────────────────────────────────────────────────────
-- attribute_profiles and attribute_profile_entries will be dropped entirely in
-- V16 when the per-objectclass attribute config model is introduced.  Here we
-- only remove the tenant_id FK so the tenants table can be dropped.
DROP INDEX idx_attr_profile_tenant;

ALTER TABLE attribute_profiles
    DROP CONSTRAINT fk_profile_tenant,
    DROP COLUMN tenant_id;

-- ── 6. audit_data_sources ──────────────────────────────────────────────────────
ALTER TABLE audit_data_sources
    DROP CONSTRAINT fk_audit_source_tenant,
    DROP COLUMN tenant_id;

-- ── 7. application_settings ────────────────────────────────────────────────────
ALTER TABLE application_settings
    DROP CONSTRAINT fk_app_settings_tenant,
    DROP CONSTRAINT uq_app_settings_tenant,
    DROP COLUMN tenant_id;

-- ── 8. directory_connections ───────────────────────────────────────────────────
DROP INDEX idx_dir_conn_tenant;
DROP INDEX idx_dir_conn_enabled;

ALTER TABLE directory_connections
    DROP CONSTRAINT fk_dir_conn_tenant,
    DROP COLUMN tenant_id;

-- Recreate the enabled index without the tenant prefix.
CREATE INDEX idx_dir_conn_enabled ON directory_connections (enabled);

-- ── 9. admin_accounts ──────────────────────────────────────────────────────────
-- The composite unique constraint (tenant_id, username) is replaced by a simple
-- unique constraint on username.  NOTE: if multiple tenants shared the same
-- username, this step will fail — ensure usernames are globally unique before
-- running this migration.
ALTER TABLE admin_accounts
    DROP CONSTRAINT fk_admin_tenant,
    DROP CONSTRAINT uq_admin_tenant_username,
    DROP COLUMN tenant_id;

ALTER TABLE admin_accounts
    ADD CONSTRAINT uq_admin_username UNIQUE (username);

-- ── 10. Drop tenants ───────────────────────────────────────────────────────────
DROP TABLE tenants;
