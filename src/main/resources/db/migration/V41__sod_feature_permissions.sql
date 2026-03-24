-- V41: Add SoD feature permission keys

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
            'access_review.manage', 'access_review.review',
            'playbook.manage', 'playbook.execute',
            'approval.manage',
            'csv_template.manage',
            'directory.browse',
            'schema.read',
            'user.read', 'group.read',
            'sod.manage', 'sod.view'
        )
    );
