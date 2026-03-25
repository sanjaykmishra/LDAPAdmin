-- V48: Add unique constraint on campaign template name per directory
CREATE UNIQUE INDEX idx_ct_directory_name ON campaign_templates (directory_id, name);
