-- Auditor links: shareable, time-scoped, read-only evidence portal access.
-- Each link grants unauthenticated read access to a scoped evidence package
-- via a cryptographically random token.

CREATE TABLE auditor_links (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    directory_id        UUID          NOT NULL,
    token               VARCHAR(64)   NOT NULL,
    label               VARCHAR(255),

    -- Scope: what the auditor can see
    campaign_ids        JSONB         NOT NULL DEFAULT '[]',
    include_sod         BOOLEAN       NOT NULL DEFAULT TRUE,
    include_entitlements BOOLEAN      NOT NULL DEFAULT FALSE,
    include_audit_events BOOLEAN      NOT NULL DEFAULT TRUE,

    -- Time bounds for evidence window
    data_from           TIMESTAMPTZ,
    data_to             TIMESTAMPTZ,

    -- Link expiry
    expires_at          TIMESTAMPTZ   NOT NULL,

    -- HMAC signature over (token + scope + expiry) for tamper detection
    hmac_signature      VARCHAR(128)  NOT NULL,

    -- Tracking
    created_by          UUID          NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_accessed_at    TIMESTAMPTZ,
    access_count        INTEGER       NOT NULL DEFAULT 0,
    revoked             BOOLEAN       NOT NULL DEFAULT FALSE,
    revoked_at          TIMESTAMPTZ,

    CONSTRAINT pk_auditor_links PRIMARY KEY (id),
    CONSTRAINT uq_auditor_links_token UNIQUE (token),
    CONSTRAINT fk_auditor_links_directory FOREIGN KEY (directory_id)
        REFERENCES directory_connections (id),
    CONSTRAINT fk_auditor_links_created_by FOREIGN KEY (created_by)
        REFERENCES accounts (id)
);

-- Fast lookup by token for portal access (only non-revoked links)
CREATE INDEX idx_auditor_links_token_active
    ON auditor_links (token) WHERE NOT revoked;

-- List links by directory for admin management
CREATE INDEX idx_auditor_links_directory
    ON auditor_links (directory_id, created_at DESC);
