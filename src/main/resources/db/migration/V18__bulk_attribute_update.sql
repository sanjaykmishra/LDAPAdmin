-- Add bulk.attribute_update feature key to the CHECK constraint
-- Also adds previously missing user.reset_password and group.edit keys

ALTER TABLE admin_feature_permissions
    DROP CONSTRAINT chk_feature_key;

ALTER TABLE admin_feature_permissions
    ADD CONSTRAINT chk_feature_key CHECK (feature_key IN (
        'user.create',
        'user.edit',
        'user.delete',
        'user.enable_disable',
        'user.move',
        'user.reset_password',
        'group.edit',
        'group.manage_members',
        'group.create_delete',
        'bulk.import',
        'bulk.export',
        'bulk.attribute_update',
        'reports.run',
        'reports.export',
        'reports.schedule'
    ));
