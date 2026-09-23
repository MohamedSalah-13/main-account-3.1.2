-- =====================================================================
-- V83 - المستند بعملة أجنبية. المرحلة د من docs/currency-plan.md §15.
--
-- ### الاتجاه انقلب، والدفاتر لم تتحرك
--
-- في V82 كُتبت فاتورة الطرف الأجنبي بالأساسية وتُرجمت إلى عملته على رأسها. هنا تُكتب بعملته: الأسعار
-- والخصومات والنقد كما في ورقة المورد، والأساسية مشتقة منها بسعر منسوخ (ق-د١). كل عمود أساسي قائم يبقى
-- بمعناه - سعر السطر وخصمه، وإجمالي الرأس وخصمه ونقده - ومنه تُحسب views الحسابات والأرباح والمخزن كما
-- اليوم. الجديد **بجوارها**:
--
-- - الرأس: `currency_id` يقول بأي عملة كُتب المستند؛ NULL = كُتب بالأساسية (مستند طرف بالأساسية، أو مستند
--   المرحلة ج المترجم). وأرقامه بعملته في أعمدة V82 نفسها (`total_foreign`، `discount_foreign`،
--   `paid_foreign`، `exchange_rate`): لمستند المرحلة د هي ما كُتب، لا ترجمة.
-- - السطر: `price_foreign` و`discount_foreign`، سعر الوحدة وخصم السطر كما كُتبا (ق-د٣). سعر السطر وخصمه
--   بالأساسية هما المكتوب × السعر مقرَّبًا، فالتكلفة والربح والرصيد المخزني لا تعرف شيئًا عن العملة.
--
-- ### NULL هي الأساسية
--
-- الأعمدة الأربعة فارغة لكل مستند وسطر قائمين: لا يتغير صف واحد.
--
-- ### القيود
--
-- مفتاح إلى `currency` **بلا** ON DELETE: عملة كُتب بها مستند لا تُحذف (`DeleteRegistry.CURRENCIES`).
-- وCHECK بـ IS NULL / IS NOT NULL صريحة في كل فرع - CHECK في MySQL يمرّ حين يكون NULL (درس V70): عملة على
-- الرأس بلا سعر مرفوضة، وعمودا السطر معًا أو لا شيء.
--
-- الإجراءان المساعدان معرَّفان هنا ومحذوفان في آخر الملف (MigrationHelperProcedureTest).
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS v83_add_column_if_missing$$
CREATE PROCEDURE v83_add_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64), IN col_def TEXT)
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

DROP PROCEDURE IF EXISTS add_constraint_if_missing$$
CREATE PROCEDURE add_constraint_if_missing(IN t_name VARCHAR(64), IN k_name VARCHAR(64), IN k_def TEXT)
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

DELIMITER ;

-- ---------------------------------------------------------------------
-- 1) رأس المستند: العملة التي كُتب بها
-- ---------------------------------------------------------------------

CALL v83_add_column_if_missing('total_sales', 'currency_id',
    'INT NULL COMMENT ''العملة التي كُتبت بها الفاتورة؛ NULL = الأساسية (ومنها مستند مترجم في V82)''');
CALL add_constraint_if_missing('total_sales', 'total_sales_currency_id_fk',
    'FOREIGN KEY (currency_id) REFERENCES currency (id)');
CALL add_constraint_if_missing('total_sales', 'total_sales_currency_chk',
    'CHECK (currency_id IS NULL OR (exchange_rate IS NOT NULL AND exchange_rate > 0))');

CALL v83_add_column_if_missing('total_sales_re', 'currency_id',
    'INT NULL COMMENT ''العملة التي كُتب بها المرتجع؛ NULL = الأساسية''');
CALL add_constraint_if_missing('total_sales_re', 'total_sales_re_currency_id_fk',
    'FOREIGN KEY (currency_id) REFERENCES currency (id)');
CALL add_constraint_if_missing('total_sales_re', 'total_sales_re_currency_chk',
    'CHECK (currency_id IS NULL OR (exchange_rate IS NOT NULL AND exchange_rate > 0))');

CALL v83_add_column_if_missing('total_buy', 'currency_id',
    'INT NULL COMMENT ''العملة التي كُتبت بها الفاتورة؛ NULL = الأساسية (ومنها مستند مترجم في V82)''');
CALL add_constraint_if_missing('total_buy', 'total_buy_currency_id_fk',
    'FOREIGN KEY (currency_id) REFERENCES currency (id)');
CALL add_constraint_if_missing('total_buy', 'total_buy_currency_chk',
    'CHECK (currency_id IS NULL OR (exchange_rate IS NOT NULL AND exchange_rate > 0))');

CALL v83_add_column_if_missing('total_buy_re', 'currency_id',
    'INT NULL COMMENT ''العملة التي كُتب بها المرتجع؛ NULL = الأساسية''');
CALL add_constraint_if_missing('total_buy_re', 'total_buy_re_currency_id_fk',
    'FOREIGN KEY (currency_id) REFERENCES currency (id)');
CALL add_constraint_if_missing('total_buy_re', 'total_buy_re_currency_chk',
    'CHECK (currency_id IS NULL OR (exchange_rate IS NOT NULL AND exchange_rate > 0))');

-- ---------------------------------------------------------------------
-- 2) السطر: سعر الوحدة وخصم السطر كما كُتبا بعملة المستند
-- ---------------------------------------------------------------------

CALL v83_add_column_if_missing('sales', 'price_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''سعر الوحدة بعملة الفاتورة كما كُتب؛ price قيمته بالأساسية''');
CALL v83_add_column_if_missing('sales', 'discount_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''خصم السطر بعملة الفاتورة كما كُتب''');
CALL add_constraint_if_missing('sales', 'sales_foreign_chk',
    'CHECK ((price_foreign IS NULL AND discount_foreign IS NULL)
            OR (price_foreign IS NOT NULL AND discount_foreign IS NOT NULL))');

CALL v83_add_column_if_missing('sales_re', 'price_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''سعر الوحدة بعملة المرتجع كما كُتب؛ price قيمته بالأساسية''');
CALL v83_add_column_if_missing('sales_re', 'discount_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''خصم السطر بعملة المرتجع كما كُتب''');
CALL add_constraint_if_missing('sales_re', 'sales_re_foreign_chk',
    'CHECK ((price_foreign IS NULL AND discount_foreign IS NULL)
            OR (price_foreign IS NOT NULL AND discount_foreign IS NOT NULL))');

CALL v83_add_column_if_missing('purchase', 'price_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''سعر الوحدة بعملة الفاتورة كما كُتب؛ price قيمته بالأساسية''');
CALL v83_add_column_if_missing('purchase', 'discount_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''خصم السطر بعملة الفاتورة كما كُتب''');
CALL add_constraint_if_missing('purchase', 'purchase_foreign_chk',
    'CHECK ((price_foreign IS NULL AND discount_foreign IS NULL)
            OR (price_foreign IS NOT NULL AND discount_foreign IS NOT NULL))');

CALL v83_add_column_if_missing('purchase_re', 'price_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''سعر الوحدة بعملة المرتجع كما كُتب؛ price قيمته بالأساسية''');
CALL v83_add_column_if_missing('purchase_re', 'discount_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''خصم السطر بعملة المرتجع كما كُتب''');
CALL add_constraint_if_missing('purchase_re', 'purchase_re_foreign_chk',
    'CHECK ((price_foreign IS NULL AND discount_foreign IS NULL)
            OR (price_foreign IS NOT NULL AND discount_foreign IS NOT NULL))');

DROP PROCEDURE IF EXISTS v83_add_column_if_missing;
DROP PROCEDURE IF EXISTS add_constraint_if_missing;
