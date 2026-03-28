-- Continuous access monitoring: alert rules and instances
--
-- alert_rules: one row per enabled check per directory
-- alert_instances: fired alerts with lifecycle (OPEN → ACKNOWLEDGED → RESOLVED/DISMISSED)

CREATE TABLE alert_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id    UUID REFERENCES directory_connections(id) ON DELETE CASCADE,
    rule_type       VARCHAR(80)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT true,
    severity        VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',
    params          JSONB        NOT NULL DEFAULT '{}',
    notify_in_app   BOOLEAN      NOT NULL DEFAULT true,
    notify_email    BOOLEAN      NOT NULL DEFAULT false,
    email_recipients TEXT,
    cooldown_hours  INT          NOT NULL DEFAULT 24,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (directory_id, rule_type)
);

CREATE TABLE alert_instances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id         UUID         NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    directory_id    UUID         REFERENCES directory_connections(id) ON DELETE CASCADE,
    severity        VARCHAR(20)  NOT NULL,
    title           VARCHAR(500) NOT NULL,
    detail          TEXT,
    context_key     VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    acknowledged_by UUID         REFERENCES accounts(id),
    acknowledged_at TIMESTAMPTZ,
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_alert_instances_open   ON alert_instances(status) WHERE status = 'OPEN';
CREATE INDEX idx_alert_instances_rule   ON alert_instances(rule_id, context_key);
CREATE INDEX idx_alert_instances_dir    ON alert_instances(directory_id, created_at DESC);
