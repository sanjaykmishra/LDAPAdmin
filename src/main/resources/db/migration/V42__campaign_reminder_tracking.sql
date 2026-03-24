-- V42: Track campaign reminders and escalations to prevent duplicate sends
CREATE TABLE campaign_reminders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id     UUID        NOT NULL REFERENCES access_review_campaigns(id) ON DELETE CASCADE,
    reviewer_account_id UUID    NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    reminder_type   VARCHAR(20) NOT NULL CHECK (reminder_type IN ('DEADLINE', 'ESCALATION')),
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_campaign_reminders_campaign ON campaign_reminders(campaign_id);
CREATE INDEX idx_campaign_reminders_lookup ON campaign_reminders(campaign_id, reviewer_account_id, reminder_type);
