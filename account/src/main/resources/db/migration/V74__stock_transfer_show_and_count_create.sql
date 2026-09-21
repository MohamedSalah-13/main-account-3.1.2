-- =====================================================================
-- V74 - صلاحيتان كانت المخازن تستعير غيرهما. المرحلة ج من docs/warehouse-plan.md §10.
--
-- ### `stock.transfer.show`
--
-- سجلّ التحويلات وتقريرها كانا يطلبان `stock.transfer.post`: من يريد أن **يرى** ما حُوِّل
-- كان عليه أن يملك حق **إجرائه**. وهو نفس ما أصلحته V35 حين أعطت شاشة المناطق `area.show`
-- بدل استعارة `items.show`. تُمنح لكل من يملك `stock.transfer.post` اليوم، فلا أحد يفقد
-- قدرة عند الترقية - ومن الغد يجوز لصاحب المحل أن يعطي أمين المخزن العرض دون الترحيل.
--
-- ### `stock.count.create`
--
-- `StockCountService.save` و`deleteDraft` كانا يطلبان صلاحيتين لا تصفان ما يفعلانه: الأولى
-- تطلب `stock.count.show` - أي أن كل من يفتح الشاشة يكتب فيها - والثانية تطلب
-- `stock.count.post`، فحذف **مسودة** كان يحتاج حق ترحيل الجرد. المسودة لا تحرّك رصيدًا
-- (`adjustment_agg` لا يقرأ إلا `POSTED`)، ومن يُدخلها هو من يتخلّص منها.
--
-- تُمنح لمن يملك `stock.count.show` **أو** `stock.count.post`: الأولى تغطي من كان يحفظ،
-- والثانية تغطي دورًا مخصَّصًا يملك الترحيل دون العرض - وهو وضع غريب لكنه ممكن، ولا أحد
-- يفقد قدرة بسبب غرابة وضعه.
--
-- الوصفان تحت 50 حرفًا: `auth_permission.description` هو `VARCHAR(50)` وV65 سقطت عليه.
-- ولا إجراء مساعد هنا، فلا شيء يُحذف في آخر الملف (`MigrationHelperProcedureTest`).
-- =====================================================================

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('stock.transfer.show', 'عرض سجل التحويلات بين المخازن', 'STOCK', 'stock.transfer',
        'SHOW', 'LOW', 0, 1, 1),
       ('stock.count.create', 'إدخال ورقة جرد وحفظها كمسودة', 'STOCK', 'stock.count',
        'CREATE', 'HIGH', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

-- Whoever may post a transfer may already see the history; this only says so.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, new_permission.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id
             AND held.permission_key = 'stock.transfer.post'
         JOIN auth_permission new_permission
              ON new_permission.permission_key = 'stock.transfer.show';

-- Whoever could open a count sheet could already save one, and whoever could post one
-- could already delete a draft. Both are covered so neither loses what it had.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT DISTINCT existing.role_id, new_permission.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id
             AND held.permission_key IN ('stock.count.show', 'stock.count.post')
         JOIN auth_permission new_permission
              ON new_permission.permission_key = 'stock.count.create';
