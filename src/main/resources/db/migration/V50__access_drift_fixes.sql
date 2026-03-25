-- V50: Access drift enhancements — snapshot user attributes for peer grouping

-- Store user attributes captured at snapshot time for peer grouping
-- This avoids querying live LDAP during analysis (which could be stale vs snapshot)
CREATE TABLE access_snapshot_users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id     UUID        NOT NULL REFERENCES access_snapshots(id) ON DELETE CASCADE,
    user_dn         VARCHAR(1024) NOT NULL,
    display_name    VARCHAR(255),
    department      VARCHAR(255),
    title           VARCHAR(255),
    ou              VARCHAR(255)
);

CREATE INDEX idx_asu_snapshot ON access_snapshot_users (snapshot_id);
CREATE UNIQUE INDEX idx_asu_snapshot_user ON access_snapshot_users (snapshot_id, user_dn);
