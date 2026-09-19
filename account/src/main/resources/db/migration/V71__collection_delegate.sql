-- =====================================================================
-- V71 - مندوب التحصيل، وصلاحية تقارير المناديب. المرحلة ب من docs/delegates-plan.md.
--
-- ### لماذا عمود
--
-- الفاتورة تحمل مندوبها (`total_sales.delegate_id`، من V1). التحصيل لا يحمل شيئًا، فعمولة
-- «على المحصَّل» بلا ما تُحسب عليه. والقاعدة (ق-3 في الخطة): تحصيل مخصَّص لفاتورة يُنسب
-- لمندوب تلك الفاتورة، وتحصيل على الحساب يُنسب للمندوب الافتراضي للعميل **يوم التحصيل**.
--
-- و«يوم التحصيل» هو سبب العمود: لو اشتُقّ المندوب عند القراءة من `custom.default_delegate_id`
-- لكان نقل عميل من مندوب إلى آخر يعيد كتابة تاريخ الاثنين - تحصيلات يناير تنتقل إلى مندوب
-- لم يكن يعرف العميل في يناير. فالمندوب **يُكتب عند الإدخال ولا يُشتق عند القراءة**.
--
-- ### الصفوف القديمة تبقى NULL
--
-- لا ترحيل. NULL تعني «بلا مندوب» وتظهر في التقرير كرقم مستقل. تخمين مندوب لتحصيل قديم من
-- افتراضي العميل **اليوم** هو بالضبط الاشتقاق الذي يمنعه العمود، بأثر رجعي - ودرس V67:
-- ربطٌ بالتخمين أسوأ من عدم الربط، لأنه يبدو صحيحًا.
--
-- ### قيد أجنبي هنا، بخلاف `custom.default_delegate_id`
--
-- V56 ترك افتراضي العميل بلا قيد عمدًا: تفضيل على شاشة عميل لا يصح أن يمنع حذف مندوب. هذا
-- العمود ليس تفضيلًا بل **واقعة** - مال حصّله شخص - وهو أخو `total_sales.delegate_id` الذي
-- يحمل القيد نفسه منذ V1. مندوب حصّل مالًا لا يُحذف، ويُوقَف بـ`is_active` كأي موظف له تاريخ.
-- القيد غير جارف، فهو مُعلَن في `DeleteRegistry.EMPLOYEES`.
--
-- ### الإجراءات المساعدة
--
-- معرَّفة هنا ومحذوفة في آخر الملف (`MigrationHelperProcedureTest`). واسم `add_constraint_if_missing`
-- **مقصود بحرفه**: `SchemaForeignKeys` يقرأ المفاتيح المضافة إلى جدول قائم من نداء بهذا الاسم
-- بالذات، وبه يعرف `DeleteRegistryTest` أن المفتاح موجود. اسم آخر يُخفي المفتاح عن الاختبارين.
-- `add_index_if_missing`
-- يحذفه V1 في سطره 994، و`add_column_if_missing` لا تنشئه هجرة أساسية إطلاقًا.
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_delegate_column_if_missing$$
CREATE PROCEDURE add_delegate_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64),
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

DROP PROCEDURE IF EXISTS add_delegate_index$$
CREATE PROCEDURE add_delegate_index(IN t_name VARCHAR(64), IN i_name VARCHAR(64),
                                    IN i_columns TEXT)
BEGIN
    DECLARE index_exists INT;

    SELECT COUNT(*)
    INTO index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = t_name
      AND INDEX_NAME = i_name;

    IF index_exists = 0 THEN
        SET @query = CONCAT('CREATE INDEX ', i_name, ' ON ', t_name, ' (', i_columns, ')');
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS add_constraint_if_missing$$
CREATE PROCEDURE add_constraint_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64),
                                                    IN c_definition TEXT)
BEGIN
    DECLARE constraint_exists INT;

    SELECT COUNT(*)
    INTO constraint_exists
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = t_name
      AND CONSTRAINT_NAME = c_name;

    IF constraint_exists = 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' ADD CONSTRAINT ', c_name, ' ', c_definition);
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- 1) مندوب التحصيل
-- ---------------------------------------------------------------------

CALL add_delegate_column_if_missing('customers_accounts', 'delegate_id',
    'INT NULL COMMENT ''مندوب التحصيل، يُكتب عند الإدخال ولا يُشتق. NULL = بلا مندوب، ومنه كل ما سبق V71''');

-- المفتاح يبني فهرسًا على العمود إن لم يجد واحدًا؛ الفهرس المركّب أدناه يكفيه ويخدم استعلام
-- الشهر، فيُنشأ قبله حتى لا يبقى فهرسان يبدآن بالعمود نفسه.
CALL add_delegate_index('customers_accounts', 'customers_accounts_delegate_date_idx',
    'delegate_id, account_date');

CALL add_constraint_if_missing('customers_accounts', 'customers_accounts_delegate_fk',
    'FOREIGN KEY (delegate_id) REFERENCES employees (id)');

-- استعلام الشهر يجمع فواتير كل مندوب ومرتجعاته في فترة. الفهرسان القائمان على `delegate_id`
-- وحده، فيُقرأ تاريخ كل فاتورة للمندوب ثم تُصفّى؛ المركّب يجعل الفترة جزءًا من البحث.
CALL add_delegate_index('total_sales', 'total_sales_delegate_date_idx', 'delegate_id, invoice_date');
CALL add_delegate_index('total_sales_re', 'total_sales_re_delegate_date_idx', 'delegate_id, invoice_date');

-- ---------------------------------------------------------------------
-- 2) صلاحية التقارير
-- ---------------------------------------------------------------------
-- `commission.reports`: تقرير أداء المناديب - مبيعاتهم ومحصَّلهم. **لا يكشف نسبة ولا عمولة**:
-- عمودا التارجت والعمولة المتوقعة لا يُجلبان إلا لمن يملك `commission.show` فوقها. تُمنح لمن
-- يملك `commission.show` اليوم (V70)، فلا أحد ممن يرى القواعد يُحرم من التقرير عند الترقية.
-- الوصف تحت 50 حرفًا (درس V65).

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('commission.reports', 'تقارير أداء المناديب: المبيعات والمحصَّل', 'COMMISSION', 'commission',
        'REPORTS', 'LOW', 0, 1, 1)
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
             AND held.permission_key = 'commission.show'
         JOIN auth_permission new_permission
              ON new_permission.permission_key = 'commission.reports';

DROP PROCEDURE IF EXISTS add_delegate_column_if_missing;
DROP PROCEDURE IF EXISTS add_delegate_index;
DROP PROCEDURE IF EXISTS add_constraint_if_missing;
