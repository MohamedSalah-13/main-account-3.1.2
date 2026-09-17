-- =====================================================================
-- V64 - بنود المصروفات صفوف تُدار، والمصروف يحمل مستفيدا ورقم إيصال.
--
-- العقد كاملا في docs/expenses-plan.md. ما تفعله هذه الهجرة، وما لا تفعله عمدا:
--
-- ### 1. البند صف يُدار، لا رقم مكتوب في Java
--
-- `expenses` جدول حقيقي منذ V1 باسم فريد - ويقابله `ExpensesType` في Java بستة بنود
-- وأرقام مكتوبة باليد (1..6)، وشاشة الإدخال لا تعرض غيرها. فبند «عمولات تحويل» الذي
-- بذرته V21 غير موجود في الشاشة أصلا، ولا شاشة في البرنامج تضيف بندا أو تسمّيه. وهو عطل
-- `UsersType` نفسه الذي أزالته V57 من الوظائف (ع-١ وع-٢ في الخطة).
--
-- `id` يصير AUTO_INCREMENT، و**MySQL ترفض تعديل عمود يشير إليه مفتاح أجنبي** (خطأ 1833):
-- `expenses_details.type_code` يشير إليه. فالمفتاح يُنزع ويُعاد، ويُبحث عنه **بأعمدته في
-- information_schema** لا باسمه - درس V57 الذي فشل على كل تركيب قبل أن يُكتشف.
--
-- ### 2. مستويان، والبند الذي يعتمد عليه النظام له مفتاح ثابت
--
-- `parent_id` يجعل البند فرعيا تحت رئيسي (ق-٣). البنود الموجودة تبقى رئيسية بلا أبناء،
-- ولا يتحرك صف مصروف واحد.
--
-- `system_key` هو ما تبحث به عمولة المحفظة عن بندها من الآن (ق-٤). كانت تبحث **بالاسم**،
-- وهذا صحيح فقط لأن أحدا لم يكن يستطيع تسمية بند - وأول ما تفعله الشاشة الجديدة هو أن
-- تسمح بذلك. الاسم يُقرأ هنا مرة واحدة، وقت الهجرة، وهو الوقت الوحيد المضمون فيه.
--
-- ### 3. بنود الموظفين
--
-- `employee_payment` يعلّم البند الذي يُدفع عليه للموظفين، فتعرضه شاشة صرف الموظف وحدها
-- وتخفيه شاشة المصروفات (ق-٥). والسلفة كانت تُدخل من شاشة المصروفات بلا غرض، فتظهر في كشف
-- الموظف «راتبا» (ع-٣).
--
-- **والهجرة لا تكتب employee_cash_purpose للسلف القديمة** - قرار م-٣: تصحيح بأثر رجعي
-- يغيّر وصف صفوف في كشوف وقّع عليها موظفون، وهو قرار بذاته لا أثر جانبي لهجرة.
--
-- ### 4. ما لا يتحرك
--
-- لا `amount` ولا `date` ولا `treasury_id` ولا `type_code` لأي صف. رصيد كل خزينة، وعمود
-- المصروفات في قائمة الأرباح، ورصيد كل موظف، كلها قبل الهجرة كما بعدها.
--
-- ### الإجراءات المساعدة
--
-- معرَّفة هنا ومحذوفة في آخر الملف (MigrationHelperProcedureTest). و`add_constraint_if_missing`
-- بهذا الاسم تحديدا: `SchemaForeignKeys` يقرأ المفاتيح المضافة بعد إنشاء الجدول من نداءاته،
-- فمفتاح يُضاف بإجراء باسم آخر يختفي من الاختبار الذي يحاسب DeleteRegistry عليه.
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_expense_column_if_missing$$
CREATE PROCEDURE add_expense_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64),
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

DROP PROCEDURE IF EXISTS drop_expense_heading_foreign_key$$
CREATE PROCEDURE drop_expense_heading_foreign_key()
BEGIN
    DECLARE fk_name VARCHAR(64);

    -- MAX(), not a bare SELECT INTO: a query matching nothing still returns one row with NULL
    -- in it, where a bare SELECT INTO raises "no data".
    SELECT MAX(CONSTRAINT_NAME)
    INTO fk_name
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'expenses_details'
      AND COLUMN_NAME = 'type_code'
      AND REFERENCED_TABLE_NAME = 'expenses';

    IF fk_name IS NOT NULL THEN
        SET @query = CONCAT('ALTER TABLE expenses_details DROP FOREIGN KEY ', fk_name);
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS add_constraint_if_missing$$
CREATE PROCEDURE add_constraint_if_missing(IN t_name VARCHAR(64), IN k_name VARCHAR(64),
                                           IN k_def TEXT)
BEGIN
    DECLARE key_exists INT;

    SELECT COUNT(*)
    INTO key_exists
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = t_name
      AND CONSTRAINT_NAME = k_name;

    IF key_exists = 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' ADD CONSTRAINT ', k_name, ' ', k_def);
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS add_expense_index_if_missing$$
CREATE PROCEDURE add_expense_index_if_missing(IN t_name VARCHAR(64), IN i_name VARCHAR(64),
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

-- ---------------------------------------------------------------------
-- 1) الرقم للقاعدة
-- ---------------------------------------------------------------------

CALL drop_expense_heading_foreign_key();

-- NO_AUTO_VALUE_ON_ZERO للحظة التعديل وحدها: قاعدة قديمة تحمل بندا برقم 0 - مهما كان
-- مستبعدا - كانت ستجد ذلك البند مُعاد الترقيم، والمصروفات عليه تشير إلى رقم لم يعد موجودا.
SET @expense_headings_old_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = CONCAT_WS(',', NULLIF(@@SESSION.sql_mode, ''), 'NO_AUTO_VALUE_ON_ZERO');

ALTER TABLE expenses
    MODIFY COLUMN id INT AUTO_INCREMENT NOT NULL;

SET SESSION sql_mode = @expense_headings_old_mode;

CALL add_constraint_if_missing('expenses_details', 'expenses_details_expenses_id_fk',
    'FOREIGN KEY (type_code) REFERENCES expenses (id)');

-- ---------------------------------------------------------------------
-- 2) البند
-- ---------------------------------------------------------------------

CALL add_expense_column_if_missing('expenses', 'parent_id',
    'INT NULL COMMENT ''البند الرئيسي، أو NULL للبند الرئيسي نفسه. مستويان لا أكثر - الخدمة تفرض ذلك''');
CALL add_expense_column_if_missing('expenses', 'is_active',
    'TINYINT(1) DEFAULT 1 NOT NULL COMMENT ''بند موقوف يبقى على مصروفاته ويخرج من شاشة الإدخال''');
CALL add_expense_column_if_missing('expenses', 'system_key',
    'VARCHAR(40) NULL COMMENT ''البند الذي يعتمد عليه النظام يُعرف بهذا لا بالاسم. WALLET_FEE لعمولة المحفظة''');
CALL add_expense_column_if_missing('expenses', 'employee_payment',
    'TINYINT(1) DEFAULT 0 NOT NULL COMMENT ''بند صرف للموظفين: تعرضه شاشة صرف الموظف وحدها''');
CALL add_expense_column_if_missing('expenses', 'sort_order', 'INT DEFAULT 0 NOT NULL');
CALL add_expense_column_if_missing('expenses', 'date_insert',
    'DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL');
CALL add_expense_column_if_missing('expenses', 'user_id', 'INT DEFAULT 1 NOT NULL');

-- بلا CASCADE: حذف بند رئيسي له أبناء يُرفض، ولا يأخذهم معه.
CALL add_constraint_if_missing('expenses', 'expenses_parent_id_fk',
    'FOREIGN KEY (parent_id) REFERENCES expenses (id)');
CALL add_constraint_if_missing('expenses', 'expenses_system_key_uk', 'UNIQUE (system_key)');

-- عمولة المحفظة. بحثا بالاسم مرة واحدة هنا، والاسم فريد فالصف واحد على الأكثر.
UPDATE expenses
SET system_key = 'WALLET_FEE'
WHERE expenses_name = 'عمولات تحويل'
  AND system_key IS NULL;

-- وإن لم يوجد - أعاد أحدهم تسميته بـ SQL قبل التحديث - يُنشأ: عمولة لا تجد بندها ترفض
-- التحصيل كله، وتحصيل مرفوض أسوأ من بند مكرر المعنى. HAVING لا WHERE، للسبب المكتوب في V21:
-- الدالة التجميعية بلا GROUP BY ترجع صفا واحدا حتى على جدول فارغ.
INSERT INTO expenses (expenses_name, system_key)
SELECT 'عمولات تحويل', 'WALLET_FEE'
FROM expenses
HAVING COALESCE(SUM(system_key = 'WALLET_FEE'), 0) = 0
   AND COALESCE(SUM(expenses_name = 'عمولات تحويل'), 0) = 0;

-- ---------------------------------------------------------------------
-- 3) بنود الموظفين
-- ---------------------------------------------------------------------

-- البندان اللذان تبذرهما V1 لهذا الغرض.
UPDATE expenses
SET employee_payment = 1
WHERE expenses_name IN ('مرتبات', 'سلف')
  AND system_key IS NULL;

-- وكل بند **كل** صفوفه تحمل موظفا - «كل» لا «أي»: بند «أخرى» عليه صف موظف واحد بالخطأ لا
-- يجب أن يختفي من شاشة المصروفات.
UPDATE expenses e
    JOIN (SELECT type_code
          FROM expenses_details
          GROUP BY type_code
          HAVING COUNT(*) > 0
             AND SUM(emp_id IS NULL) = 0) only_employees
    ON only_employees.type_code = e.id
SET e.employee_payment = 1
WHERE e.system_key IS NULL;

-- ---------------------------------------------------------------------
-- 4) الحركة
-- ---------------------------------------------------------------------

CALL add_expense_column_if_missing('expenses_details', 'payee',
    'VARCHAR(100) NULL COMMENT ''لمن دُفع - نص حر، لا ربط بالموردين عمدا (ق-٧)''');
CALL add_expense_column_if_missing('expenses_details', 'reference_no',
    'VARCHAR(50) NULL COMMENT ''رقم الإيصال أو الفاتورة التي دُفع بها المصروف''');

-- التقارير بالبند تقرأ (البند، التاريخ)، واقتراحات المستفيد تقرأ الاسم.
CALL add_expense_index_if_missing('expenses_details', 'expenses_details_type_date_idx', 'type_code, date');
CALL add_expense_index_if_missing('expenses_details', 'expenses_details_payee_idx', 'payee');

-- ---------------------------------------------------------------------
-- 5) الصلاحيات
-- ---------------------------------------------------------------------
--
-- المفاتيح هي ما يشتقّه AppPermissions.definition من النص نفسه، والمزامنة عند بدء التشغيل
-- تكتب نفس القيم؛ الصفوف هنا موجودة قبلها فقط لكي يجد المنح ما يربطه به. وكل مفتاح يُمنح لمن
-- يملك ما يقابله اليوم، على نمط V34 وV55 وV58: لا أحد يفقد قدرة عند الترقية.

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('expenses.show', 'عرض المصروفات', 'EXPENSES', 'expenses', 'SHOW', 'LOW', 0, 1, 1),
       ('expenses.headings.update', 'إدارة بنود المصروفات: إضافة وتسمية ونقل وإيقاف',
        'EXPENSES', 'expenses.headings', 'UPDATE', 'HIGH', 0, 1, 1),
       ('expenses.export', 'طباعة المصروفات وتصديرها', 'EXPENSES', 'expenses', 'EXPORT', 'LOW', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key        = VALUES(module_key),
                        resource_key      = VALUES(resource_key),
                        action_key        = VALUES(action_key),
                        risk_level        = VALUES(risk_level),
                        system_permission = 1,
                        enabled           = 1;

-- الشاشة القديمة كانت تُفتح بـ treasury.show، و expenses.show لم يقرأها شيء. من كان يرى
-- المصروفات لا يفقدها حين تصير الصلاحية التي تحمل اسمها هي ما يُسأل.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'treasury.show'
         JOIN auth_permission granted ON granted.permission_key = 'expenses.show';

-- لم تكن هناك شاشة بنود أصلا؛ أقرب ثقة لها اليوم هي تعديل مصروف.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'expenses.update'
         JOIN auth_permission granted ON granted.permission_key = 'expenses.headings.update';

-- زر الطباعة في الشاشة القديمة كان بلا صلاحية. بعد منح expenses.show أعلاه، فيشمل من أخذها للتو.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'expenses.show'
         JOIN auth_permission granted ON granted.permission_key = 'expenses.export';

DROP PROCEDURE IF EXISTS add_expense_index_if_missing;
DROP PROCEDURE IF EXISTS add_constraint_if_missing;
DROP PROCEDURE IF EXISTS drop_expense_heading_foreign_key;
DROP PROCEDURE IF EXISTS add_expense_column_if_missing;
