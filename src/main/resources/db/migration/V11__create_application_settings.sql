-- V11: Per-tenant application settings (§10.2).
-- Covers branding, session timeout, SMTP mail relay, and S3-compatible storage.
-- Exactly one row per tenant (enforced by unique constraint on tenant_id).

CREATE TABLE application_settings (
    id                         UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                  UUID          NOT NULL,

    -- ── Branding ──────────────────────────────────────────────────────────────
    app_name                   VARCHAR(255)  NOT NULL DEFAULT 'LDAP Portal',
    logo_url                   VARCHAR(1000),
    primary_colour             VARCHAR(20),
    secondary_colour           VARCHAR(20),

    -- ── Session ───────────────────────────────────────────────────────────────
    session_timeout_minutes    INTEGER       NOT NULL DEFAULT 60,

    -- ── SMTP mail relay (report delivery + async export notifications) ────────
    smtp_host                  VARCHAR(255),
    smtp_port                  INTEGER                DEFAULT 587,
    smtp_sender_address        VARCHAR(255),
    smtp_username              VARCHAR(255),
    -- AES-256 encrypted SMTP password
    smtp_password_encrypted    TEXT,
    smtp_use_tls               BOOLEAN       NOT NULL DEFAULT TRUE,

    -- ── S3-compatible object storage (saved reports + async exports) ──────────
    s3_endpoint_url            VARCHAR(500),
    s3_bucket_name             VARCHAR(255),
    s3_access_key              VARCHAR(255),
    -- AES-256 encrypted S3 secret key
    s3_secret_key_encrypted    TEXT,
    s3_region                  VARCHAR(100),
    -- TTL for pre-signed download links in hours (default 24 h per §7.2)
    s3_presigned_url_ttl_hours INTEGER       NOT NULL DEFAULT 24,

    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_application_settings PRIMARY KEY (id),
    CONSTRAINT uq_app_settings_tenant   UNIQUE (tenant_id),
    CONSTRAINT fk_app_settings_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE
);
