-- Feature permissions for HR integration
INSERT INTO feature_permission_defaults (feature_key, role, enabled)
VALUES
    ('hr.manage', 'SUPERADMIN', true),
    ('hr.manage', 'ADMIN', false),
    ('hr.view', 'SUPERADMIN', true),
    ('hr.view', 'ADMIN', true);
