-- =====================================================================
-- V58 - حساب الموظف: دفتر لما ليس نقدا، وغرض بجانب كل صرف.
--
-- العقد في docs/employees-plan.md §4 (المرحلة ب) وق-١ وق-٥ وق-٦. والقاعدة التي يقوم
-- عليها الملف كله سطر واحد:
--
--   **النقد له كاتب واحد: `expenses_details`.** أي جنيه يخرج للموظف - راتب، سلفة،
--   مكافأة نقدية، تسوية - هو صف هناك بـ`emp_id`، لا أكثر.
--
-- وثلاثة أسباب، لا واحد:
--
--   * `treasury_balance` تقرأ `expenses_details` بالفعل، فالخزينة تعرف النقد بلا سطر جديد؛
--   * `ExpensesDetailsService` يمرّ بالفعل بـ`ShiftGate` و`PeriodLock` و`AuthorizationGuard`
--     ويكتب في دفتر الوردية - جدول نقد ثانٍ يعني إعادة بناء أربع قواعد وأربع فرص لنسيان
--     إحداها؛
--   * وسنوات الصرف الموجودة في قواعد العملاء **تدخل الكشف الجديد بلا ترحيل بيانات**، لأنه
--     يقرأ نفس الجدول الذي كُتبت فيه.
--
-- وينتج عن ذلك مباشرة ما يجعل `employee_ledger` مفهوما: **هو لا يسجّل النقد أصلا.** يسجّل
-- ما ليس نقدا فقط - استحقاق، مكافأة مستحقة، خصم، عمولة معتمدة، رصيد افتتاحي - والكشف يضمّ
-- الاثنين، تماما كما تضمّ `account_customer_table` المدفوعات مع `total_sales`.
--
-- > صف في دفتر الموظف بمبلغ نقدي هو **عطل، لا ميزة**.
--
-- ### لماذا الاتجاه في النوع، لا في إشارة المبلغ
--
-- `amount` موجب دائما (CHECK)، والاتجاه يقرأ من `kind`. فلا يوجد صف يقول شيئا وإشارته تقول
-- غيره، ولا صف بإشارة كُتبت بالخطأ يمرّ لأنه «رقم صحيح». والاتجاه معلن مرة واحدة في
-- `EmployeeEntryKind.sign()`، وتعيده الـview بـCASE يطابقه، ويربط الاثنين
-- `EmployeeLedgerAgreesWithEntryKindTest` - لأن تعريفين للاتجاه هو بالضبط العطل الذي أنفق
-- نظام الأطراف شهرا في إزالته (V15، انظر `DocumentLedgerEffect` في CLAUDE.md).
--
-- والرصيد الافتتاحي نوعان لا نوع بإشارة: `OPENING_DUE` (له عندنا) و`OPENING_OWED` (علينا
-- عنده). شاشة تسأل «لصالح الموظف أم عليه» أوضح من خانة تقبل سالبا.
--
-- ### السلفة تُخصم مرة واحدة
--
-- السلفة نقد خرج، فهي صف مصروف، فهي مدينة على الموظف من لحظتها. المسيَّر (المرحلة ج) يدخل
-- **الاستحقاق كاملا** في هذا الدفتر ويصرف الفرق نقدا، فيوازن الرصيد نفسه لأن الطرفين
-- مسجّلان مرة واحدة كلٌّ في مكانه. خصمها مرة ثانية من الاستحقاق يحمّل الموظف بضعفها.
--
-- وغرض الصرف بجانب صف المصروف لا داخله: `employee_cash_purpose`. **ولا عمود خاص
-- بالموظفين يُضاف إلى `expenses_details`** - الجدول مشترك، وأول عمود خاص يفتح الباب لعشرة.
-- و**صف مصروف بلا صف غرض = راتب**، وهو ما تعنيه سنوات الصرف السابقة فعلا.
--
-- ### ولا يُزرع بند مصروف
--
-- أول صيغة من هذه الهجرة زرعت بندا اسمه «رواتب وأجور» - و**التشغيل على سكيما من الصفر
-- أظهر أن `V1` يزرع «مرتبات» و«سلف» أصلا** (سطر 1168). فكان البند الجديد ثالثا مكررا
-- يظهر في كل تقرير مصروفات بجانب اثنين يعنيان ما يعنيه. حُذف قبل أن يُشحن.
--
-- و`expenses_details.type_code` إجباري، فالصف لا بد له من بند: تختاره شاشة الصرف من
-- `expenses` كما تفعل شاشة المصروفات، **بلا رقم مكتوب في Java** - ربط ثابت مثل
-- `SALARY -> 1` هو بالضبط خطأ `UsersType` و`DELEGATE_JOB` الذي أزالته المرحلة أ، لأن
-- الشاشة تسمح بإعادة تسمية البند وحذفه. و`purpose` هو التعريف الوحيد الذي يقرؤه كشف
-- الموظف؛ البند تصنيف شاشة المصروفات لنفسها، ولصف المصروف بند منذ V1.
--
-- ولا أثر على قائمة الأرباح: التقرير السنوي و`ProfitLossDao` يقرآن `expenses_details`
-- بتاريخها، فالسلفة مصروف يوم خروجها والباقي مصروف يوم صرفه، والمجموع هو الاستحقاق مرة
-- واحدة. لا ازدواج، وإنما توقيت نقدي - وهو ما يفعله هذا النظام بكل مصروف آخر.
--
-- ### الإجراءات المساعدة
--
-- معرَّفة هنا ومحذوفة في آخر الملف، للسبب الذي دفعه V55 وV56 وV57: `add_index_if_missing`
-- يحذفه V1 في سطره 994، و`add_column_if_missing` لا تنشئه هجرة أساسية إطلاقا.
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_employee_ledger_index$$
CREATE PROCEDURE add_employee_ledger_index(IN t_name VARCHAR(64), IN i_name VARCHAR(64),
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
-- 1) الدفتر: ما ليس نقدا
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS employee_ledger
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    employee_id    INT                                   NOT NULL,
    entry_date     DATE                                  NOT NULL,
    kind           VARCHAR(20)                           NOT NULL,
    amount         DECIMAL(14, 2)                        NOT NULL,
    notes          VARCHAR(255)                          NULL,
    payroll_run_id INT                                   NULL,
    date_insert    DATETIME  DEFAULT CURRENT_TIMESTAMP   NOT NULL,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP   NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id        INT       DEFAULT 1                   NOT NULL,
    CONSTRAINT employee_ledger_employees_id_fk FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT employee_ledger_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT employee_ledger_amount_chk CHECK (amount >= 0),
    CONSTRAINT employee_ledger_kind_chk
        CHECK (kind IN ('OPENING_DUE', 'OPENING_OWED', 'ENTITLEMENT', 'BONUS', 'COMMISSION',
                        'DEDUCTION'))
);

-- لا قيد أجنبي جارف على الموظف عمدا: `DeleteRegistry.EMPLOYEES` يرفض حذف موظف له حركة،
-- وتاريخه المالي ليس شيئا يُجرف مع صفّه. وهو معلَن هناك، ويقرأ `DeleteRegistryTest`
-- المفاتيح من هذا الملف فيُفشل البناء إن نُسي.

-- الكشف يفلتر على (الموظف، التاريخ) معا، والمفتاح الأجنبي يغطي العمود الأول وحده.
CALL add_employee_ledger_index('employee_ledger', 'employee_ledger_employee_date_idx',
                               'employee_id, entry_date');

-- `payroll_run_id` بلا مفتاح أجنبي: جدول المسيَّر في المرحلة ج، ومفتاح إلى جدول غير موجود
-- لا يُكتب. ولا يقرؤه شيء بعد.
ALTER TABLE employee_ledger
    MODIFY COLUMN payroll_run_id INT NULL
        COMMENT 'مسيَّر الرواتب الذي أنشأ الصف - المرحلة ج. لا قيد أجنبي بعد، ولا يقرؤه شيء';

-- ---------------------------------------------------------------------
-- 2) غرض الصرف، بجانب صف المصروف
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS employee_cash_purpose
(
    expense_id     INT                                 NOT NULL PRIMARY KEY,
    purpose        VARCHAR(20)                         NOT NULL,
    payroll_run_id INT                                 NULL,
    date_insert    DATETIME DEFAULT CURRENT_TIMESTAMP  NOT NULL,
    user_id        INT      DEFAULT 1                  NOT NULL,
    CONSTRAINT employee_cash_purpose_expenses_details_id_fk
        FOREIGN KEY (expense_id) REFERENCES expenses_details (id) ON DELETE CASCADE,
    CONSTRAINT employee_cash_purpose_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT employee_cash_purpose_purpose_chk
        CHECK (purpose IN ('SALARY', 'ADVANCE', 'BONUS', 'SETTLEMENT'))
);

-- ON DELETE CASCADE هنا صحيح ولذلك **لا يُعلَن في DeleteRegistry**: الغرض صفة على صف
-- المصروف، يزول بزواله. والقاعدة المكتوبة في CLAUDE.md هي أن يُعلَن ما لا يَجرف وحده -
-- ومفتاح جارف مُعلَن يجعل الحذف مرفوضا في التطبيق ومقبولا في القاعدة.

-- ---------------------------------------------------------------------
-- 3) الصلاحيات
-- ---------------------------------------------------------------------
--
-- كل مفتاح يُمنح لمن يملك ما يقابله اليوم، على نمط V34 وV35 وV55 وV57: لا أحد يفقد قدرة
-- عند الترقية.
--
-- **و`employee.account.adjust` ليس في جدول §9 من الخطة، وأُضيف هنا بقرار مكتوب:** الخصم
-- والمكافأة يكتبان صفا في الدفتر، و`AuthorizationArchitectureTest` يُفشل البناء على خدمة
-- تكتب صفا بلا `require` - فالسؤال ليس «هل نحرسه» بل «بأي مفتاح». و`employee.pay` خطأ
-- في الاتجاهين: الخصم لا يُخرج جنيها من خزينة، ومن يصرف النقد ليس بالضرورة من يقرر أن على
-- الموظف مئتين. وهو نفس التفريق الذي أقامه V55 بين `account.create` و`account.adjust`
-- للأطراف، ولنفس السبب: النقد يقابله عدّ في الدرج، والقرار لا يقابله شيء.

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('employee.account.show', 'عرض كشف حساب الموظف',
        'EMPLOYEES', 'employee.account', 'SHOW', 'MEDIUM', 0, 1, 1),
       ('employee.account.adjust', 'تسجيل خصم أو مكافأة على حساب الموظف',
        'EMPLOYEES', 'employee.account', 'ADJUST', 'HIGH', 0, 1, 1),
       ('employee.pay', 'صرف نقدية لموظف من الخزينة',
        'EMPLOYEES', 'employee.pay', 'CREATE', 'HIGH', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key        = VALUES(module_key),
                        resource_key      = VALUES(resource_key),
                        action_key        = VALUES(action_key),
                        risk_level        = VALUES(risk_level),
                        system_permission = 1,
                        enabled           = 1;

-- الكشف يكشف ما يُدفع للموظف، فيذهب لمن يرى الرواتب اليوم - لا لكل من يفتح شاشة الموظفين.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'employees.show.salary'
         JOIN auth_permission granted ON granted.permission_key = 'employee.account.show';

-- تقرير أن على الموظف مئتين من نفس صنف ثقة تحديد راتبه.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id
                  AND held.permission_key = 'employee.salary.change'
         JOIN auth_permission granted ON granted.permission_key = 'employee.account.adjust';

-- ومن يستطيع تسجيل مصروف يستطيع اليوم صرف راتب - لأن الصرف مصروف. فلا يفقد ذلك.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'expenses.create'
         JOIN auth_permission granted ON granted.permission_key = 'employee.pay';

DROP PROCEDURE IF EXISTS add_employee_ledger_index;
