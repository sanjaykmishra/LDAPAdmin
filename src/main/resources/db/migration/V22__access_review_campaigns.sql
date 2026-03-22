-- Access review / recertification campaigns

CREATE TABLE access_review_campaigns (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    directory_id    UUID         NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    starts_at       TIMESTAMPTZ,
    deadline        TIMESTAMPTZ  NOT NULL,
    auto_revoke     BOOLEAN      NOT NULL DEFAULT FALSE,
    auto_revoke_on_expiry BOOLEAN NOT NULL DEFAULT FALSE,
    created_by      UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    CONSTRAINT pk_access_review_campaigns PRIMARY KEY (id),
    CONSTRAINT fk_arc_directory FOREIGN KEY (directory_id) REFERENCES directory_connections (id) ON DELETE CASCADE,
    CONSTRAINT fk_arc_creator   FOREIGN KEY (created_by)   REFERENCES accounts (id),
    CONSTRAINT chk_arc_status   CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED', 'CANCELLED', 'EXPIRED'))
);

CREATE INDEX idx_arc_directory_status ON access_review_campaigns (directory_id, status);

CREATE TABLE access_review_groups (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    campaign_id     UUID         NOT NULL,
    group_dn        VARCHAR(1000) NOT NULL,
    group_name      VARCHAR(500),
    member_attribute VARCHAR(50) NOT NULL DEFAULT 'member',
    reviewer_id     UUID         NOT NULL,
    CONSTRAINT pk_access_review_groups  PRIMARY KEY (id),
    CONSTRAINT uq_arg_campaign_group    UNIQUE (campaign_id, group_dn),
    CONSTRAINT fk_arg_campaign FOREIGN KEY (campaign_id) REFERENCES access_review_campaigns (id) ON DELETE CASCADE,
    CONSTRAINT fk_arg_reviewer FOREIGN KEY (reviewer_id) REFERENCES accounts (id)
);

CREATE TABLE access_review_decisions (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    review_group_id UUID         NOT NULL,
    member_dn       VARCHAR(1000) NOT NULL,
    member_display  VARCHAR(500),
    decision        VARCHAR(20),
    comment         TEXT,
    decided_by      UUID,
    decided_at      TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    CONSTRAINT pk_access_review_decisions PRIMARY KEY (id),
    CONSTRAINT uq_ard_group_member       UNIQUE (review_group_id, member_dn),
    CONSTRAINT fk_ard_review_group FOREIGN KEY (review_group_id) REFERENCES access_review_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_ard_decided_by   FOREIGN KEY (decided_by)      REFERENCES accounts (id),
    CONSTRAINT chk_ard_decision    CHECK (decision IS NULL OR decision IN ('CONFIRM', 'REVOKE'))
);

CREATE INDEX idx_ard_review_group ON access_review_decisions (review_group_id);
CREATE INDEX idx_ard_undecided    ON access_review_decisions (review_group_id) WHERE decision IS NULL;

-- Campaign status transition history (audit trail for campaign lifecycle)
CREATE TABLE access_review_campaign_history (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    campaign_id     UUID         NOT NULL,
    old_status      VARCHAR(20),
    new_status      VARCHAR(20)  NOT NULL,
    changed_by      UUID         NOT NULL,
    changed_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    note            TEXT,
    CONSTRAINT pk_arch PRIMARY KEY (id),
    CONSTRAINT fk_arch_campaign   FOREIGN KEY (campaign_id) REFERENCES access_review_campaigns (id) ON DELETE CASCADE,
    CONSTRAINT fk_arch_changed_by FOREIGN KEY (changed_by)  REFERENCES accounts (id)
);

CREATE INDEX idx_arch_campaign ON access_review_campaign_history (campaign_id, changed_at);

-- Add feature keys to constraint
ALTER TABLE admin_feature_permissions
    DROP CONSTRAINT IF EXISTS chk_afp_feature_key;

ALTER TABLE admin_feature_permissions
    ADD CONSTRAINT chk_afp_feature_key CHECK (
        feature_key IN (
            'user.create', 'user.edit', 'user.delete',
            'user.enable_disable', 'user.move', 'user.reset_password',
            'group.edit', 'group.manage_members', 'group.create_delete',
            'bulk.import', 'bulk.export', 'bulk.attribute_update',
            'reports.run', 'reports.export', 'reports.schedule',
            'access_review.manage', 'access_review.review'
        )
    );
