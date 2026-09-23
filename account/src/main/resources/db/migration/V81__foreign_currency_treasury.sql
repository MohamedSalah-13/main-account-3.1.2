-- =====================================================================
-- V81 - خزينة بعملة أجنبية. المرحلة ب من docs/currency-plan.md §11.
--
-- ### الدفاتر لا تتحرك
--
-- كل عمود مبلغ قائم يبقى بالعملة الأساسية ويبقى معناه: `treasury.amount` و
-- `treasury_deposit_expenses.amount` و`treasury_transfers.amount`، ومنها يُحسب
-- `treasury_current_balance` كما اليوم. الأعمدة الجديدة **بجوارها**: ما كانت الحركة بعملة الخزينة،
-- والسعر الذي حُسبت به القيمة الأساسية منسوخًا على الصف (ق-٤). فخزينة الدولار لها رصيدان من view
-- واحد: بعملتها، وقيمتها الدفترية بالأساسية.
--
-- ### NULL هي الأساسية
--
-- `treasury.currency_id` فارغ لكل خزينة قائمة، وكذلك أعمدة المبلغ الأجنبي على كل حركة قائمة: لا
-- يتغير صف واحد، ولا تحتاج الترقية أن تعرف أي عملة هي الأساسية. والخزينة الأجنبية تسمّي عملتها ولا
-- تسمّي الأساسية صراحة أبدًا (الخدمة تكتبها NULL)، فتصحيح الأساسية قبل أول سعر لا يلمس الخزائن.
--
-- ### القيود
--
-- مفتاح إلى `currency` **بلا** ON DELETE: عملة تعمل بها خزينة لا تُحذف (`DeleteRegistry.CURRENCIES`).
-- وCHECK يربط الأعمدة معًا بـ IS NULL / IS NOT NULL صريحة في كل فرع - CHECK في MySQL يمرّ حين يكون
-- NULL (درس V70). الرصيد الافتتاحي لخزينة أجنبية مطلوب (صفر مسموح)، وسعره مطلوب ما دام غير صفر.
--
-- ما ليس هنا عمدًا: عمود أجنبي على الفواتير والتحصيل والمصروفات - تلك الصفوف تُرفض وجهتها خزينة
-- أجنبية حتى المرحلتين ج ود (ق-ب٦). ولا شيء عن الورديات: خزينة بلا صف في `shift_treasury_policy`
-- لا تُتابَع، والخدمة ترفض متابعة خزينة أجنبية (ق-ب٣).
--
-- الإجراءان المساعدان معرَّفان هنا ومحذوفان في آخر الملف (MigrationHelperProcedureTest).
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS v81_add_column_if_missing$$
CREATE PROCEDURE v81_add_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64), IN col_def TEXT)
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
-- 1) الخزينة: عملتها، ورصيدها الافتتاحي بها وسعره
-- ---------------------------------------------------------------------

CALL v81_add_column_if_missing('treasury', 'currency_id',
    'INT NULL COMMENT ''عملة الخزينة؛ NULL = العملة الأساسية. لا تتغير بعد أول حركة''');
CALL v81_add_column_if_missing('treasury', 'opening_foreign',
    'DECIMAL(18, 3) NULL COMMENT ''الرصيد الافتتاحي بعملة الخزينة الأجنبية؛ amount قيمته بالأساسية''');
CALL v81_add_column_if_missing('treasury', 'opening_rate',
    'DECIMAL(20, 10) NULL COMMENT ''السعر الذي حُسب به amount من opening_foreign، منسوخًا''');

CALL add_constraint_if_missing('treasury', 'treasury_currency_id_fk',
    'FOREIGN KEY (currency_id) REFERENCES currency (id)');

CALL add_constraint_if_missing('treasury', 'treasury_opening_foreign_chk',
    'CHECK ((currency_id IS NULL AND opening_foreign IS NULL AND opening_rate IS NULL)
            OR (currency_id IS NOT NULL AND opening_foreign IS NOT NULL
                AND ((opening_foreign = 0 AND opening_rate IS NULL)
                     OR (opening_foreign <> 0 AND opening_rate IS NOT NULL AND opening_rate > 0))))');

-- ---------------------------------------------------------------------
-- 2) الإيداع والسحب: المبلغ بعملة الخزينة وسعره
-- ---------------------------------------------------------------------

CALL v81_add_column_if_missing('treasury_deposit_expenses', 'foreign_amount',
    'DECIMAL(18, 3) NULL COMMENT ''المبلغ بعملة الخزينة الأجنبية؛ amount قيمته بالأساسية. NULL لخزينة الأساسية''');
CALL v81_add_column_if_missing('treasury_deposit_expenses', 'exchange_rate',
    'DECIMAL(20, 10) NULL COMMENT ''سعر يوم الحركة من شاشة العملات، منسوخًا''');

CALL add_constraint_if_missing('treasury_deposit_expenses', 'treasury_deposit_expenses_foreign_chk',
    'CHECK ((foreign_amount IS NULL AND exchange_rate IS NULL)
            OR (foreign_amount IS NOT NULL AND foreign_amount > 0
                AND exchange_rate IS NOT NULL AND exchange_rate > 0))');

-- ---------------------------------------------------------------------
-- 3) التحويل: ما خرج وما دخل، كلٌّ بعملة خزينته
-- ---------------------------------------------------------------------
-- amount يبقى ما تحرّكه الدفاتر: يخرج من المصدر ويدخل الهدف كما هو، فلا يتحرك رقم في الأرباح
-- (ق-ب٢). الصرافة مبلغان حقيقيان (ق-ب٥)، والسعر الناتج منهما حاصل قسمتهما فلا يُخزَّن.

CALL v81_add_column_if_missing('treasury_transfers', 'amount_from',
    'DECIMAL(18, 3) NULL COMMENT ''ما خرج من المصدر بعملته الأجنبية؛ NULL حين المصدر بالأساسية''');
CALL v81_add_column_if_missing('treasury_transfers', 'amount_to',
    'DECIMAL(18, 3) NULL COMMENT ''ما دخل الهدف بعملته الأجنبية؛ NULL حين الهدف بالأساسية''');

CALL add_constraint_if_missing('treasury_transfers', 'treasury_transfers_foreign_chk',
    'CHECK ((amount_from IS NULL OR amount_from > 0) AND (amount_to IS NULL OR amount_to > 0))');

DROP PROCEDURE IF EXISTS v81_add_column_if_missing;
DROP PROCEDURE IF EXISTS add_constraint_if_missing;
