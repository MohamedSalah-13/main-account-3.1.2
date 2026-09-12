-- =====================================================================
-- V59 - مسيَّر الرواتب: حساب شهري يُجمَّد عند الاعتماد.
--
-- العقد في docs/employees-plan.md §5 (المرحلة ج) وق-٣ وق-٤ وق-٥ وق-٧.
--
-- ### ما يفعله المسيَّر، وما لا يفعله
--
-- يحسب لكل موظف ما استحقه عن شهر، ثم - عند الاعتماد - يكتب ذلك في `employee_ledger`.
-- **ولا يصرف نقدا بنفسه**: الصرف يمرّ بـ`expenses_details` كما يمرّ كل جنيه آخر
-- (V58)، صفا لكل موظف لا صفا إجماليا، وإلا لا يعرف كشف الموظف نصيبه منه.
--
-- ### ق-٥: السلفة تُخصم مرة واحدة، يوم صرفها - وهذا الملف يحترمها بعمود اسمه يقولها
--
-- السلفة نقد خرج يوم خروجه، فهي مدينة على الموظف من لحظتها. فلو طرحها المسيَّر مرة
-- أخرى من الاستحقاق، لحُمِّل الموظف بضعفها ولقال رصيده إنه مدين بما ليس عليه.
--
-- لذلك `payroll_line.advances_outstanding` **إعلامي للطباعة وحده**، وله COMMENT يقول
-- ذلك في القاعدة نفسها. يقرأه المحاسب على القسيمة - «هذا مستحقه، وهذا ما سبق أن أخذه،
-- فادفع الفرق» - ولا يدخل في `net_pay` ولا في أي صف دفتر. والذي يدخل الدفتر هو
-- **الاستحقاق كاملا**، والذي يخرج من الخزينة هو ما يقرره المحاسب فعلا. الرصيد يوازن
-- نفسه لأن الطرفين مسجَّلان مرة واحدة كلٌّ في مكانه.
--
-- ### ما يكتبه الاعتماد في الدفتر، وبأي أنواع
--
-- سطران على الأكثر لكل موظف، وكلٌّ بنوعه الحقيقي - لا صف واحد بالصافي:
--
--   * `ENTITLEMENT` بمقدار `earned` (الأساسي + البدلات + العمولة) - ما كسبه؛
--   * `DEDUCTION` بمقدار (الخصومات + خصم الغياب) إن وُجد - ما خُصم منه.
--
-- وصف واحد بالصافي كان سيجعل «استحقاق» تعني أحيانا الاستحقاق وأحيانا ما تبقى منه،
-- وهو بالضبط صنف العطل الذي أزالته V58 حين جعلت الاتجاه في النوع لا في الإشارة.
-- و`net_pay = earned - deductions` بلا CHECK على إشارته: شهر خصومه أكبر من كسبه حالة
-- حقيقية، ورصيد الموظف يقولها عندئذ كما هي.
--
-- ### ق-٧: التجميد شرطي بالحالة لا مطلق
--
-- `payroll_run` يمرّ بـ`DRAFT -> APPROVED -> PAID`، و`CANCELLED` من `DRAFT` وحدها،
-- و`UNIQUE(period_year, period_month)` - مسيَّر واحد للشهر لا اثنان يختلفان عليه.
--
-- والـtriggers في `R__triggers.sql` ترفض `UPDATE`/`DELETE` على `payroll_line` لمسيَّر
-- غادر `DRAFT`. **وللحذف وحده مخرج `@app_bulk_wipe`، وليس للتعديل** - وهو بالضبط الفرق
-- الذي أوقع `V43` حين ظنّ أن الحارسين واحد فحاول تصحيح قيمة مخزَّنة ففشل على قاعدة فيها
-- وردية مغلقة (`CLAUDE.md`، «الورديات»). مسح البيانات يأخذ الصف؛ لا شيء يعدّله.
--
-- ### ق-٤: قواعد التأمين والضريبة جدول لا كود
--
-- `payroll_rule` بنسبة أو مبلغ وتاريخ سريان، **ومزروع بقاعدتين قيمتهما صفر** - لأن
-- النسب تختلف بالدولة وتتغير بالسنة، ورقم مكتوب في Java هو رقم يخصّ بلدا واحدا وسنة
-- واحدة. صفر يعني «لم يُقرَّر بعد»، فلا يُفرض على أحد رقم عند الترقية ولا يتغير حساب
-- أحد بسبب هذه الهجرة.
--
-- ### المفتاحان الأجنبيان اللذان تركهما V58 معلَّقين
--
-- `employee_ledger.payroll_run_id` و`employee_cash_purpose.payroll_run_id` كُتبا بلا
-- قيد لأن الجدول لم يكن موجودا، ومفتاح إلى جدول غير موجود لا يُكتب. صار موجودا، فيُربطان
-- هنا - والقيد هو ما يمنع حذف مسيَّر خلّف أثرا في الدفتر أو في الخزينة.
--
-- ### الإجراءات المساعدة
--
-- معرَّفة هنا ومحذوفة في آخر الملف، للسبب الذي دفعه V55 وV56 وV57 وV58:
-- `add_index_if_missing` يحذفه V1 في سطره 994، و`add_column_if_missing` لا تنشئه هجرة
-- أساسية إطلاقا.
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_payroll_index$$
CREATE PROCEDURE add_payroll_index(IN t_name VARCHAR(64), IN i_name VARCHAR(64),
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

DROP PROCEDURE IF EXISTS add_payroll_constraint_if_missing$$
CREATE PROCEDURE add_payroll_constraint_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64),
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
-- 1) المسيَّر: رأس الشهر
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS payroll_run
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    period_year   SMALLINT                             NOT NULL,
    period_month  TINYINT                              NOT NULL,
    status        VARCHAR(20) DEFAULT 'DRAFT'          NOT NULL,
    notes         VARCHAR(255)                         NULL,
    date_insert   DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id       INT         DEFAULT 1                NOT NULL,
    approved_at   DATETIME                             NULL,
    approved_by   INT                                  NULL,
    paid_at       DATETIME                             NULL,
    paid_by       INT                                  NULL,
    CONSTRAINT payroll_run_period_uk UNIQUE (period_year, period_month),
    CONSTRAINT payroll_run_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT payroll_run_approved_by_fk FOREIGN KEY (approved_by) REFERENCES users (id),
    CONSTRAINT payroll_run_paid_by_fk FOREIGN KEY (paid_by) REFERENCES users (id),
    CONSTRAINT payroll_run_status_chk
        CHECK (status IN ('DRAFT', 'APPROVED', 'PAID', 'CANCELLED')),
    CONSTRAINT payroll_run_month_chk CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT payroll_run_year_chk CHECK (period_year BETWEEN 2000 AND 2200)
);

-- ---------------------------------------------------------------------
-- 2) السطر: موظف واحد في شهر واحد
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS payroll_line
(
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    payroll_run_id       INT                                  NOT NULL,
    employee_id          INT                                  NOT NULL,
    salary_kind          VARCHAR(20)                          NOT NULL,
    rate                 DECIMAL(14, 2)                       NOT NULL,
    worked_days          DECIMAL(6, 2)  DEFAULT 0             NOT NULL,
    absence_days         DECIMAL(6, 2)  DEFAULT 0             NOT NULL,
    worked_hours         DECIMAL(8, 2)  DEFAULT 0             NOT NULL,
    basic                DECIMAL(14, 2) DEFAULT 0             NOT NULL,
    allowances           DECIMAL(14, 2) DEFAULT 0             NOT NULL,
    commission           DECIMAL(14, 2) DEFAULT 0             NOT NULL,
    deductions           DECIMAL(14, 2) DEFAULT 0             NOT NULL,
    absence_deduction    DECIMAL(14, 2) DEFAULT 0             NOT NULL,
    advances_outstanding DECIMAL(14, 2) DEFAULT 0             NOT NULL,
    net_pay              DECIMAL(14, 2) DEFAULT 0             NOT NULL,
    notes                VARCHAR(255)                         NULL,
    date_insert          DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id              INT            DEFAULT 1             NOT NULL,
    CONSTRAINT payroll_line_run_employee_uk UNIQUE (payroll_run_id, employee_id),
    CONSTRAINT payroll_line_payroll_run_id_fk
        FOREIGN KEY (payroll_run_id) REFERENCES payroll_run (id) ON DELETE CASCADE,
    CONSTRAINT payroll_line_employees_id_fk FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT payroll_line_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT payroll_line_kind_chk
        CHECK (salary_kind IN ('MONTHLY', 'DAILY', 'HOURLY', 'COMMISSION')),
    CONSTRAINT payroll_line_amounts_chk
        CHECK (rate >= 0 AND basic >= 0 AND allowances >= 0 AND commission >= 0
            AND deductions >= 0 AND absence_deduction >= 0 AND advances_outstanding >= 0),
    CONSTRAINT payroll_line_days_chk
        CHECK (worked_days >= 0 AND absence_days >= 0 AND worked_hours >= 0)
);

-- السطر يزول بزوال مسيَّره - وهو **جارف عمدا**، فلا يُعلَن في `DeleteRegistry`: القاعدة
-- المكتوبة في CLAUDE.md هي أن يُعلَن ما لا يجرف وحده. ومسيَّر لا يُحذف إلا وهو `DRAFT`،
-- وحينها لم يكتب شيئا في الدفتر بعد.

-- الكشف وقسيمة الراتب يقرآن سطور موظف عبر السنين.
CALL add_payroll_index('payroll_line', 'payroll_line_employee_idx', 'employee_id');

ALTER TABLE payroll_line
    MODIFY COLUMN advances_outstanding DECIMAL(14, 2) DEFAULT 0 NOT NULL
        COMMENT 'إعلامي للطباعة وحده - ما سبق صرفه سلفا. لا يدخل net_pay ولا الدفتر: السلفة خُصمت يوم خروجها (ق-٥)';

ALTER TABLE payroll_line
    MODIFY COLUMN net_pay DECIMAL(14, 2) DEFAULT 0 NOT NULL
        COMMENT 'الكسب ناقص الخصومات. قد يكون سالبا في شهر خصومه أكبر من كسبه، وهي حالة حقيقية';

-- ---------------------------------------------------------------------
-- 3) قواعد التأمين والضريبة - جدول لا كود
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS payroll_rule
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    rule_key       VARCHAR(50)                          NOT NULL,
    description    VARCHAR(255)                         NULL,
    effective_from DATE                                 NOT NULL,
    calc_kind      VARCHAR(20)                          NOT NULL,
    rate           DECIMAL(14, 4) DEFAULT 0             NOT NULL,
    is_active      TINYINT(1)     DEFAULT 1             NOT NULL,
    date_insert    DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id        INT            DEFAULT 1             NOT NULL,
    CONSTRAINT payroll_rule_key_date_uk UNIQUE (rule_key, effective_from),
    CONSTRAINT payroll_rule_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT payroll_rule_calc_chk CHECK (calc_kind IN ('PERCENT', 'AMOUNT')),
    CONSTRAINT payroll_rule_rate_chk CHECK (rate >= 0)
);

-- صفر يعني «لم يُقرَّر بعد». لا يتغير حساب أحد بسبب هذه الهجرة.
INSERT IGNORE INTO payroll_rule(rule_key, description, effective_from, calc_kind, rate, is_active)
VALUES ('INSURANCE', 'التأمينات الاجتماعية - نسبة من الأساسي', '2000-01-01', 'PERCENT', 0, 1),
       ('TAX', 'ضريبة كسب العمل - نسبة من الأساسي', '2000-01-01', 'PERCENT', 0, 1);

-- ---------------------------------------------------------------------
-- 4) بدلات ثابتة على الموظف
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS employee_allowance
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    employee_id    INT                                  NOT NULL,
    allowance_name VARCHAR(50)                          NOT NULL,
    amount         DECIMAL(14, 2)                       NOT NULL,
    effective_from DATE                                 NOT NULL,
    effective_to   DATE                                 NULL,
    is_active      TINYINT(1)     DEFAULT 1             NOT NULL,
    notes          VARCHAR(255)                         NULL,
    date_insert    DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id        INT            DEFAULT 1             NOT NULL,
    CONSTRAINT employee_allowance_uk UNIQUE (employee_id, allowance_name, effective_from),
    CONSTRAINT employee_allowance_employees_id_fk
        FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT employee_allowance_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT employee_allowance_amount_chk CHECK (amount >= 0),
    CONSTRAINT employee_allowance_range_chk
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CALL add_payroll_index('employee_allowance', 'employee_allowance_employee_idx',
                       'employee_id, effective_from');

-- ---------------------------------------------------------------------
-- 5) المفتاحان اللذان تركهما V58 معلَّقين
-- ---------------------------------------------------------------------

CALL add_payroll_constraint_if_missing('employee_ledger', 'employee_ledger_payroll_run_id_fk',
                                       'FOREIGN KEY (payroll_run_id) REFERENCES payroll_run (id)');

CALL add_payroll_constraint_if_missing('employee_cash_purpose',
                                       'employee_cash_purpose_payroll_run_id_fk',
                                       'FOREIGN KEY (payroll_run_id) REFERENCES payroll_run (id)');

ALTER TABLE employee_ledger
    MODIFY COLUMN payroll_run_id INT NULL
        COMMENT 'مسيَّر الرواتب الذي أنشأ الصف. القيد الأجنبي هو ما يمنع حذف مسيَّر خلّف أثرا';

-- ---------------------------------------------------------------------
-- 6) الصلاحيات
-- ---------------------------------------------------------------------
--
-- أربعة مفاتيح، و**الاعتماد والصرف مفتاحان مختلفان عمدا**: من يحسب لا يصرف. النقد يقابله
-- عدّ في الدرج، والاعتماد لا يقابله شيء - وهو نفس التفريق الذي أقامه V55 بين
-- `account.create` و`account.adjust`، وV58 بين `employee.pay` و`employee.account.adjust`.
--
-- وكل مفتاح يُمنح لمن يملك ما يقابله اليوم (V34، V35، V55، V57، V58): لا أحد يفقد قدرة.
-- ولا أحد يملك هذه القدرة اليوم أصلا - لا مسيَّر في النظام - فالمنح هنا اختيار لمن يبدأ
-- بها، لا استعادة لما كان.

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('payroll.show', 'عرض مسيَّر الرواتب',
        'EMPLOYEES', 'payroll', 'SHOW', 'MEDIUM', 0, 1, 1),
       ('payroll.create', 'إنشاء مسيَّر شهر وتعديل سطوره',
        'EMPLOYEES', 'payroll', 'CREATE', 'HIGH', 0, 1, 1),
       ('payroll.approve', 'اعتماد المسيَّر وتجميده وكتابته في دفاتر الموظفين',
        'EMPLOYEES', 'payroll', 'APPROVE', 'CRITICAL', 0, 1, 1),
       ('payroll.pay', 'صرف مسيَّر معتمد من الخزينة',
        'EMPLOYEES', 'payroll', 'PAY', 'CRITICAL', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key        = VALUES(module_key),
                        resource_key      = VALUES(resource_key),
                        action_key        = VALUES(action_key),
                        risk_level        = VALUES(risk_level),
                        system_permission = 1,
                        enabled           = 1;

-- المسيَّر كله أرقام رواتب، فرؤيته لمن يرى الرواتب اليوم.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'employees.show.salary'
         JOIN auth_permission granted ON granted.permission_key = 'payroll.show';

-- وإنشاؤه واعتماده من صنف ثقة تحديد راتب.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id
                  AND held.permission_key = 'employee.salary.change'
         JOIN auth_permission granted
              ON granted.permission_key IN ('payroll.create', 'payroll.approve');

-- والصرف لمن يصرف اليوم - فهو نفس الفعل، بمصدر مختلف.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'employee.pay'
         JOIN auth_permission granted ON granted.permission_key = 'payroll.pay';

DROP PROCEDURE IF EXISTS add_payroll_index;
DROP PROCEDURE IF EXISTS add_payroll_constraint_if_missing;
