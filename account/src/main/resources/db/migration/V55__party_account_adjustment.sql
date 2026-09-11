-- حركة يدوية على حساب الطرف: إشعار مدين أو دائن.
--
-- ما كان ناقصا: جدولا customers_accounts و suppliers_accounts يحملان عمود purchase من V1،
-- والـ views تقرؤه، و**لا كاتب له في Java** - قائمة الإدراج في PartyLedgerSpec لا تذكره. فكل
-- ما يمكن تسجيله على حساب طرف هو تحصيل (دائن).
--
-- وأثر ذلك ليس تفصيليا. OpeningBalanceGuard يمنع تعديل first_balance بعد أول حركة - وهو قرار
-- صحيح: الرصيد الافتتاحي هو الرقم الوحيد بلا تاريخ، فتغييره يغيّر ما كان الطرف مدينا به في
-- كل لحظة من تاريخه، وكشف حساب طُبع ووُقّع الشهر الماضي يُطبع اليوم مختلفا. والرسالة التي
-- يعطيها المنع (opening.correction.customers) تقول: «لتصحيح الرصيد سجّل حركة على حساب
-- العميل». فإن كان الرصيد الافتتاحي أُدخل **ناقصا** - أي الطرف مدين بأكثر مما سُجِّل - لم يكن
-- في النظام كله أي وسيلة لزيادته، والرسالة توجّه إلى طريق غير موجود.
--
-- فالصلاحية هنا هي ما يفتح ذلك الطريق، وهي **منفصلة عن صلاحية التحصيل عمدا**: تحصيل مبلغ من
-- عميل حدث مادي يقابله نقد في الخزينة، أما إشعار مدين فهو تعديل لرصيد بقرار إداري ولا يقابله
-- شيء - ومن يقوم على الخزينة ليس بالضرورة من يملك أن يقرر أن عميلا مدين بألف إضافية.
--
-- وتُمنح لمن يملك بالفعل إنشاء حركة على الحساب، على نمط V34: لا أحد يفقد قدرة كانت له عند
-- الترقية، والإدارة تسحبها بعد ذلك ممن لا يجب أن يملكها.

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('customer.account.adjust', 'تسجيل إشعار مدين أو دائن على حساب عميل',
        'CUSTOMERS', 'customer.account', 'ADJUST', 'HIGH', 0, 1, 1),
       ('suppliers.account.adjust', 'تسجيل إشعار مدين أو دائن على حساب مورد',
        'SUPPLIERS', 'suppliers.account', 'ADJUST', 'HIGH', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, adjust_permission.id, 1
FROM auth_role_permission existing
         JOIN auth_permission create_permission
              ON create_permission.id = existing.permission_id
             AND create_permission.permission_key = 'customer.account.create'
         JOIN auth_permission adjust_permission
              ON adjust_permission.permission_key = 'customer.account.adjust';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, adjust_permission.id, 1
FROM auth_role_permission existing
         JOIN auth_permission create_permission
              ON create_permission.id = existing.permission_id
             AND create_permission.permission_key = 'suppliers.account.create'
         JOIN auth_permission adjust_permission
              ON adjust_permission.permission_key = 'suppliers.account.adjust';

-- وثّق العمودين بما يعنيانه، على نمط تعليق treasury.amount في V20: عمود اسمه purchase على
-- جدول اسمه customers_accounts لا يقول لقارئه أنه الطرف المدين من الحركة.
ALTER TABLE customers_accounts
    MODIFY COLUMN purchase DECIMAL(14, 2) DEFAULT 0 NOT NULL
        COMMENT 'الطرف المدين من الحركة: إشعار مدين يزيد ما على العميل. paid هو الطرف الدائن',
    MODIFY COLUMN numberInv BIGINT NOT NULL
        COMMENT 'رقم الفاتورة التي خُصِّصت لها الدفعة، أو 0 لدفعة على الحساب عموما';

ALTER TABLE suppliers_accounts
    MODIFY COLUMN purchase DECIMAL(14, 2) DEFAULT 0 NOT NULL
        COMMENT 'الطرف المدين من الحركة: إشعار مدين يزيد ما لنا عند المورد. paid هو الطرف الدائن',
    MODIFY COLUMN numberInv BIGINT NOT NULL
        COMMENT 'رقم الفاتورة التي خُصِّصت لها الدفعة، أو 0 لدفعة على الحساب عموما';

-- فهرس مركّب لكشف الحساب: الفلترة على (الطرف، التاريخ) معا، والمفتاح الأجنبي يغطي العمود
-- الأول وحده. PartyStatementQuery يقرأ الجدولين من خلال الـ view بهذين العمودين في كل استعلام.
--
-- والإجراء معرَّف هنا محليا ويُحذف بعد الاستعمال، على نمط V21 و V22 و V23 — وليس نداءً على
-- add_index_if_missing الذي ينشئه V1.
--
-- **وهذه أول صيغة لهذه الهجرة كانت تناديه، وكانت ستفشل على كل تركيب.** V1 ينشئ الإجراء في سطره
-- 311 ويستعمله ثمانين مرة ثم **يحذفه في سطره 994**، فلا وجود له بعد انتهاء الخط الأساسي؛ وتركيب
-- قائم مختوم على V1 لم يُنشئه أصلا. وهذا هو بالضبط صنف العطل الذي يسجّله
-- V1_1__audit_log_procedure.sql: هجرة مرقَّمة لا يجوز أن تعتمد على شيء لا تنشئه هجرة مرقَّمة
-- تسبقها. لم يكشفه أي اختبار في البناء الأخضر - كُشف عند تطبيق الهجرات على سكيما بُنيت من الصفر،
-- وهو السبب الوحيد لفعل ذلك.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_party_index_if_missing$$
CREATE PROCEDURE add_party_index_if_missing(IN t_name VARCHAR(64), IN i_name VARCHAR(64),
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

DELIMITER ;

CALL add_party_index_if_missing('customers_accounts', 'customers_accounts_party_date_idx',
                                'account_code, account_date');
CALL add_party_index_if_missing('suppliers_accounts', 'suppliers_accounts_party_date_idx',
                                'account_code, account_date');

DROP PROCEDURE IF EXISTS add_party_index_if_missing;
