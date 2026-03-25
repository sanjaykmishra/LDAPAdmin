-- V49: Access drift / privilege creep detection

-- Periodic snapshots of user-to-group memberships
CREATE TABLE access_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id    UUID        NOT NULL REFERENCES directory_connections(id) ON DELETE CASCADE,
    captured_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS'
                    CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    total_users     INTEGER,
    total_groups    INTEGER,
    error_message   TEXT,
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_as_directory ON access_snapshots (directory_id, captured_at DESC);

-- Individual membership records within a snapshot
CREATE TABLE access_snapshot_memberships (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id     UUID        NOT NULL REFERENCES access_snapshots(id) ON DELETE CASCADE,
    user_dn         VARCHAR(1024) NOT NULL,
    group_dn        VARCHAR(1024) NOT NULL,
    group_name      VARCHAR(255)
);

CREATE INDEX idx_asm_snapshot ON access_snapshot_memberships (snapshot_id);
CREATE INDEX idx_asm_user ON access_snapshot_memberships (snapshot_id, user_dn);

-- Peer group rules: how to bucket users for comparison
CREATE TABLE peer_group_rules (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id            UUID        NOT NULL REFERENCES directory_connections(id) ON DELETE CASCADE,
    name                    VARCHAR(255) NOT NULL,
    grouping_attribute      VARCHAR(100) NOT NULL,  -- e.g. "department", "title", "ou"
    normal_threshold_pct    INTEGER     NOT NULL DEFAULT 50,
    anomaly_threshold_pct   INTEGER     NOT NULL DEFAULT 10,
    enabled                 BOOLEAN     NOT NULL DEFAULT TRUE,
    created_by              UUID        REFERENCES accounts(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pgr_directory ON peer_group_rules (directory_id);

-- Detected drift findings
CREATE TABLE access_drift_findings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id         UUID        NOT NULL REFERENCES access_snapshots(id) ON DELETE CASCADE,
    rule_id             UUID        NOT NULL REFERENCES peer_group_rules(id) ON DELETE CASCADE,
    user_dn             VARCHAR(1024) NOT NULL,
    user_display        VARCHAR(255),
    peer_group_value    VARCHAR(255) NOT NULL,
    peer_group_size     INTEGER     NOT NULL,
    group_dn            VARCHAR(1024) NOT NULL,
    group_name          VARCHAR(255),
    peer_membership_pct DOUBLE PRECISION NOT NULL,
    severity            VARCHAR(10) NOT NULL CHECK (severity IN ('HIGH', 'MEDIUM', 'LOW')),
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED', 'EXEMPTED')),
    acknowledged_by     UUID        REFERENCES accounts(id),
    acknowledged_at     TIMESTAMPTZ,
    exemption_reason    TEXT,
    detected_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_adf_snapshot ON access_drift_findings (snapshot_id);
CREATE INDEX idx_adf_status ON access_drift_findings (status);
CREATE INDEX idx_adf_directory ON access_drift_findings (snapshot_id, status);
