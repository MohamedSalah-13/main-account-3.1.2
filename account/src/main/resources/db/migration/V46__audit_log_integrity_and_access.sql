-- Preserve who performed an operation at the time it happened. Joining the current
-- users row made an old log entry change when a user was renamed and disappear when a
-- legacy install deleted that user. The workstation snapshot distinguishes tills that
-- share one database.
ALTER TABLE audit_log
    ADD COLUMN actor_name VARCHAR(100) NULL AFTER user_id,
    ADD COLUMN workstation_id VARCHAR(128) NULL AFTER source,
    ADD COLUMN workstation_name VARCHAR(100) NULL AFTER workstation_id;

UPDATE audit_log a
    LEFT JOIN users u ON u.id = a.user_id
SET a.actor_name = u.user_name
WHERE a.actor_name IS NULL;

-- The audit browser always starts with a time range. DATE(action_time) prevented MySQL
-- from using any of the existing composite indexes for that ordinary case.
CREATE INDEX idx_audit_time ON audit_log (action_time);
CREATE INDEX idx_audit_table_time ON audit_log (table_name, action_time);

-- Reading before/after values is sensitive and no longer borrows the broad settings
-- permission. Existing roles keep their previous access on upgrade.
INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('audit.view', 'عرض سجل التدقيق', 'AUDIT', 'audit', 'VIEW', 'HIGH', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT DISTINCT existing.role_id, audit_view.id, 1
FROM auth_role_permission existing
         JOIN auth_permission source_permission
              ON source_permission.id = existing.permission_id
             AND source_permission.permission_key IN ('setting.show', 'audit.delete')
         JOIN auth_permission audit_view
              ON audit_view.permission_key = 'audit.view';
