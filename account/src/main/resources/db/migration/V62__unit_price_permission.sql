-- صلاحية تعديل أسعار الوحدات: شاشة "أسعار الوحدات" التي تعرض كل صنف وتحته وحداته.
--
-- كان تعديل سعر وحدة ممكنا من شاشة الصنف وحدها، بصلاحية items.update التي تعدّل كل شيء في
-- الصنف. والشاشة الجديدة تعدّل مئة صنف في حفظة واحدة، وزرّها "اجعل الأسعار تلقائية" يمسح أسعار
-- وحدات كثيرة دفعة واحدة - فهي صلاحية مستقلة يمكن منحها لمن يراجع الأسعار دون أن يملك تعديل
-- اسم الصنف أو باركوده أو رصيده الافتتاحي.
--
-- وتُمنح لكل دور يملك items.update اليوم، على نمط V34 و V55: من كان يستطيع تعديل سعر وحدة من
-- شاشة الصنف لا يفقد ذلك عند الترقية، والإدارة تسحبها بعد ذلك ممن لا يجب أن يملكها.
--
-- والمفتاح هو ما يشتقّه AppPermissions.definition من النص نفسه: الوحدة ITEMS، والمورد
-- items.unit.price، والفعل UPDATE بخطورة HIGH. المزامنة عند بدء التشغيل تكتب نفس القيم، والصف
-- هنا موجود قبلها فقط لكي يجد المنح ما يربطه به.

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('items.unit.price.update', 'تعديل أسعار وحدات الأصناف وجعلها تلقائية', 'ITEMS', 'items.unit.price',
        'UPDATE', 'HIGH', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, unit_price_permission.id, 1
FROM auth_role_permission existing
         JOIN auth_permission edit_permission
              ON edit_permission.id = existing.permission_id
             AND edit_permission.permission_key = 'items.update'
         JOIN auth_permission unit_price_permission
              ON unit_price_permission.permission_key = 'items.unit.price.update';
