-- The price-check screen: a device on the shop wall that answers a scanned barcode with a
-- price, and can do nothing else. It is not 'items.show' - that key opens the item list,
-- where the buying price and the value of the stock are, and this screen stands unattended
-- in front of customers. A shop must be able to grant the wall screen without granting the
-- catalogue.
-- module/resource/action are what AppPermissions.definition() derives from the key itself, so the
-- startup synchronisation rewrites this row to the same values rather than to different ones.
INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('items.price.check', 'شاشة الاستعلام عن الأسعار', 'ITEMS', 'items.price', 'CHECK',
        'LOW', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

-- Nobody gains an ability they did not have: reading an item's selling price is what
-- 'items.show' already allowed, so every role holding it keeps being able to show that same
-- price on the wall. Only role grants are backfilled, which is the choice V35 and V12 made
-- before this one - a user holding 'items.show' through an override alone needs an
-- administrator to grant this key.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT DISTINCT existing.role_id, price_check.id, 1
FROM auth_role_permission existing
         JOIN auth_permission source
              ON source.id = existing.permission_id
             AND source.permission_key = 'items.show'
         JOIN auth_permission price_check
              ON price_check.permission_key = 'items.price.check';
