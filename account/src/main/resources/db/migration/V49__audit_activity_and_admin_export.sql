-- Exporting the immutable administration journal is more sensitive than reading it
-- on screen. Existing roles receive the new capability only when they already hold
-- both administration-view and ordinary audit-export permissions.
INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('audit.admin.export', 'تصدير سجل إدارة التدقيق', 'AUDIT', 'audit.admin',
        'EXPORT', 'HIGH', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT DISTINCT admin_view.role_id, admin_export.id, 1
FROM auth_role_permission admin_view
         JOIN auth_permission admin_view_permission
              ON admin_view_permission.id = admin_view.permission_id
             AND admin_view_permission.permission_key = 'audit.admin.view'
         JOIN auth_role_permission ordinary_export
              ON ordinary_export.role_id = admin_view.role_id
         JOIN auth_permission ordinary_export_permission
              ON ordinary_export_permission.id = ordinary_export.permission_id
             AND ordinary_export_permission.permission_key = 'audit.export'
         JOIN auth_permission admin_export
              ON admin_export.permission_key = 'audit.admin.export';

-- The activity overview asks for direct SQL changes in a bounded period.
CREATE INDEX idx_audit_source_time ON audit_log (source, action_time);
