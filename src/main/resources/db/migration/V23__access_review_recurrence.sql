-- Add deadline-as-days and recurring schedule support to access review campaigns

ALTER TABLE access_review_campaigns
  ADD COLUMN deadline_days      INT,
  ADD COLUMN recurrence_months  INT,
  ADD COLUMN source_campaign_id UUID;

ALTER TABLE access_review_campaigns
  ADD CONSTRAINT fk_arc_source_campaign
    FOREIGN KEY (source_campaign_id) REFERENCES access_review_campaigns (id)
    ON DELETE SET NULL;
