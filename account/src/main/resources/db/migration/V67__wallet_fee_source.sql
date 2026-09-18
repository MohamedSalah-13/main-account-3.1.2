-- =====================================================================
-- V67: عمولة المحفظة تعرف الحركة التي دُفعت عنها
-- =====================================================================
--
-- عمولة المحفظة صف في `expenses_details` يُكتب في معاملة التحصيل نفسها - لكنه لم يكن يحمل
-- شيئا يقول **أي** تحصيل. فحذف التحصيل كان يترك عمولته مصروفا قائما، وطريق التصحيح الذي
-- تعلنه `docs/treasury-plan.md` §17 («احذف التحصيل وأعد إدخاله») كان يكتب العمولة مرتين.
--
-- العمودان معا هما الربط:
--
--   fee_source_type  رقم `ShiftCashSource` للحركة: 1-4 المستندات الأربعة، 5 تحصيل عميل،
--                    6 دفع لمورد. رقم لا نص - درس `MovementLabel`.
--   fee_source_id    مفتاح تلك الحركة في جدولها.
--
-- **لا مفتاح أجنبي**، ولا يمكن: العمود يشير إلى ستة جداول. الحذف مع المصدر تفعله
-- `WalletFeeService.removeFor` داخل معاملة حذف المصدر نفسها.
--
-- **الفهرس فريد**: للحركة الواحدة عمولة واحدة. وMySQL يعدّ كل NULL قيمة مختلفة في الفهرس
-- الفريد، فكل المصروفات العادية (NULL, NULL) لا تتصادم.
--
-- **الصفوف القائمة تبقى NULL.** عمولة كُتبت قبل هذه الهجرة لا تحمل ما يدل على تحصيلها،
-- ومطابقتها بالتاريخ والخزينة والمبلغ تخمين - وصف عمولة مربوط بالتحصيل الخطأ يُحذف مع
-- تحصيل لا يخصّه. تبقى مصروفات عادية تُحذف من شاشة المصروفات كما كانت.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_fee_column_if_missing$$
CREATE PROCEDURE add_fee_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64),
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

DROP PROCEDURE IF EXISTS add_fee_constraint_if_missing$$
CREATE PROCEDURE add_fee_constraint_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64),
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

CALL add_fee_column_if_missing('expenses_details', 'fee_source_type',
                               'TINYINT NULL COMMENT ''نوع الحركة التي دُفعت عنها عمولة المحفظة (ShiftCashSource 1-6)، أو NULL''');
CALL add_fee_column_if_missing('expenses_details', 'fee_source_id',
                               'BIGINT NULL COMMENT ''مفتاح الحركة التي دُفعت عنها العمولة، أو NULL''');

-- الاثنان معا أو لا شيء: نوع بلا مفتاح لا يدل على حركة، ومفتاح بلا نوع يدل على ست.
CALL add_fee_constraint_if_missing('expenses_details', 'expenses_details_fee_source_chk',
                                   'CHECK ((fee_source_type IS NULL AND fee_source_id IS NULL) OR (fee_source_type BETWEEN 1 AND 6 AND fee_source_id > 0))');

CALL add_fee_constraint_if_missing('expenses_details', 'expenses_details_fee_source_uk',
                                   'UNIQUE (fee_source_type, fee_source_id)');

DROP PROCEDURE IF EXISTS add_fee_column_if_missing;
DROP PROCEDURE IF EXISTS add_fee_constraint_if_missing;
