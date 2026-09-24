-- =====================================================================
-- V84 - شرائح الأسعار. المرحلة أ من docs/pricing-and-offers-plan.md.
--
-- ### الأسعار الثلاثة كانت موجودة، وما حولها لم يكن
--
-- سعر البيع الثالث في `items.sel_price1..3` منذ V1، ولكل وحدة في `items_units.sel_price..3` منذ V6،
-- وشريحة العميل في `custom.price_id`. ما كان ناقصًا ثلاثة أشياء، وهذه الهجرة تضيف أعمدتها:
--
-- - **الشريحة تُسمّى وتُوقَف وتُملأ بقاعدة** (`type_price`): `is_active` يُخرج شريحة لا يبيع بها المحل من
--   كل كومبو، والشريحة 1 لا تُوقَف أبدًا - هي السعر الذي يملكه كل صنف، وإليها يرجع صنف بلا سعر في
--   شريحة العميل (ق-س٣). وقاعدة الملء (`rule_*`) تكتب الأسعار بمعاينة وتأكيد ولا تُحسب لحظة البيع (ق-س٥).
-- - **الفاتورة تحمل شريحتها** (`total_sales.price_tier_id`، `total_sales_re.price_tier_id`): منسوخة لحظة
--   الحفظ كما يُنسخ سعر الصرف، فنقل العميل غدًا إلى شريحة أخرى لا يعيد تسعير فاتورة الأمس (ق-س٢).
-- - **السطر يحمل سعر قائمته** (`sales.list_price`) بجوار السعر الذي حُصِّل، بالأساسية كالسعر: سعر يُكتب
--   يدويًا تحته خصم متخفٍّ لا يراه تقرير اليوم (ق-س٤).
--
-- ### NULL لما قبلها
--
-- الأعمدة الثلاثة على المستندات فارغة لكل ما حُفظ قبل هذه الهجرة: لا يُخمَّن بأي شريحة سُعِّرت فاتورة قديمة،
-- ولا ما كان سعر قائمة سطرها. لا يتغيّر رقم في أي view.
--
-- ### لماذا لا CHECK على الشريحة 1
--
-- الخطة كتبت «CHECK على id=1»، وMySQL يرفض CHECK يذكر عمود AUTO_INCREMENT (الخطأ 3818) - فكانت الهجرة
-- ستفشل على كل تركيب. القاعدة في trigger في `R__triggers.sql` (قسم V84، آخر الملف) وفي
-- `PriceTierService`. وCHECK القاعدة يكتب IS NULL / IS NOT NULL في كل فرع: CHECK في MySQL يمرّ حين يكون
-- NULL (درس V70).
--
-- ### الصلاحيتان
--
-- `sales.price.below.list` (سعر يدوي تحت القائمة) تُمنح لكل دور وكل استثناء ALLOW يحمل `sales.create` -
-- طريقة V79 - فلا يخسر أحد قدرة كانت له بلا اسم. و`sales.price.tier.change` لا تُمنح لأحد: القدرة لم تكن
-- موجودة. `auth_permission.description` هو VARCHAR(50) (درس V65).
--
-- الإجراءان المساعدان معرَّفان هنا ومحذوفان في آخر الملف (MigrationHelperProcedureTest).
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS v84_add_column_if_missing$$
CREATE PROCEDURE v84_add_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64), IN col_def TEXT)
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
-- 1) الشريحة: نشطة أم لا، وقاعدة ملئها
-- ---------------------------------------------------------------------

CALL v84_add_column_if_missing('type_price', 'is_active',
    'TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''الشريحة الموقوفة تخرج من كل كومبو؛ الشريحة 1 لا تُوقَف (trigger)''');
CALL v84_add_column_if_missing('type_price', 'rule_source',
    'VARCHAR(10) NULL COMMENT ''قاعدة الملء: NULL بلا قاعدة، COST من سعر الشراء، TIER من شريحة أخرى''');
CALL v84_add_column_if_missing('type_price', 'rule_tier_id',
    'INT NULL COMMENT ''الشريحة المصدر حين rule_source = TIER''');
CALL v84_add_column_if_missing('type_price', 'rule_percent',
    'DECIMAL(7, 3) NULL COMMENT ''النسبة على المصدر: موجبة زيادة وسالبة نقص''');
CALL v84_add_column_if_missing('type_price', 'rule_rounding',
    'DECIMAL(8, 2) NULL COMMENT ''التقريب لأقرب مضاعف: 0.05، 0.25، 0.50، 1، 5''');

CALL add_constraint_if_missing('type_price', 'type_price_rule_tier_fk',
    'FOREIGN KEY (rule_tier_id) REFERENCES type_price (id)');
CALL add_constraint_if_missing('type_price', 'type_price_active_chk',
    'CHECK (is_active IN (0, 1))');
CALL add_constraint_if_missing('type_price', 'type_price_rule_chk',
    'CHECK ((rule_source IS NULL AND rule_tier_id IS NULL AND rule_percent IS NULL AND rule_rounding IS NULL)
            OR (rule_source = ''COST'' AND rule_tier_id IS NULL
                AND rule_percent IS NOT NULL AND rule_percent > -100
                AND rule_rounding IS NOT NULL AND rule_rounding > 0)
            OR (rule_source = ''TIER'' AND rule_tier_id IS NOT NULL
                AND rule_percent IS NOT NULL AND rule_percent > -100
                AND rule_rounding IS NOT NULL AND rule_rounding > 0))');

-- ---------------------------------------------------------------------
-- 2) الفاتورة: الشريحة التي سُعِّرت بها، منسوخة
-- ---------------------------------------------------------------------

CALL v84_add_column_if_missing('total_sales', 'price_tier_id',
    'INT NULL COMMENT ''الشريحة التي سُعِّرت بها الفاتورة لحظة الحفظ؛ NULL لما قبل V84''');
CALL add_constraint_if_missing('total_sales', 'total_sales_price_tier_fk',
    'FOREIGN KEY (price_tier_id) REFERENCES type_price (id)');

CALL v84_add_column_if_missing('total_sales_re', 'price_tier_id',
    'INT NULL COMMENT ''الشريحة التي سُعِّر بها المرتجع لحظة الحفظ؛ NULL لما قبل V84''');
CALL add_constraint_if_missing('total_sales_re', 'total_sales_re_price_tier_fk',
    'FOREIGN KEY (price_tier_id) REFERENCES type_price (id)');

-- ---------------------------------------------------------------------
-- 3) السطر: سعر القائمة بجوار السعر المحصَّل
-- ---------------------------------------------------------------------

CALL v84_add_column_if_missing('sales', 'list_price',
    'DECIMAL(14, 2) NULL COMMENT ''ما قالته قائمة الشريحة لهذا السطر، بالأساسية كـ price؛ NULL لما قبل V84''');
CALL add_constraint_if_missing('sales', 'sales_list_price_chk',
    'CHECK (list_price IS NULL OR list_price >= 0)');

-- ---------------------------------------------------------------------
-- 4) الصلاحيتان
-- ---------------------------------------------------------------------

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('sales.price.below.list', 'البيع بسعر يدوي تحت سعر القائمة', 'SALES', 'sales.price.below',
        'LIST', 'MEDIUM', 0, 1, 1),
       ('sales.price.tier.change', 'تغيير شريحة السعر على الفاتورة', 'SALES', 'sales.price.tier',
        'CHANGE', 'MEDIUM', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

-- Whoever may create a sale could type any price above the cost yesterday; this only says so.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, below.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id
             AND held.permission_key = 'sales.create'
         JOIN auth_permission below
              ON below.permission_key = 'sales.price.below.list';

-- A user given create outside their roles could do it too, for as long as that grant runs. A DENY is
-- not copied: denying create is not a statement about prices.
INSERT IGNORE INTO auth_user_permission_override(user_id, permission_id, effect, reason, expires_at, granted_by)
SELECT existing.user_id, below.id, 'ALLOW', 'V84: had sales.create', existing.expires_at, 1
FROM auth_user_permission_override existing
         JOIN auth_permission held
              ON held.id = existing.permission_id
             AND held.permission_key = 'sales.create'
         JOIN auth_permission below
              ON below.permission_key = 'sales.price.below.list'
WHERE existing.effect = 'ALLOW';

DROP PROCEDURE IF EXISTS v84_add_column_if_missing;
DROP PROCEDURE IF EXISTS add_constraint_if_missing;
