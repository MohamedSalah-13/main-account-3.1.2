-- =====================================================================
-- V66 - الموازنة والمصروفات الدورية (المرحلة ج من docs/expenses-plan.md §5).
--
-- ### 1. الموازنة
--
-- `expense_budget` صف لكل (بند، سنة، شهر). `month NULL` تعني موازنة سنوية، وفريد واحد يحكم
-- الاثنين معا - MySQL يعامل NULL في الفهرس الفريد كقيم مختلفة، فالفريد وحده لا يمنع موازنتين
-- سنويتين لنفس البند. ولهذا `month_key` عمود محسوب مخزَّن = COALESCE(month, 0)، والفريد عليه:
-- صفر ليس شهرا، فلا يصطدم بموازنة شهر حقيقي، ولا يسمح بموازنتين سنويتين.
--
-- ولا موازنة على بند موقوف أو محذوف: المفتاح الأجنبي على `expenses` بلا CASCADE عن قصد -
-- حذف بند له موازنة يُرفض، وهو ما يقوله DeleteRegistry للمستخدم بدل أن تختفي الموازنة صامتة.
--
-- ### 2. المصروفات الدورية
--
-- `expense_recurring` قالب: بند وخزينة ومبلغ ودورية ويوم من الشهر. **لا يسجّل شيئا بنفسه**
-- (§5.2): المال يخرج من درج بيد شخص، و`ShiftGate` يطلب وردية مفتوحة لذلك الشخص على تلك
-- الخزينة - ولا شخص في مهمة مجدولة. القالب يذكّر فقط، والتسجيل يمر بشاشة الإدخال كأي مصروف.
--
-- و`expenses_details.recurring_id` هو الربط: NULL لكل صف مسجَّل بيد، ومفتاح أجنبي
-- **بلا CASCADE وبـ ON DELETE SET NULL** - حذف القالب لا يمسّ ما سُجِّل منه، لأن تلك المصروفات
-- مال خرج فعلا من الخزينة ولا علاقة لبقائها ببقاء القالب. ومن هنا يعرف المنبّه «مستحق»:
-- لا صف في تلك الفترة يحمل معرّف هذا القالب.
--
-- ### 3. ما لا يتحرك
--
-- لا صف في `expenses_details` يتغيّر: العمود الجديد يُضاف NULL للجميع. رصيد كل خزينة، وعمود
-- المصروفات في قائمة الأرباح (قرار م-٢: كما هو)، وكل تقارير المرحلة ب تقرأ نفس الأرقام بعدها.
--
-- ### الإجراءات المساعدة
--
-- معرَّفة هنا ومحذوفة في آخر الملف (MigrationHelperProcedureTest)، و`add_constraint_if_missing`
-- بهذا الاسم لأن `SchemaForeignKeys` يقرأ منه المفاتيح المضافة بعد إنشاء الجدول.
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_budget_column_if_missing$$
CREATE PROCEDURE add_budget_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64),
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

DROP PROCEDURE IF EXISTS add_budget_index_if_missing$$
CREATE PROCEDURE add_budget_index_if_missing(IN t_name VARCHAR(64), IN i_name VARCHAR(64),
                                             IN i_cols TEXT)
BEGIN
    DECLARE idx_exists INT;

    SELECT COUNT(*)
    INTO idx_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = t_name
      AND INDEX_NAME = i_name;

    IF idx_exists = 0 THEN
        SET @query = CONCAT('CREATE INDEX ', i_name, ' ON ', t_name, ' (', i_cols, ')');
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS add_constraint_if_missing$$
CREATE PROCEDURE add_constraint_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64),
                                           IN c_def TEXT)
BEGIN
    DECLARE con_exists INT;

    SELECT COUNT(*)
    INTO con_exists
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = t_name
      AND CONSTRAINT_NAME = c_name;

    IF con_exists = 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' ADD CONSTRAINT ', c_name, ' ', c_def);
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- 1) الموازنة
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS expense_budget
(
    id         INT AUTO_INCREMENT PRIMARY KEY,
    heading_id INT            NOT NULL,
    year       SMALLINT       NOT NULL,
    month      TINYINT        NULL COMMENT 'شهر 1-12، أو NULL لموازنة سنوية',
    month_key  TINYINT AS (COALESCE(month, 0)) STORED COMMENT 'الفريد يقرأ هذا: صفر ليس شهرا',
    amount     DECIMAL(15, 2) NOT NULL,
    notes      VARCHAR(255)   NULL,
    user_id    INT            NOT NULL,
    created_at TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT expense_budget_amount_chk CHECK (amount > 0),
    CONSTRAINT expense_budget_year_chk CHECK (year BETWEEN 2000 AND 2100),
    CONSTRAINT expense_budget_month_chk CHECK (month IS NULL OR month BETWEEN 1 AND 12),
    CONSTRAINT expense_budget_uk UNIQUE (heading_id, year, month_key)
) ENGINE = InnoDB;

CALL add_constraint_if_missing('expense_budget', 'expense_budget_heading_fk',
                               'FOREIGN KEY (heading_id) REFERENCES expenses (id)');
CALL add_constraint_if_missing('expense_budget', 'expense_budget_user_fk',
                               'FOREIGN KEY (user_id) REFERENCES users (id)');
CALL add_budget_index_if_missing('expense_budget', 'expense_budget_year_idx', 'year, month_key');

-- ---------------------------------------------------------------------
-- 2) المصروفات الدورية
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS expense_recurring
(
    id           INT AUTO_INCREMENT PRIMARY KEY,
    heading_id   INT            NOT NULL,
    treasury_id  INT            NOT NULL,
    amount       DECIMAL(15, 2) NOT NULL,
    payee        VARCHAR(150)   NULL,
    notes        VARCHAR(255)   NULL,
    frequency    VARCHAR(12)    NOT NULL COMMENT 'MONTHLY / QUARTERLY / YEARLY',
    day_of_month TINYINT        NOT NULL DEFAULT 1 COMMENT 'يوم الاستحقاق؛ 31 في شهر قصير يقع على آخره',
    start_date   DATE           NOT NULL,
    end_date     DATE           NULL,
    is_active    TINYINT(1)     NOT NULL DEFAULT 1,
    user_id      INT            NOT NULL,
    created_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT expense_recurring_amount_chk CHECK (amount > 0),
    CONSTRAINT expense_recurring_day_chk CHECK (day_of_month BETWEEN 1 AND 31),
    CONSTRAINT expense_recurring_frequency_chk CHECK (frequency IN ('MONTHLY', 'QUARTERLY', 'YEARLY')),
    CONSTRAINT expense_recurring_period_chk CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT expense_recurring_active_chk CHECK (is_active IN (0, 1))
) ENGINE = InnoDB;

CALL add_constraint_if_missing('expense_recurring', 'expense_recurring_heading_fk',
                               'FOREIGN KEY (heading_id) REFERENCES expenses (id)');
CALL add_constraint_if_missing('expense_recurring', 'expense_recurring_treasury_fk',
                               'FOREIGN KEY (treasury_id) REFERENCES treasury (id)');
CALL add_constraint_if_missing('expense_recurring', 'expense_recurring_user_fk',
                               'FOREIGN KEY (user_id) REFERENCES users (id)');
CALL add_budget_index_if_missing('expense_recurring', 'expense_recurring_active_idx', 'is_active, heading_id');

-- الربط: صف سُجِّل من قالب يحمل معرّفه. NULL لكل ما سُجِّل بيد، واليوم لكل صف موجود.
CALL add_budget_column_if_missing('expenses_details', 'recurring_id',
                                  'INT NULL COMMENT ''القالب الدوري الذي سُجِّل منه، أو NULL''');
CALL add_constraint_if_missing('expenses_details', 'expenses_details_recurring_fk',
                               'FOREIGN KEY (recurring_id) REFERENCES expense_recurring (id) ON DELETE SET NULL');
CALL add_budget_index_if_missing('expenses_details', 'expenses_details_recurring_idx', 'recurring_id, date');

-- ---------------------------------------------------------------------
-- 3) الصلاحيات
-- ---------------------------------------------------------------------
--
-- الوصف خمسون حرفا على الأكثر: auth_permission.description هو VARCHAR(50) من V1، ومسودة V65
-- الأولى فشلت على أول قاعدة رُحِّلت إليها بسبب ذلك.

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('expenses.budget.manage', 'وضع موازنة لبنود المصروفات وتعديلها',
        'EXPENSES', 'expenses.budget', 'MANAGE', 'CRITICAL', 0, 1, 1),
       ('expenses.recurring.manage', 'إدارة المصروفات الدورية وتذكيراتها',
        'EXPENSES', 'expenses.recurring', 'MANAGE', 'CRITICAL', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key        = VALUES(module_key),
                        resource_key      = VALUES(resource_key),
                        action_key        = VALUES(action_key),
                        risk_level        = VALUES(risk_level),
                        system_permission = 1,
                        enabled           = 1;

-- من يدير البنود هو أقرب من يملك هذين اليوم: البند والموازنة والقالب كلها إعداد للمصروفات،
-- لا صرف لها. على نمط V34 وV55 وV58 وV64 وV65: لا أحد يفقد قدرة عند الترقية، ومن لا يُراد
-- له وضع موازنة تُسحب منه بعدها.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'expenses.headings.update'
         JOIN auth_permission granted ON granted.permission_key = 'expenses.budget.manage';

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id AND held.permission_key = 'expenses.headings.update'
         JOIN auth_permission granted ON granted.permission_key = 'expenses.recurring.manage';

DROP PROCEDURE IF EXISTS add_budget_column_if_missing;
DROP PROCEDURE IF EXISTS add_budget_index_if_missing;
DROP PROCEDURE IF EXISTS add_constraint_if_missing;
