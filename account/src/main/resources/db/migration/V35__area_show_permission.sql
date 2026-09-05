-- An area belongs to a customer or a supplier, not to an item, but the areas section had no
-- view permission of its own: it borrowed 'items.show', which is what the button that used to
-- open it was guarded by. Reading the areas list is now its own key.
INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('area.show', 'عرض المناطق', 'AREA', 'area', 'SHOW', 'LOW', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

-- Nobody loses the section on upgrade: every role that could already see it keeps it, and a role
-- that may write an area but never held 'items.show' can now see what it writes. Only role grants
-- are backfilled - a user carrying 'items.show' through auth_user_permission_override alone needs
-- an administrator to grant this one, which is the same choice V12 made when it split create from
-- view.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT DISTINCT existing.role_id, show_permission.id, 1
FROM auth_role_permission existing
         JOIN auth_permission source
              ON source.id = existing.permission_id
             AND source.permission_key IN ('items.show', 'area.create', 'area.update', 'area.delete')
         JOIN auth_permission show_permission
              ON show_permission.permission_key = 'area.show';
