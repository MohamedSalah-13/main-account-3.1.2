-- =====================================================================
-- V65 - صلاحية تقارير المصروفات (المرحلة ب من docs/expenses-plan.md §4).
--
-- لا جدول ولا عمود: التقارير تقرأ expenses_details نفسه بنفس WHERE القائمة. ما تضيفه الهجرة
-- مفتاح واحد، ويُمنح لمن يرى القائمة اليوم - على نمط V34 وV55 وV58 وV64: لا أحد يفقد قدرة عند
-- الترقية، ومن لا يُراد له أن يرى أين تذهب أموال المحل تُسحب منه بعدها.
--
-- الوصف خمسون حرفا على الأكثر: auth_permission.description هو VARCHAR(50) من V1، والمسودة الأولى
-- لهذا الملف كانت 62 حرفا وفشلت على أول قاعدة رُحِّلت إليها.
-- =====================================================================

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('expenses.reports', 'تقارير المصروفات بالبند والشهور والاتجاه',
        'EXPENSES', 'expenses', 'REPORTS', 'LOW', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key        = VALUES(module_key),
                        resource_key      = VALUES(resource_key),
                        action_key        = VALUES(action_key),
                        risk_level        = VALUES(risk_level),
                        system_permission = 1,
                        enabled           = 1;

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'expenses.show'
         JOIN auth_permission granted ON granted.permission_key = 'expenses.reports';
