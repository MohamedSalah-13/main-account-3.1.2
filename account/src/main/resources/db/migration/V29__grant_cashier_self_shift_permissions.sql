-- Cashiers own their operational shift.  Seed the catalogue here as well as
-- granting the role because Flyway runs before the startup catalogue sync on a
-- fresh installation.
INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('shift.self.view', 'shift.self.view', 'SHIFT', 'shift.self', 'VIEW', 'LOW', 0, 1, 1),
       ('shift.self.open', 'shift.self.open', 'SHIFT', 'shift.self', 'OPEN', 'LOW', 0, 1, 1),
       ('shift.self.close', 'shift.self.close', 'SHIFT', 'shift.self', 'CLOSE', 'LOW', 0, 1, 1),
       ('shift.xreport.view', 'shift.xreport.view', 'SHIFT', 'shift.xreport', 'VIEW', 'LOW', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT r.id, p.id, 1
FROM auth_role r
JOIN auth_permission p ON p.permission_key IN (
    'shift.self.view', 'shift.self.open', 'shift.self.close', 'shift.xreport.view'
)
WHERE r.role_code = 'DEFAULT_SALES_CASHIER';
