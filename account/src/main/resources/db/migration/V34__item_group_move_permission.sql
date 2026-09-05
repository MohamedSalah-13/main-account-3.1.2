-- Group reassignment is narrower than editing every field of an item. Existing roles that
-- could edit items keep the ability after the split; administrators may revoke it later.
INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('items.group.move', 'نقل الأصناف بين المجموعات', 'ITEMS', 'items.group',
        'MOVE', 'HIGH', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, move_permission.id, 1
FROM auth_role_permission existing
         JOIN auth_permission edit_permission
              ON edit_permission.id = existing.permission_id
             AND edit_permission.permission_key = 'items.update'
         JOIN auth_permission move_permission
              ON move_permission.permission_key = 'items.group.move';
