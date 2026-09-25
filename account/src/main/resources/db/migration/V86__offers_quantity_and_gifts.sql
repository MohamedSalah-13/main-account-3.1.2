-- =====================================================================
-- V86 - العروض، المرحلة ج: سعر الكمية («3 بـ 100») و«اشترِ وخذ»، وحدّا العرض.
-- docs/pricing-and-offers-plan.md §4.2 و§12.
--
-- ### نوعان جديدان، وأعمدتهما
--
-- `QUANTITY_PRICE`: `buy_quantity` وحدة بسعر `offer_price` للمجموعة كلها. `BUY_GET`: يُشترى `buy_quantity`
-- ويُعطى `get_quantity` بخصم `get_percent` (100 = مجانًا)، من الصنف نفسه أو من صنف هدية يسمّيه هدف بدور
-- `REWARD`. والوحدة (`unit_id`) وحدة العدّ كما في المرحلة ب: حين تُذكر لا يُعدّ سطر بوحدة أخرى، وحين لا تُذكر
-- تُعدّ الوحدات الأساسية - فكرتونة من 12 و3 قطع خمس عشرة قطعة.
--
-- ### الحدّان يعدّان ما يعطيه العرض
--
-- `max_per_invoice` في الفاتورة الواحدة و`quantity_limit` على كل الفواتير ناقص ما رُدّ. يعدّان **مرات العرض**:
-- وحدة لعرض النسبة والمبلغ والسعر، ومجموعة لعرض الكمية و«اشترِ وخذ» - «حتى 3 قطع للعميل» و«3 بـ 100، مرتين
-- للعميل» جملتان صحيحتان بعدّ واحد.
--
-- ### ما أعطاه العرض على السطر
--
-- `offer_quantity` على سطر البيع والمرتجع: كم وحدة (بوحدة عدّ العرض) غطّاها العرض من هذا السطر. منه يُعدّ الحدّ
-- الكلي - مجموع سطور البيع ناقص سطور المرتجع، مقسومًا على حجم المجموعة - ولا عدّاد مخزَّن يمكن أن يختلف عنه.
-- سطور V85 تُملأ هنا بما غطّاه عرضها فعلًا: السطر كله، بوحدته حين يسمّي العرض وحدة وإلا بوحداته الأساسية -
-- أنواع المرحلة ب تعطي كل وحدة في السطر.
--
-- ### الـCHECK يُستبدل
--
-- `offer_kind_chk` يُحذف ويُكتب ثانية بالنوعين الجديدين وبأن ما لا يخص النوع NULL. كل فرع يقارن عمودًا يقبل
-- NULL يكتب IS NOT NULL بنفسه (درس V70). والمساعدات معرَّفة هنا ومحذوفة آخر الملف (MigrationHelperProcedureTest).
-- والـtriggers التي تقرأ الأعمدة الجديدة في قسم V86 آخر `R__triggers.sql`: schema عند V85 لا يحملها.
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS v86_add_column_if_missing$$
CREATE PROCEDURE v86_add_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64), IN col_def TEXT)
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

DROP PROCEDURE IF EXISTS v86_add_check_if_missing$$
CREATE PROCEDURE v86_add_check_if_missing(IN t_name VARCHAR(64), IN k_name VARCHAR(64), IN k_def TEXT)
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

DROP PROCEDURE IF EXISTS v86_drop_check_if_present$$
CREATE PROCEDURE v86_drop_check_if_present(IN t_name VARCHAR(64), IN k_name VARCHAR(64))
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
-- 1) العرض: الكمية، «اشترِ وخذ»، والحدّان
-- ---------------------------------------------------------------------

CALL v86_add_column_if_missing('offer', 'buy_quantity',
    'DECIMAL(14, 3) NULL COMMENT ''QUANTITY_PRICE: كم وحدة بسعر العرض؛ BUY_GET: كم يُشترى'' AFTER unit_id');
CALL v86_add_column_if_missing('offer', 'get_quantity',
    'DECIMAL(14, 3) NULL COMMENT ''BUY_GET: كم يُعطى'' AFTER buy_quantity');
CALL v86_add_column_if_missing('offer', 'get_percent',
    'DECIMAL(6, 3) NULL COMMENT ''BUY_GET: خصم ما يُعطى؛ 100 مجانًا'' AFTER get_quantity');
CALL v86_add_column_if_missing('offer', 'max_per_invoice',
    'DECIMAL(14, 3) NULL COMMENT ''أقصى مرات العرض في فاتورة: وحدات، أو مجموعات لعرض الكمية واشترِ وخذ'' AFTER get_percent');
CALL v86_add_column_if_missing('offer', 'quantity_limit',
    'DECIMAL(14, 3) NULL COMMENT ''أقصى مرات العرض على كل الفواتير ناقص ما رُدّ'' AFTER max_per_invoice');

CALL v86_drop_check_if_present('offer', 'offer_kind_chk');
CALL v86_add_check_if_missing('offer', 'offer_kind_chk',
    'CHECK ((kind = ''PERCENT'' AND percent IS NOT NULL AND percent > 0 AND percent <= 100
                AND amount IS NULL AND offer_price IS NULL AND unit_id IS NULL
                AND buy_quantity IS NULL AND get_quantity IS NULL AND get_percent IS NULL)
        OR (kind = ''AMOUNT'' AND amount IS NOT NULL AND amount > 0
                AND percent IS NULL AND offer_price IS NULL
                AND buy_quantity IS NULL AND get_quantity IS NULL AND get_percent IS NULL)
        OR (kind = ''PRICE'' AND offer_price IS NOT NULL AND offer_price > 0
                AND percent IS NULL AND amount IS NULL
                AND buy_quantity IS NULL AND get_quantity IS NULL AND get_percent IS NULL)
        OR (kind = ''QUANTITY_PRICE'' AND buy_quantity IS NOT NULL AND buy_quantity > 0
                AND offer_price IS NOT NULL AND offer_price > 0
                AND percent IS NULL AND amount IS NULL AND get_quantity IS NULL AND get_percent IS NULL)
        OR (kind = ''BUY_GET'' AND buy_quantity IS NOT NULL AND buy_quantity > 0
                AND get_quantity IS NOT NULL AND get_quantity > 0
                AND get_percent IS NOT NULL AND get_percent > 0 AND get_percent <= 100
                AND percent IS NULL AND amount IS NULL AND offer_price IS NULL))');

CALL v86_add_check_if_missing('offer', 'offer_limits_chk',
    'CHECK ((max_per_invoice IS NULL OR max_per_invoice > 0) AND (quantity_limit IS NULL OR quantity_limit > 0))');

-- ---------------------------------------------------------------------
-- 2) الهدف: يؤهِّل، أو هو الهدية
-- ---------------------------------------------------------------------

-- الهدية صنف بعينه (بوحدة أو بوحدته الأساسية)، لا مجموعة ولا استثناء.
CALL v86_add_column_if_missing('offer_target', 'role',
    'VARCHAR(10) DEFAULT ''QUALIFY'' NOT NULL COMMENT ''QUALIFY يؤهِّل للعرض؛ REWARD صنف الهدية في BUY_GET'' AFTER offer_id');
CALL v86_add_check_if_missing('offer_target', 'offer_target_role_chk',
    'CHECK (role = ''QUALIFY'' OR (role = ''REWARD'' AND scope = ''ITEM'' AND excluded = 0))');

-- ---------------------------------------------------------------------
-- 3) السطر: كم غطّى العرض منه
-- ---------------------------------------------------------------------

CALL v86_add_column_if_missing('sales', 'offer_quantity',
    'DECIMAL(14, 3) NOT NULL DEFAULT 0 COMMENT ''كم وحدة من السطر غطّاها العرض، بوحدة عدّه'' AFTER offer_discount');
CALL v86_add_check_if_missing('sales', 'sales_offer_quantity_chk',
    'CHECK (offer_quantity >= 0 AND (offer_id IS NOT NULL OR offer_quantity = 0))');

CALL v86_add_column_if_missing('sales_re', 'offer_quantity',
    'DECIMAL(14, 3) NOT NULL DEFAULT 0 COMMENT ''حصة المرتجع مما غطّاه عرض سطر المصدر، بنسبة كميته'' AFTER offer_discount');
CALL v86_add_check_if_missing('sales_re', 'sales_re_offer_quantity_chk',
    'CHECK (offer_quantity >= 0 AND (offer_id IS NOT NULL OR offer_quantity = 0))');

-- سطور V85: عرض النسبة والمبلغ والسعر يغطي السطر كله - بوحدته حين يسمّي العرض وحدة، وإلا بوحداته الأساسية.
UPDATE sales s JOIN offer o ON o.id = s.offer_id
SET s.offer_quantity = IF(o.unit_id IS NULL, s.quantity * s.type_value, s.quantity)
WHERE s.offer_id IS NOT NULL AND s.offer_quantity = 0;

UPDATE sales_re r JOIN offer o ON o.id = r.offer_id
SET r.offer_quantity = IF(o.unit_id IS NULL, r.quantity * r.type_value, r.quantity)
WHERE r.offer_id IS NOT NULL AND r.offer_quantity = 0;

DROP PROCEDURE IF EXISTS v86_add_column_if_missing;
DROP PROCEDURE IF EXISTS v86_add_check_if_missing;
DROP PROCEDURE IF EXISTS v86_drop_check_if_present;
