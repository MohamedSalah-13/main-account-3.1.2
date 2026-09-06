-- Taking the database out of the building, and pouring one back in, become abilities of
-- their own.
--
-- 'setting.backup.show' has been in the permission catalogue since it was written and was
-- used by nothing: the sidebar button and the settings tab were both guarded by
-- 'setting.show', so anyone who could open the settings screen could dump every customer,
-- every invoice and every price into one file. 'backup.restore' is new, and it is the more
-- dangerous half - a restore runs DROP TABLE over the live schema.
--
-- Nobody loses what they had: both keys go to every role that already held 'setting.show',
-- which is exactly the set of roles that could reach the screen yesterday. Splitting them
-- is what makes it possible to take the restore away from a shift supervisor tomorrow
-- without taking backups away too.
INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
-- The metadata is what AppPermissions.definition() derives from the key itself, because the
-- startup synchronisation rewrites these columns from that derivation on every launch.
-- Writing anything else here would be a value that lasts until the next start.
VALUES ('setting.backup.show', 'النسخ الاحتياطي', 'SETTING', 'setting.backup', 'SHOW', 'LOW', 0, 1, 1),
       ('backup.restore', 'استعادة نسخة احتياطية', 'BACKUP', 'backup', 'RESTORE', 'CRITICAL', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT DISTINCT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission source
              ON source.id = existing.permission_id
             AND source.permission_key = 'setting.show'
         JOIN auth_permission granted
              ON granted.permission_key IN ('setting.backup.show', 'backup.restore');
