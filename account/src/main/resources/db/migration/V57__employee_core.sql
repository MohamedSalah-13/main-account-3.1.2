-- =====================================================================
-- V57 - الملف الوظيفي: الوظيفة صف، والراتب مؤرَّخ، والموظف له حالة.
--
-- العقد كاملا في docs/employees-plan.md. الثلاثة التي تفعلها هذه الهجرة:
--
-- ### 1. الوظيفة صف في `jobs`، والمندوب علامة لا رقم
--
-- `jobs` جدول حقيقي منذ V1 باسم فريد - ويقابله `UsersType` في Java بأربع قيم وأرقام
-- مكتوبة باليد (1 مسئول، 2 مدير، 3 موظف، 4 مندوب). و`UsersType.getUserTypeById` ترجع
-- **null** لأي رقم آخر، فأي صف يُضاف إلى الجدول يُسقط شاشة الموظفين عند الرسم وعند
-- الحفظ معا. أي أن جدولا قابلا للتحرير كان في الحقيقة أربع قيم مقفلة.
--
-- والمندوب كان `job = 4` حرفيا في `EmployeesDao.DELEGATE_JOB`، فمحل عنده «مندوب توزيع»
-- و«مندوب تحصيل» لا يستطيع أن يكون عنده اثنان. `is_delegate` هي ما يجعل الوظيفة وظيفة
-- مندوب من الآن، والهجرة تضعها على الصف 4 وحده - وهو ما كان يعنيه الرقم بالضبط - فلا
-- يتغير شيء عند الترقية.
--
-- `job_name` يتسع من 20 إلى 50 حرفا: «مندوب مبيعات وتحصيل» لا يسع في عشرين.
--
-- ### 2. الراتب مؤرَّخ
--
-- `employees.salary` رقم واحد بلا تاريخ. رفع راتب في سبتمبر يجعل أي حساب لأغسطس - الآن
-- أو بعد سنة - يقرأ الرقم الجديد، ولا شيء يسجل أنه تغير. وهو **نفس درس `first_balance`
-- و`treasury.amount`** بالحرف.
--
-- `employee_compensation` يحمل التاريخ، والعمود القديم يبقى **براتب التعيين** بتعليق
-- يقول ذلك - نفس معاملة `treasury.amount` في V20، ولنفس السبب: إسقاطه يأخذ معه تاريخ كل
-- تثبيت في كل تركيب قائم. والترحيل ينسخ (salary, hire_date) لكل موظف، فلا يتحرك رقم لأحد.
--
-- ### 3. الحالة وترك العمل
--
-- `DeleteRegistry.EMPLOYEES` يرفض حذف موظف على فاتورة أو مصروف - وهو الصواب، فتاريخه لا
-- يُحذف معه - و`employees.column_name` فريد فالاسم لا يُعاد إصداره. فمن ترك العمل كان
-- يبقى في كومبو المندوب وفي كل قائمة إلى الأبد. `is_active` هو ما حلّ به V56 نفس
-- المشكلة للأطراف، و`UsersService.updateActive` للمستخدمين.
--
-- و`birth_date` يصير NULL-able: `EmployeesDao.map` كانت تكتب
-- `LocalDate.parse(rs.getDate(...).toString())`، فكان تاريخ الميلاد **إجباريا لتسجيل
-- موظف**، وهي بيانات كثير من المحلات لا تملكها. القارئ الجديد يقرؤه كـ NULL.
--
-- ### الإجراءات المساعدة
--
-- معرَّفة هنا ومحذوفة في آخر الملف. `add_index_if_missing` ينشئه V1 في سطره 311
-- و**يحذفه في سطره 994**، و`add_column_if_missing` لا تنشئه هجرة أساسية إطلاقا - فنداء
-- أيٍّ منهما من هجرة جديدة يفشل على كل تركيب، جديدا كان أو مرقًّى. الدرس دُفع ثمنه في
-- V55 وV56 في يوم واحد، و`MigrationHelperProcedureTest` يُفشل البناء عليه الآن.
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_employee_column_if_missing$$
CREATE PROCEDURE add_employee_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64),
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

DROP PROCEDURE IF EXISTS drop_employee_job_foreign_key$$
CREATE PROCEDURE drop_employee_job_foreign_key()
BEGIN
    DECLARE fk_name VARCHAR(64);

    -- MAX(), not a bare SELECT INTO: a query that matches nothing still returns one row with
    -- NULL in it, where a bare SELECT INTO raises "no data" and the handler for it would have to
    -- be declared here for no other reason.
    SELECT MAX(CONSTRAINT_NAME)
    INTO fk_name
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'employees'
      AND COLUMN_NAME = 'job'
      AND REFERENCED_TABLE_NAME = 'jobs';

    IF fk_name IS NOT NULL THEN
        SET @query = CONCAT('ALTER TABLE employees DROP FOREIGN KEY ', fk_name);
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS add_employee_constraint_if_missing$$
CREATE PROCEDURE add_employee_constraint_if_missing(IN t_name VARCHAR(64), IN k_name VARCHAR(64),
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

DROP PROCEDURE IF EXISTS add_employee_index_if_missing$$
CREATE PROCEDURE add_employee_index_if_missing(IN t_name VARCHAR(64), IN i_name VARCHAR(64),
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
-- 1) الوظائف
-- ---------------------------------------------------------------------

-- «مندوب مبيعات وتحصيل» لا يسع في عشرين حرفا. والعمود ليس مفتاحا، فلا يحتاج مراسم.
ALTER TABLE jobs
    MODIFY COLUMN job_name VARCHAR(50) NOT NULL;

-- الرقم كان يُكتب باليد لأن الصفوف الأربعة كانت مزروعة. شاشة تضيف وظيفة تحتاج من يعطيها
-- رقما، و AUTO_INCREMENT يبدأ من أكبر رقم موجود فلا يصطدم بالمزروع.
--
-- **و MySQL ترفض تعديل عمود يشير إليه مفتاح أجنبي** (خطأ 1833): `employees.job` يشير إلى
-- `jobs.id`، فالمفتاح يُنزع ثم يُعاد. وهو يُبحث عنه باسمه في information_schema لا بالاسم
-- الذي يفترضه V1: قاعدة قديمة مرّت على V4 قد تحمله باسم آخر، وحذف اسم غير موجود يفشل
-- الهجرة كلها. وهذا السطر بالذات كان ALTER مباشرا، **ولم يكشفه بناء أخضر**: هجرة لا تكون
-- خاطئة إلا عند قراءة MySQL لها، وترحيل سكيما من الصفر هو الشيء الوحيد الذي يقرؤها.
CALL drop_employee_job_foreign_key();

ALTER TABLE jobs
    MODIFY COLUMN id INT AUTO_INCREMENT NOT NULL;

CALL add_employee_constraint_if_missing('employees', 'employees_jobs_id_fk',
    'FOREIGN KEY (job) REFERENCES jobs (id)');

CALL add_employee_column_if_missing('jobs', 'is_delegate',
    'TINYINT(1) DEFAULT 0 NOT NULL COMMENT ''وظيفة مندوب: هي ما يملأ كومبو المندوب، لا الرقم 4''');
CALL add_employee_column_if_missing('jobs', 'is_active',
    'TINYINT(1) DEFAULT 1 NOT NULL COMMENT ''وظيفة موقوفة تبقى على من يحملها وتخرج من الكومبوهات''');
CALL add_employee_column_if_missing('jobs', 'default_salary',
    'DECIMAL(14, 2) NULL COMMENT ''الراتب الذي تقترحه الشاشة عند اختيار الوظيفة. لا يُلزم أحدا''');
CALL add_employee_column_if_missing('jobs', 'notes', 'VARCHAR(200) NULL');
CALL add_employee_column_if_missing('jobs', 'date_insert',
    'DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL');
CALL add_employee_column_if_missing('jobs', 'updated_at',
    'TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP');
CALL add_employee_column_if_missing('jobs', 'user_id', 'INT DEFAULT 1 NOT NULL');

-- الصف 4 هو ما كان يعنيه DELEGATE_JOB في كل استعلام للمناديب منذ V1. فهذه ليست قاعدة
-- جديدة تُفرض، بل القاعدة القائمة تُكتب في المكان الذي يمكن تغييرها منه.
UPDATE jobs SET is_delegate = 1 WHERE id = 4;

-- ---------------------------------------------------------------------
-- 2) الموظف
-- ---------------------------------------------------------------------

CALL add_employee_column_if_missing('employees', 'is_active',
    'TINYINT(1) DEFAULT 1 NOT NULL COMMENT ''موظف موقوف يبقى بتاريخه ويخرج من الكومبوهات''');
CALL add_employee_column_if_missing('employees', 'end_date',
    'DATE NULL COMMENT ''تاريخ ترك العمل. لا يُحذف الموظف: تاريخه على الفواتير والمصروفات''');
CALL add_employee_column_if_missing('employees', 'national_id', 'VARCHAR(20) NULL');
CALL add_employee_column_if_missing('employees', 'employment_type',
    'VARCHAR(20) DEFAULT ''FULL_TIME'' NOT NULL');
CALL add_employee_column_if_missing('employees', 'default_treasury_id',
    'INT NULL COMMENT ''الخزينة التي يُصرف منها له عادة. لا قيد أجنبي عمدا - انظر V57. ولا يقرؤه شيء بعد''');
CALL add_employee_column_if_missing('employees', 'notes', 'VARCHAR(500) NULL');

-- تاريخ الميلاد لم يعد شرطا لتسجيل موظف.
ALTER TABLE employees
    MODIFY COLUMN birth_date DATE NULL,
    MODIFY COLUMN salary DECIMAL(14, 2) NOT NULL
        COMMENT 'راتب التعيين، لا الراتب الحالي. الساري من employee_compensation - انظر V57';

-- نوع التعاقد قائمة مغلقة، ومن يكتب فيها غير ذلك يكتب شيئا لا يعرفه أي حساب راتب.
-- CHECK لا ENUM: إضافة قيمة إلى ENUM تعيد بناء الجدول، وإضافتها هنا تعديل قيد.
CALL add_employee_constraint_if_missing('employees', 'employees_employment_type_chk',
    'CHECK (employment_type IN (''FULL_TIME'', ''PART_TIME'', ''CONTRACT'', ''TEMPORARY''))');

-- القائمة تفتح على النشطين مرتَّبين بالاسم، وهو ما يخدمه هذا الفهرس وحده.
CALL add_employee_index_if_missing('employees', 'employees_active_name_idx', 'is_active, column_name');

-- ---------------------------------------------------------------------
-- 3) الراتب المؤرَّخ
-- ---------------------------------------------------------------------

-- ON DELETE CASCADE عمدا، ولذلك **لا يُعلَن في DeleteRegistry**: القاعدة المكتوبة في
-- CLAUDE.md هي أن يُعلَن ما لا يَجرف وحده - ومفتاح جارف يجعل الحذف مرفوضا في التطبيق
-- ومقبولا في القاعدة. وتاريخ الرواتب يخص صف الموظف: يزول معه إن زال، وهو لا يزول إلا
-- إذا لم تشر إليه فاتورة ولا مصروف ولا سطر مسيَّر.
CREATE TABLE IF NOT EXISTS employee_compensation
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    employee_id    INT                                    NOT NULL,
    effective_from DATE                                   NOT NULL,
    salary_kind    VARCHAR(20) DEFAULT 'MONTHLY'          NOT NULL,
    rate           DECIMAL(14, 2)                         NOT NULL,
    notes          VARCHAR(200)                           NULL,
    date_insert    DATETIME    DEFAULT CURRENT_TIMESTAMP  NOT NULL,
    updated_at     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP  NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id        INT         DEFAULT 1                  NOT NULL,
    CONSTRAINT employee_compensation_uk UNIQUE (employee_id, effective_from),
    CONSTRAINT employee_compensation_employees_id_fk
        FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE,
    CONSTRAINT employee_compensation_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT employee_compensation_rate_chk CHECK (rate >= 0),
    CONSTRAINT employee_compensation_kind_chk
        CHECK (salary_kind IN ('MONTHLY', 'DAILY', 'HOURLY', 'COMMISSION'))
);

-- الصف الأحدث الذي لم يأتِ تاريخه بعد هو الساري، وهو ما تجيب به employee_current_compensation.
--
-- ولا تعليق على الجدول نفسه عمدا: `SchemaForeignKeys` يقرأ أجسام CREATE TABLE من ملفات
-- الهجرة بنمط ينتهي عند `\n);`، فجدول ينتهي بـ `) COMMENT '...';` تختفي مفاتيحه من القارئ
-- الذي يحاسب DeleteRegistry و WipeCatalog. تعليق جميل يشتري صمتا في الاختبار.

-- الترحيل: راتب التعيين من تاريخ التعيين. لا رقم يتحرك، ولا موظف يبقى بلا تعويض معروف.
INSERT INTO employee_compensation (employee_id, effective_from, salary_kind, rate, notes, user_id)
SELECT e.id, e.hire_date, 'MONTHLY', e.salary, 'مرحَّل من راتب التعيين - V57', e.user_id
FROM employees e
WHERE NOT EXISTS (SELECT 1 FROM employee_compensation c WHERE c.employee_id = e.id);

-- ---------------------------------------------------------------------
-- 4) الصلاحيات
-- ---------------------------------------------------------------------
--
-- كل مفتاح جديد يُمنح لمن يملك ما يقابله اليوم، على نمط V34 وV35 وV55: لا أحد يفقد قدرة
-- عند الترقية. و`employee.salary.change` مفتاح مستقل لأن **تغيير راتب ليس تصحيح رقم
-- هاتف**: الأول يغيّر ما يُدفع شهريا، والثاني بيانات اتصال، وهما ليسا نفس الثقة.

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('employee.salary.change', 'تغيير راتب موظف بزيادة مؤرَّخة',
        'EMPLOYEES', 'employee.salary', 'CHANGE', 'HIGH', 0, 1, 1),
       ('job.show', 'عرض الوظائف', 'EMPLOYEES', 'job', 'SHOW', 'LOW', 0, 1, 1),
       ('job.create', 'إضافة وظيفة', 'EMPLOYEES', 'job', 'CREATE', 'MEDIUM', 0, 1, 1),
       ('job.update', 'تعديل وظيفة', 'EMPLOYEES', 'job', 'UPDATE', 'MEDIUM', 0, 1, 1),
       ('job.delete', 'حذف وظيفة', 'EMPLOYEES', 'job', 'DELETE', 'MEDIUM', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key        = VALUES(module_key),
                        resource_key      = VALUES(resource_key),
                        action_key        = VALUES(action_key),
                        risk_level        = VALUES(risk_level),
                        system_permission = 1,
                        enabled           = 1;

-- من يملك تعديل بيانات الموظف يملك تغيير راتبه اليوم (لا شيء يفرّق بينهما)، فلا يفقده.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'employee.update'
         JOIN auth_permission granted ON granted.permission_key = 'employee.salary.change';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'employee.show'
         JOIN auth_permission granted ON granted.permission_key = 'job.show';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'employee.create'
         JOIN auth_permission granted ON granted.permission_key = 'job.create';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'employee.update'
         JOIN auth_permission granted ON granted.permission_key = 'job.update';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'employee.delete'
         JOIN auth_permission granted ON granted.permission_key = 'job.delete';

DROP PROCEDURE IF EXISTS add_employee_index_if_missing;
DROP PROCEDURE IF EXISTS drop_employee_job_foreign_key;
DROP PROCEDURE IF EXISTS add_employee_constraint_if_missing;
DROP PROCEDURE IF EXISTS add_employee_column_if_missing;
