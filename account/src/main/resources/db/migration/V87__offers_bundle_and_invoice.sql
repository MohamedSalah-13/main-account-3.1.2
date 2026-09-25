-- =====================================================================
-- V87 - العروض، المرحلة د: الطقم وعرض إجمالي الفاتورة. docs/pricing-and-offers-plan.md §4.2 و§13.
--
-- ### الطقم عرض، لا صنف (ق-ع١٢)
--
-- `BUNDLE`: مكوّنات بكمياتها بسعر واحد (`offer_price`). المكوّن هدف بدور `COMPONENT` يسمّي صنفًا - بوحدة بعينها
-- أو بوحداته الأساسية - وكميته في `offer_target.quantity`. المخزون يتحرك لكل مكوّن لأن كل مكوّن سطر عادي،
-- والتكلفة تكلفته، والمرتجع يرُدّ مكوّنًا بعينه بحصته - فلا يلزم صنف مركّب ولا تعريف ثانٍ لرصيد.
-- وللطقم **باركود اختياري** (`offer.barcode`): مسحه على الفاتورة يضيف المكوّنات سطورًا ثم يوزّع المحرك خصمه
-- عليها بالقيمة (ق-ع٢). أرقام فقط، لأن خانة الباركود في الفاتورة العادية لا تقبل غيرها، وفريد بين العروض
-- (`offer_barcode_uk`)؛ وألا يحمله صنف تقوله الخدمة وشاشة الصنف معًا - الجداول الأربعة لا يرى أحدها غيره.
--
-- ### عرض إجمالي الفاتورة
--
-- `INVOICE`: «من 1,000 فأكثر خصم 5%» أو «خصم 50». الحدّ (`threshold`) يُحكم على صافي السطور التي تصلها أهدافه
-- **بعد عروضها**، والخصم - نسبة (`percent`) أو مبلغ (`amount`)، واحد منهما - يُعطى على **ما لم يأخذه عرض
-- آخر** منها، موزَّعًا بالقيمة على السطور لا في خانة خصم الفاتورة، فيُرَدّ بنسبته كأي خصم سطر (المثال 6).
-- مرة واحدة في الفاتورة، فلا حدّ للفاتورة الواحدة له (`max_per_invoice` NULL)؛ والحدّ الكلي يعدّ الفواتير.
--
-- ### الـCHECK يُستبدل
--
-- `offer_kind_chk` و`offer_target_role_chk` يُحذفان ويُكتبان كاملَين، كل فرع يكتب IS NOT NULL لما يستعمله وNULL لما
-- لا يخصه (درس V70). والمساعدات معرَّفة هنا ومحذوفة آخر الملف (MigrationHelperProcedureTest). والـtriggers التي
-- تقرأ الأعمدة الجديدة في قسم V87 آخر `R__triggers.sql`: schema عند V86 لا يحملها.
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS v87_add_column_if_missing$$
CREATE PROCEDURE v87_add_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64), IN col_def TEXT)
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

DROP PROCEDURE IF EXISTS v87_add_constraint_if_missing$$
CREATE PROCEDURE v87_add_constraint_if_missing(IN t_name VARCHAR(64), IN k_name VARCHAR(64), IN k_def TEXT)
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

DROP PROCEDURE IF EXISTS v87_drop_check_if_present$$
CREATE PROCEDURE v87_drop_check_if_present(IN t_name VARCHAR(64), IN k_name VARCHAR(64))
BEGIN
    DECLARE key_exists INT;
    SELECT COUNT(*)
    INTO key_exists
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = t_name
      AND CONSTRAINT_NAME = k_name
      AND CONSTRAINT_TYPE = 'CHECK';
    IF key_exists = 1 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' DROP CHECK ', k_name);
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- 1) العرض: حدّ الفاتورة، وباركود الطقم
-- ---------------------------------------------------------------------

CALL v87_add_column_if_missing('offer', 'threshold',
    'DECIMAL(14, 2) NULL COMMENT ''INVOICE: صافي السطور التي يصلها العرض الذي يبدأ منه'' AFTER quantity_limit');
CALL v87_add_column_if_missing('offer', 'barcode',
    'VARCHAR(50) NULL COMMENT ''BUNDLE: مسحه على الفاتورة يضيف المكوّنات؛ أرقام فقط'' AFTER threshold');

CALL v87_add_constraint_if_missing('offer', 'offer_barcode_uk', 'UNIQUE (barcode)');
CALL v87_add_constraint_if_missing('offer', 'offer_barcode_chk',
    'CHECK (barcode IS NULL OR (kind = ''BUNDLE'' AND barcode <> ''''))');

CALL v87_drop_check_if_present('offer', 'offer_kind_chk');
CALL v87_add_constraint_if_missing('offer', 'offer_kind_chk',
    'CHECK ((kind = ''PERCENT'' AND percent IS NOT NULL AND percent > 0 AND percent <= 100
                AND amount IS NULL AND offer_price IS NULL AND unit_id IS NULL
                AND buy_quantity IS NULL AND get_quantity IS NULL AND get_percent IS NULL AND threshold IS NULL)
        OR (kind = ''AMOUNT'' AND amount IS NOT NULL AND amount > 0
                AND percent IS NULL AND offer_price IS NULL
                AND buy_quantity IS NULL AND get_quantity IS NULL AND get_percent IS NULL AND threshold IS NULL)
        OR (kind = ''PRICE'' AND offer_price IS NOT NULL AND offer_price > 0
                AND percent IS NULL AND amount IS NULL
                AND buy_quantity IS NULL AND get_quantity IS NULL AND get_percent IS NULL AND threshold IS NULL)
        OR (kind = ''QUANTITY_PRICE'' AND buy_quantity IS NOT NULL AND buy_quantity > 0
                AND offer_price IS NOT NULL AND offer_price > 0
                AND percent IS NULL AND amount IS NULL AND get_quantity IS NULL AND get_percent IS NULL
                AND threshold IS NULL)
        OR (kind = ''BUY_GET'' AND buy_quantity IS NOT NULL AND buy_quantity > 0
                AND get_quantity IS NOT NULL AND get_quantity > 0
                AND get_percent IS NOT NULL AND get_percent > 0 AND get_percent <= 100
                AND percent IS NULL AND amount IS NULL AND offer_price IS NULL AND threshold IS NULL)
        OR (kind = ''BUNDLE'' AND offer_price IS NOT NULL AND offer_price > 0
                AND percent IS NULL AND amount IS NULL AND unit_id IS NULL
                AND buy_quantity IS NULL AND get_quantity IS NULL AND get_percent IS NULL AND threshold IS NULL)
        OR (kind = ''INVOICE'' AND threshold IS NOT NULL AND threshold > 0
                AND ((percent IS NOT NULL AND percent > 0 AND percent <= 100 AND amount IS NULL)
                    OR (amount IS NOT NULL AND amount > 0 AND percent IS NULL))
                AND offer_price IS NULL AND unit_id IS NULL AND max_per_invoice IS NULL
                AND buy_quantity IS NULL AND get_quantity IS NULL AND get_percent IS NULL))');

-- ---------------------------------------------------------------------
-- 2) الهدف: يؤهِّل، أو الهدية، أو مكوّن الطقم بكميته
-- ---------------------------------------------------------------------

CALL v87_add_column_if_missing('offer_target', 'quantity',
    'DECIMAL(14, 3) NULL COMMENT ''COMPONENT: كم من هذا الصنف في الطقم الواحد، بوحدته أو بوحداته الأساسية'' AFTER excluded');

CALL v87_drop_check_if_present('offer_target', 'offer_target_role_chk');
CALL v87_add_constraint_if_missing('offer_target', 'offer_target_role_chk',
    'CHECK ((role = ''QUALIFY'' AND quantity IS NULL)
        OR (role = ''REWARD'' AND scope = ''ITEM'' AND excluded = 0 AND quantity IS NULL)
        OR (role = ''COMPONENT'' AND scope = ''ITEM'' AND excluded = 0 AND quantity IS NOT NULL AND quantity > 0))');

DROP PROCEDURE IF EXISTS v87_add_column_if_missing;
DROP PROCEDURE IF EXISTS v87_add_constraint_if_missing;
DROP PROCEDURE IF EXISTS v87_drop_check_if_present;
