-- V47: Report engine enhancements
--   1. Add MISSING_PROFILE_GROUPS and SOD_VIOLATIONS to report type constraint
--   2. Add timezone and concurrency tracking to scheduled report jobs

-- Fix report type constraint to include all enum values
ALTER TABLE scheduled_report_jobs DROP CONSTRAINT IF EXISTS chk_report_type;
ALTER TABLE scheduled_report_jobs ADD CONSTRAINT chk_report_type CHECK (report_type IN (
    'USERS_IN_GROUP',
    'USERS_IN_BRANCH',
    'USERS_WITH_NO_GROUP',
    'RECENTLY_ADDED',
    'RECENTLY_MODIFIED',
    'RECENTLY_DELETED',
    'DISABLED_ACCOUNTS',
    'MISSING_PROFILE_GROUPS',
    'SOD_VIOLATIONS'
));

-- Timezone for cron evaluation (null = UTC)
ALTER TABLE scheduled_report_jobs ADD COLUMN timezone VARCHAR(50);

-- Run history: last 10 runs stored as JSONB array
ALTER TABLE scheduled_report_jobs ADD COLUMN run_history JSONB DEFAULT '[]';
