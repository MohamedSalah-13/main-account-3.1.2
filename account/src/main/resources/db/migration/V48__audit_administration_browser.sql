-- Reading audit-administration evidence is separate from ordinary audit access.
-- Existing retention administrators keep access after upgrade; other roles may be
-- granted it independently from deletion or cleanup rights.
INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('audit.admin.view', 'عرض سجل إدارة التدقيق', 'AUDIT', 'audit.admin', 'VIEW', 'HIGH', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT DISTINCT existing.role_id, admin_view.id, 1
FROM auth_role_permission existing
         JOIN auth_permission source_permission
              ON source_permission.id = existing.permission_id
             AND source_permission.permission_key IN ('audit.retention.manage', 'audit.delete')
         JOIN auth_permission admin_view
              ON admin_view.permission_key = 'audit.admin.view';

-- The screen is time-bounded first; these cover its common secondary filters.
CREATE INDEX idx_audit_admin_source_time ON audit_admin_event (source, occurred_at);
CREATE INDEX idx_audit_admin_actor_time ON audit_admin_event (actor_user_id, occurred_at);
