-- V12: Audit event log (§8 — Audit Logging).
--
-- Stores two kinds of events:
--   INTERNAL        — write operations performed through LDAPAdmin's REST API
--   LDAP_CHANGELOG  — change records pulled from an AuditDataSource (cn=changelog)
--
-- actor_* columns are denormalised so records survive admin account deletion.
-- directory_* columns are denormalised for the same reason.

CREATE TABLE audit_events (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID          NOT NULL,

    -- Origin of the event
    source           VARCHAR(20)   NOT NULL,   -- 'INTERNAL' | 'LDAP_CHANGELOG'

    -- Who performed the action (null for LDAP_CHANGELOG events with no match)
    actor_id         UUID,
    actor_type       VARCHAR(20),              -- 'ADMIN' | 'SUPERADMIN'
    actor_username   VARCHAR(255),             -- denormalised for history

    -- Which directory was affected
    directory_id     UUID,
    directory_name   VARCHAR(255),             -- denormalised for history

    -- What happened
    action           VARCHAR(50)   NOT NULL,   -- AuditAction enum db-value
    target_dn        VARCHAR(2000),

    -- Supplementary detail as JSONB (attribute names, old values, etc.)
    detail           JSONB,

    -- Changelog-specific: the changeNumber / CSN from cn=changelog
    changelog_change_number VARCHAR(255),

    -- When the real operation happened vs when LDAPAdmin recorded it
    occurred_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    recorded_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_audit_events PRIMARY KEY (id),
    CONSTRAINT fk_audit_event_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT chk_audit_source
        CHECK (source IN ('INTERNAL', 'LDAP_CHANGELOG')),
    CONSTRAINT chk_audit_actor_type
        CHECK (actor_type IS NULL OR actor_type IN ('ADMIN', 'SUPERADMIN'))
);

-- Typical access patterns
CREATE INDEX idx_audit_tenant_occurred  ON audit_events (tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_directory        ON audit_events (directory_id, occurred_at DESC);
CREATE INDEX idx_audit_actor            ON audit_events (actor_id, occurred_at DESC);
CREATE INDEX idx_audit_action           ON audit_events (action);
CREATE INDEX idx_audit_target_dn        ON audit_events (target_dn);
