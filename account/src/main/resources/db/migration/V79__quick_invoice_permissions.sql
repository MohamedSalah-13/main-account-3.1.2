-- =====================================================================
-- V79 - مفتاحان للفاتورة السريعة: `sales.quick` و`purchase.quick`.
--
-- الفاتورة السريعة صارت شاشة مستقلة (QuickInvoiceController)، وكانت قبلها وضعًا داخل
-- شاشة الفاتورة العادية يفتحه أي من يفتح الفاتورة - بلا مفتاح. المفتاح الجديد لا يحرس
-- الكتابة: الحفظ ما زال يطلب `sales.create`/`purchase.create` داخل InvoiceSaveService.
-- هو اختيار شاشة يعطيه صاحب المحل للكاشير أو يمنعه عنه.
--
-- يُمنح لكل دور يملك مفتاح الإنشاء اليوم، ولكل مستخدم مُنح الإنشاء استثناءً (ALLOW)،
-- فلا أحد يفقد الشاشة التي كان يعمل عليها عند الترقية. ومن الغد يجوز سحبه وحده.
--
-- الوصفان تحت 50 حرفًا: `auth_permission.description` هو `VARCHAR(50)` وV65 سقطت عليه.
-- ولا إجراء مساعد هنا، فلا شيء يُحذف في آخر الملف (`MigrationHelperProcedureTest`).
-- =====================================================================

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('sales.quick', 'استعمال الفاتورة السريعة للمبيعات', 'SALES', 'sales',
        'QUICK', 'LOW', 0, 1, 1),
       ('purchase.quick', 'استعمال الفاتورة السريعة للمشتريات', 'PURCHASES', 'purchase',
        'QUICK', 'LOW', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

-- Whoever may create a sale could open the quick screen yesterday; this only says so.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, quick.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id
             AND held.permission_key = 'sales.create'
         JOIN auth_permission quick
              ON quick.permission_key = 'sales.quick';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, quick.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id
             AND held.permission_key = 'purchase.create'
         JOIN auth_permission quick
              ON quick.permission_key = 'purchase.quick';

-- A user given create outside their roles had the screen too, for as long as that grant runs. A DENY is not copied: a user
-- refused create could not save from either screen, and a refusal is not an ability to keep.
INSERT IGNORE INTO auth_user_permission_override(user_id, permission_id, effect, reason, expires_at, granted_by)
SELECT existing.user_id, quick.id, 'ALLOW', 'V79: had sales.create', existing.expires_at, 1
FROM auth_user_permission_override existing
         JOIN auth_permission held
              ON held.id = existing.permission_id
             AND held.permission_key = 'sales.create'
         JOIN auth_permission quick
              ON quick.permission_key = 'sales.quick'
WHERE existing.effect = 'ALLOW';

INSERT IGNORE INTO auth_user_permission_override(user_id, permission_id, effect, reason, expires_at, granted_by)
SELECT existing.user_id, quick.id, 'ALLOW', 'V79: had purchase.create', existing.expires_at, 1
FROM auth_user_permission_override existing
         JOIN auth_permission held
              ON held.id = existing.permission_id
             AND held.permission_key = 'purchase.create'
         JOIN auth_permission quick
              ON quick.permission_key = 'purchase.quick'
WHERE existing.effect = 'ALLOW';
