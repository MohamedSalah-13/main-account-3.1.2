-- =====================================================================
-- V73 - سقف خصم المندوب. المرحلة د-2 من docs/delegates-plan.md (ق-6).
--
-- ### ما هو
--
-- `employees.max_discount_percent`: أقصى خصم، كنسبة من إجمالي الفاتورة قبل أي خصم، يجوز لفاتورة
-- بيع تحمل هذا المندوب. **NULL = بلا سقف**، وهي حال كل موظف يوم الترقية - فلا فاتورة تُرفض غدًا
-- كانت تُحفظ اليوم. والصفر سقف حقيقي: مندوب لا يخصم شيئًا. الفرق بين الفارغ والصفر هو نفسه
-- الفرق في حقل فلتر (`Utils.setOptionalNumberFormatter`) وفي شرائح العمولة.
--
-- ### على ماذا يُقاس
--
-- على **خصم السطور + خصم الفاتورة معًا**، مقابل إجمالي السطور قبل الخصم. سقف يقيس خصم رأس
-- الفاتورة وحده يُلتفّ عليه من عمود الخصم في السطر. والمقارنة بالمبالغ لا بالنسبة المقرَّبة
-- (`DiscountCeiling`) - درس `CommissionTiers`: 10.004% تُعرض 10.00% وليست ضمن سقف 10%.
--
-- ### الصلاحية
--
-- `sales.discount.override`: حفظ فاتورة يتجاوز خصمها سقف مندوبها. تُمنح لمن يملك
-- `commission.rule.update` - من يضع السقف يجوز له تجاوزه - **لا لمن يملك `sales.create`**:
-- منحها لكل من يبيع يجعل السقف الذي يضعه صاحب المحل بلا أثر على أحد، وهو عطل أصمت من الرفض.
-- ولا أحد يفقد قدرة عند الترقية، لأن لا سقف موجود حتى يضعه أحد بيده.
-- الوصف تحت 50 حرفًا (درس V65).
--
-- الإجراء المساعد معرَّف هنا ومحذوف في آخر الملف (`MigrationHelperProcedureTest`).
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_ceiling_column_if_missing$$
CREATE PROCEDURE add_ceiling_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64),
                                               IN col_def TEXT)
BEGIN
    DECLARE col_exists INT;

    SELECT COUNT(*)
    INTO col_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = t_name
      AND COLUMN_NAME = c_name;

    IF col_exists = 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' ADD COLUMN ', c_name, ' ', col_def);
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_ceiling_column_if_missing('employees', 'max_discount_percent',
    'DECIMAL(5,2) NULL COMMENT ''سقف خصم فاتورة البيع لهذا المندوب، نسبة من الإجمالي قبل الخصم. NULL = بلا سقف'' CHECK (max_discount_percent IS NULL OR (max_discount_percent >= 0 AND max_discount_percent <= 100))');

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('sales.discount.override', 'حفظ فاتورة بيع يتجاوز خصمها سقف المندوب', 'SALES', 'sales.discount',
        'OVERRIDE', 'HIGH', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, new_permission.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id
             AND held.permission_key = 'commission.rule.update'
         JOIN auth_permission new_permission
              ON new_permission.permission_key = 'sales.discount.override';

DROP PROCEDURE IF EXISTS add_ceiling_column_if_missing;
