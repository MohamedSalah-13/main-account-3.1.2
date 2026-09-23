-- =====================================================================
-- V82 - عملة الطرف: عميل أو مورد يتعامل بعملة. المرحلة ج من docs/currency-plan.md §14.
--
-- ### الدفاتر لا تتحرك
--
-- كل عمود مبلغ قائم يبقى بالعملة الأساسية ويبقى معناه: `first_balance`، و`paid`/`purchase` في حسابات
-- الأطراف، و`total`/`discount` وعمود النقد في رؤوس المستندات الأربعة. منها تُحسب views الحسابات
-- والخزائن والأرباح كما اليوم. الأعمدة الجديدة **بجوارها**: المبلغ بعملة الطرف، والسعر الذي يربط
-- الاثنين منسوخًا على الصف (ق-٤). فالطرف الأجنبي له رصيدان من view واحد: بعملته، وقيمته الدفترية.
--
-- ### NULL هي الأساسية
--
-- `currency_id` فارغ لكل طرف قائم، وكذلك الأعمدة الأجنبية على كل حركة ومستند قائمين: لا يتغير صف واحد.
-- والطرف الأجنبي يسمّي عملته ولا يسمّي الأساسية صراحة أبدًا (الخدمة تكتبها NULL).
--
-- ### ما يكتبه كل جدول
--
-- - الطرف: عملته، ورصيده الافتتاحي بها وسعره (`first_balance` قيمته بالأساسية، ق-ج٢). وحدّه الائتماني
--   (`custom.limit_num`) يُكتب بعملته - لا عمود جديد له: عملة الطرف تُختار قبل أول حركة، مع الحد في
--   النموذج نفسه، ولا تتغير بعدها.
-- - الحركة على حسابه: المدفوع والإشعار بعملته والسعر (ق-ج٤، ق-ج٥).
-- - رأس المستند: الإجمالي والخصم والمدفوع بعملة الطرف والسعر (ق-ج٣). `paid_foreign` هو المدفوع أيًا
--   كان اسم عمود النقد في جدوله (`paid_up`، `paid_to_treasury`، `paid_from_treasury`).
--
-- ### القيود
--
-- مفتاح إلى `currency` **بلا** ON DELETE: عملة يتعامل بها طرف لا تُحذف (`DeleteRegistry.CURRENCIES`).
-- وCHECK يربط الأعمدة معًا بـ IS NULL / IS NOT NULL صريحة في كل فرع - CHECK في MySQL يمرّ حين يكون
-- NULL (درس V70). الأعمدة الأجنبية على الحركة والمستند تُكتب معًا أو لا تُكتب.
--
-- الإجراءان المساعدان معرَّفان هنا ومحذوفان في آخر الملف (MigrationHelperProcedureTest).
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS v82_add_column_if_missing$$
CREATE PROCEDURE v82_add_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64), IN col_def TEXT)
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
-- 1) الطرف: عملته، ورصيده الافتتاحي بها وسعره
-- ---------------------------------------------------------------------

CALL v82_add_column_if_missing('custom', 'currency_id',
    'INT NULL COMMENT ''عملة العميل؛ NULL = العملة الأساسية. لا تتغير بعد أول حركة''');
CALL v82_add_column_if_missing('custom', 'opening_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''الرصيد الافتتاحي بعملة العميل الأجنبية؛ first_balance قيمته بالأساسية''');
CALL v82_add_column_if_missing('custom', 'opening_rate',
    'DECIMAL(20, 10) NULL COMMENT ''السعر الذي حُسب به first_balance من opening_foreign، منسوخًا''');

CALL add_constraint_if_missing('custom', 'custom_currency_id_fk',
    'FOREIGN KEY (currency_id) REFERENCES currency (id)');

CALL add_constraint_if_missing('custom', 'custom_opening_foreign_chk',
    'CHECK ((currency_id IS NULL AND opening_foreign IS NULL AND opening_rate IS NULL)
            OR (currency_id IS NOT NULL AND opening_foreign IS NOT NULL
                AND ((opening_foreign = 0 AND opening_rate IS NULL)
                     OR (opening_foreign <> 0 AND opening_rate IS NOT NULL AND opening_rate > 0))))');

CALL v82_add_column_if_missing('suppliers', 'currency_id',
    'INT NULL COMMENT ''عملة المورد؛ NULL = العملة الأساسية. لا تتغير بعد أول حركة''');
CALL v82_add_column_if_missing('suppliers', 'opening_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''الرصيد الافتتاحي بعملة المورد الأجنبية؛ first_balance قيمته بالأساسية''');
CALL v82_add_column_if_missing('suppliers', 'opening_rate',
    'DECIMAL(20, 10) NULL COMMENT ''السعر الذي حُسب به first_balance من opening_foreign، منسوخًا''');

CALL add_constraint_if_missing('suppliers', 'suppliers_currency_id_fk',
    'FOREIGN KEY (currency_id) REFERENCES currency (id)');

CALL add_constraint_if_missing('suppliers', 'suppliers_opening_foreign_chk',
    'CHECK ((currency_id IS NULL AND opening_foreign IS NULL AND opening_rate IS NULL)
            OR (currency_id IS NOT NULL AND opening_foreign IS NOT NULL
                AND ((opening_foreign = 0 AND opening_rate IS NULL)
                     OR (opening_foreign <> 0 AND opening_rate IS NOT NULL AND opening_rate > 0))))');

-- ---------------------------------------------------------------------
-- 2) الحركة على حساب الطرف: المدفوع والإشعار بعملته وسعره
-- ---------------------------------------------------------------------

CALL v82_add_column_if_missing('customers_accounts', 'paid_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''المدفوع بعملة العميل الأجنبية؛ paid قيمته بالأساسية''');
CALL v82_add_column_if_missing('customers_accounts', 'purchase_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''الإشعار بعملة العميل الأجنبية؛ purchase قيمته بالأساسية''');
CALL v82_add_column_if_missing('customers_accounts', 'exchange_rate',
    'DECIMAL(20, 10) NULL COMMENT ''سعر يوم الحركة من شاشة العملات، منسوخًا''');

CALL add_constraint_if_missing('customers_accounts', 'customers_accounts_foreign_chk',
    'CHECK ((paid_foreign IS NULL AND purchase_foreign IS NULL AND exchange_rate IS NULL)
            OR (paid_foreign IS NOT NULL AND purchase_foreign IS NOT NULL
                AND exchange_rate IS NOT NULL AND exchange_rate > 0))');

CALL v82_add_column_if_missing('suppliers_accounts', 'paid_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''المدفوع بعملة المورد الأجنبية؛ paid قيمته بالأساسية''');
CALL v82_add_column_if_missing('suppliers_accounts', 'purchase_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''الإشعار بعملة المورد الأجنبية؛ purchase قيمته بالأساسية''');
CALL v82_add_column_if_missing('suppliers_accounts', 'exchange_rate',
    'DECIMAL(20, 10) NULL COMMENT ''سعر يوم الحركة من شاشة العملات، منسوخًا''');

CALL add_constraint_if_missing('suppliers_accounts', 'suppliers_accounts_foreign_chk',
    'CHECK ((paid_foreign IS NULL AND purchase_foreign IS NULL AND exchange_rate IS NULL)
            OR (paid_foreign IS NOT NULL AND purchase_foreign IS NOT NULL
                AND exchange_rate IS NOT NULL AND exchange_rate > 0))');

-- ---------------------------------------------------------------------
-- 3) رأس المستند: إجماليه وخصمه ومدفوعه بعملة الطرف، وسعر يومه
-- ---------------------------------------------------------------------

CALL v82_add_column_if_missing('total_sales', 'exchange_rate',
    'DECIMAL(20, 10) NULL COMMENT ''سعر عملة العميل في يوم الفاتورة، منسوخًا؛ NULL لعميل بالأساسية''');
CALL v82_add_column_if_missing('total_sales', 'total_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''total بعملة العميل''');
CALL v82_add_column_if_missing('total_sales', 'discount_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''discount بعملة العميل''');
CALL v82_add_column_if_missing('total_sales', 'paid_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''paid_up بعملة العميل''');

CALL add_constraint_if_missing('total_sales', 'total_sales_foreign_chk',
    'CHECK ((exchange_rate IS NULL AND total_foreign IS NULL AND discount_foreign IS NULL
             AND paid_foreign IS NULL)
            OR (exchange_rate IS NOT NULL AND exchange_rate > 0 AND total_foreign IS NOT NULL
                AND discount_foreign IS NOT NULL AND paid_foreign IS NOT NULL))');

CALL v82_add_column_if_missing('total_sales_re', 'exchange_rate',
    'DECIMAL(20, 10) NULL COMMENT ''سعر عملة العميل، منسوخًا: سعر الفاتورة المرتجعة أو سعر يوم المرتجع الحر''');
CALL v82_add_column_if_missing('total_sales_re', 'total_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''total بعملة العميل''');
CALL v82_add_column_if_missing('total_sales_re', 'discount_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''discount بعملة العميل''');
CALL v82_add_column_if_missing('total_sales_re', 'paid_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''paid_from_treasury بعملة العميل''');

CALL add_constraint_if_missing('total_sales_re', 'total_sales_re_foreign_chk',
    'CHECK ((exchange_rate IS NULL AND total_foreign IS NULL AND discount_foreign IS NULL
             AND paid_foreign IS NULL)
            OR (exchange_rate IS NOT NULL AND exchange_rate > 0 AND total_foreign IS NOT NULL
                AND discount_foreign IS NOT NULL AND paid_foreign IS NOT NULL))');

CALL v82_add_column_if_missing('total_buy', 'exchange_rate',
    'DECIMAL(20, 10) NULL COMMENT ''سعر عملة المورد في يوم الفاتورة، منسوخًا؛ NULL لمورد بالأساسية''');
CALL v82_add_column_if_missing('total_buy', 'total_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''total بعملة المورد''');
CALL v82_add_column_if_missing('total_buy', 'discount_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''discount بعملة المورد''');
CALL v82_add_column_if_missing('total_buy', 'paid_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''paid_up بعملة المورد''');

CALL add_constraint_if_missing('total_buy', 'total_buy_foreign_chk',
    'CHECK ((exchange_rate IS NULL AND total_foreign IS NULL AND discount_foreign IS NULL
             AND paid_foreign IS NULL)
            OR (exchange_rate IS NOT NULL AND exchange_rate > 0 AND total_foreign IS NOT NULL
                AND discount_foreign IS NOT NULL AND paid_foreign IS NOT NULL))');

CALL v82_add_column_if_missing('total_buy_re', 'exchange_rate',
    'DECIMAL(20, 10) NULL COMMENT ''سعر عملة المورد، منسوخًا: سعر الفاتورة المرتجعة أو سعر يوم المرتجع الحر''');
CALL v82_add_column_if_missing('total_buy_re', 'total_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''total بعملة المورد''');
CALL v82_add_column_if_missing('total_buy_re', 'discount_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''discount بعملة المورد''');
CALL v82_add_column_if_missing('total_buy_re', 'paid_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''paid_to_treasury بعملة المورد''');

CALL add_constraint_if_missing('total_buy_re', 'total_buy_re_foreign_chk',
    'CHECK ((exchange_rate IS NULL AND total_foreign IS NULL AND discount_foreign IS NULL
             AND paid_foreign IS NULL)
            OR (exchange_rate IS NOT NULL AND exchange_rate > 0 AND total_foreign IS NOT NULL
                AND discount_foreign IS NOT NULL AND paid_foreign IS NOT NULL))');

DROP PROCEDURE IF EXISTS v82_add_column_if_missing;
DROP PROCEDURE IF EXISTS add_constraint_if_missing;
