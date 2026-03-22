-- Rename campaign status DRAFT -> UPCOMING

UPDATE access_review_campaigns SET status = 'UPCOMING' WHERE status = 'DRAFT';

ALTER TABLE access_review_campaigns DROP CONSTRAINT chk_arc_status;
ALTER TABLE access_review_campaigns ADD CONSTRAINT chk_arc_status
    CHECK (status IN ('UPCOMING', 'ACTIVE', 'CLOSED', 'CANCELLED', 'EXPIRED'));
