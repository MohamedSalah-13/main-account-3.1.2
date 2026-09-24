-- =====================================================================
-- V85 - العروض: الجداول، والمرحلة ب (نسبة، مبلغ، سعر). docs/pricing-and-offers-plan.md §4.2.
--
-- ### العرض لا يغيّر سعرًا؛ يكتب خصمًا على السطر
--
-- السطر يبقى بالسعر الذي حُصِّل، والعرض يكتب **خصمًا** عليه (ق-ع١)، وبجوار عمود الخصم عمودان:
-- `offer_id` (أي عرض) و`offer_discount` (كم من الخصم منه). فالربح والمرتجع والإيصال وقائمة الدخل وعمولة
-- المندوب وأرصدة العملاء كلها تقرأ `discount` كما كانت، ولا يتغيّر view واحد. ما قبل هذه الهجرة
-- `offer_id` فيه NULL و`offer_discount` صفر - لا عرض كان، ولا يُخمَّن.
--
-- ### ثلاثة أنواع فقط، وأعمدتها فقط
--
-- المرحلة ب تبني النسبة والمبلغ والسعر. الكمية و«اشترِ وخذ» (ج) والطقم وعرض الفاتورة (د) تضيف أنواعها
-- وأعمدتها وقيودها في هجراتها، فلا يحمل الجدول اليوم عمودًا لا يقرؤه شيء ولا CHECK يعد بنوع لم يُبنَ.
--
-- ### المجموعة عمودان، لا عمود واحد
--
-- الخطة كتبت `group_id` واحدًا للمجموعة الفرعية والرئيسية. عمود يشير إلى جدولين لا يحمل مفتاحًا أجنبيًا،
-- فحذف مجموعة يسمّيها عرض يمرّ بلا كلمة ويترك العرض يشير إلى لا شيء. فهما عمودان، لكل منهما مفتاحه،
-- و`DeleteRegistry` يعلنهما.
--
-- ### المفاتيح
--
-- الهدف والشرائح جزء من تعريف العرض، فيُجرفان معه (CASCADE) - والعرض المستعمَل لا يُحذف أصلًا:
-- `sales.offer_id` و`sales_re.offer_id` بلا CASCADE (ق-ع٧). والصنف والوحدة والمجموعتان بلا CASCADE:
-- حذف صنف يسمّيه عرض يُرفض ويقول لماذا، ودمجه ينقل الهدف (`ItemReferenceRegistry`).
--
-- ### أيام الأسبوع قناع
--
-- `weekdays` NULL = كل يوم؛ وإلا بت لكل يوم: الاثنين 1، الثلاثاء 2، ... الأحد 64 (ترتيب `DayOfWeek` في
-- Java). المحل الذي يبدأ أسبوعه السبت يرى الأيام بترتيبه على الشاشة؛ التخزين لا يعنيه.
--
-- ### الصلاحيات
--
-- `offer.show` لكل من يملك `items.show`، و`offer.create`/`offer.update` لمن يملك `items.update`،
-- و`offer.delete` لمن يملك `items.delete` (§6، قُرِّر 2026-09-24) - طريقة V79، الأدوار والاستثناءات ALLOW.
-- و`auth_permission.description` هو VARCHAR(50) (درس V65).
--
-- الإجراءان المساعدان معرَّفان هنا ومحذوفان في آخر الملف (MigrationHelperProcedureTest). والتدقيق على
-- `offer` في آخر `R__triggers.sql` تحت علامة V85.
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS v85_add_column_if_missing$$
CREATE PROCEDURE v85_add_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64), IN col_def TEXT)
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
-- 1) العرض
-- ---------------------------------------------------------------------

-- كل CHECK يقارن عمودًا يقبل NULL يكتب IS NOT NULL بنفسه: CHECK في MySQL يمرّ حين يُقيَّم NULL (درس V70).
CREATE TABLE IF NOT EXISTS offer
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)                          NOT NULL,
    kind        VARCHAR(20)                           NOT NULL COMMENT 'PERCENT / AMOUNT / PRICE (المرحلة ب)',
    status      VARCHAR(10)  DEFAULT 'DRAFT'          NOT NULL COMMENT 'DRAFT لا يصل فاتورة، ACTIVE يصل، STOPPED لا يصل',
    starts_on   DATE                                  NOT NULL,
    ends_on     DATE                                  NULL COMMENT 'NULL بلا نهاية',
    weekdays    TINYINT UNSIGNED                      NULL COMMENT 'NULL كل يوم؛ الاثنين 1 ... الأحد 64',
    priority    INT          DEFAULT 0                NOT NULL COMMENT 'الأعلى يُفضَّل حين يصل عرضان سطرًا واحدًا',
    percent     DECIMAL(6, 3)                         NULL COMMENT 'PERCENT: النسبة من سعر السطر',
    amount      DECIMAL(14, 2)                        NULL COMMENT 'AMOUNT: المبلغ عن كل وحدة (unit_id، أو الأساسية)',
    offer_price DECIMAL(14, 2)                        NULL COMMENT 'PRICE: سعر الوحدة (unit_id، أو الأساسية)؛ لا يرفع سعرًا',
    unit_id     INT                                   NULL COMMENT 'وحدة المبلغ أو السعر؛ حين تُذكر لا يصل العرض سطرًا بوحدة أخرى',
    notes       VARCHAR(255)                          NULL,
    user_id     INT          DEFAULT 1                NOT NULL COMMENT 'من أنشأ',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) NOT NULL
        COMMENT 'إصدار للتعديل المتفائل، كـ V50/V52',
    CONSTRAINT offer_name_uk UNIQUE (name),
    CONSTRAINT offer_units_fk FOREIGN KEY (unit_id) REFERENCES units (unit_id),
    CONSTRAINT offer_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT offer_status_chk CHECK (status IN ('DRAFT', 'ACTIVE', 'STOPPED')),
    CONSTRAINT offer_dates_chk CHECK (ends_on IS NULL OR ends_on >= starts_on),
    CONSTRAINT offer_weekdays_chk CHECK (weekdays IS NULL OR weekdays BETWEEN 1 AND 127),
    CONSTRAINT offer_kind_chk
        CHECK ((kind = 'PERCENT' AND percent IS NOT NULL AND percent > 0 AND percent <= 100
                    AND amount IS NULL AND offer_price IS NULL AND unit_id IS NULL)
            OR (kind = 'AMOUNT' AND amount IS NOT NULL AND amount > 0
                    AND percent IS NULL AND offer_price IS NULL)
            OR (kind = 'PRICE' AND offer_price IS NOT NULL AND offer_price > 0
                    AND percent IS NULL AND amount IS NULL))
);

-- ما يصله العرض: صنف (بوحدة بعينها أو بكل وحداته)، أو مجموعة فرعية، أو رئيسية، أو كل شيء - ومنها ما
-- يُستثنى («كل المنظفات عدا المسحوق»). عمود لكل ما يُشار إليه، ولكل منها مفتاحه.
CREATE TABLE IF NOT EXISTS offer_target
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    offer_id      INT                   NOT NULL,
    scope         VARCHAR(12)           NOT NULL COMMENT 'ITEM / SUB_GROUP / MAIN_GROUP / ALL',
    item_id       INT                   NULL,
    unit_id       INT                   NULL COMMENT 'ITEM: وحدة بعينها؛ NULL كل وحداته',
    sub_group_id  INT                   NULL,
    main_group_id INT                   NULL,
    excluded      TINYINT(1) DEFAULT 0  NOT NULL COMMENT '1: يُستثنى مما يصله باقي الأهداف',
    CONSTRAINT offer_target_offer_fk FOREIGN KEY (offer_id) REFERENCES offer (id) ON DELETE CASCADE,
    CONSTRAINT offer_target_items_fk FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT offer_target_units_fk FOREIGN KEY (unit_id) REFERENCES units (unit_id),
    CONSTRAINT offer_target_sub_group_fk FOREIGN KEY (sub_group_id) REFERENCES sub_group (id),
    CONSTRAINT offer_target_main_group_fk FOREIGN KEY (main_group_id) REFERENCES main_group (id),
    CONSTRAINT offer_target_excluded_chk CHECK (excluded IN (0, 1)),
    CONSTRAINT offer_target_scope_chk
        CHECK ((scope = 'ITEM' AND item_id IS NOT NULL AND sub_group_id IS NULL AND main_group_id IS NULL)
            OR (scope = 'SUB_GROUP' AND sub_group_id IS NOT NULL AND item_id IS NULL AND unit_id IS NULL
                    AND main_group_id IS NULL)
            OR (scope = 'MAIN_GROUP' AND main_group_id IS NOT NULL AND item_id IS NULL AND unit_id IS NULL
                    AND sub_group_id IS NULL)
            OR (scope = 'ALL' AND item_id IS NULL AND unit_id IS NULL AND sub_group_id IS NULL
                    AND main_group_id IS NULL AND excluded = 0))
);

-- الشرائح التي يصلها العرض: لا صفوف = كل الشرائح (عرض للمستهلك لا يصل تاجر الجملة).
CREATE TABLE IF NOT EXISTS offer_price_tier
(
    offer_id      INT NOT NULL,
    price_tier_id INT NOT NULL,
    PRIMARY KEY (offer_id, price_tier_id),
    CONSTRAINT offer_price_tier_offer_fk FOREIGN KEY (offer_id) REFERENCES offer (id) ON DELETE CASCADE,
    CONSTRAINT offer_price_tier_type_price_fk FOREIGN KEY (price_tier_id) REFERENCES type_price (id)
);

-- ---------------------------------------------------------------------
-- 2) السطر: أي عرض، وكم من خصمه منه
-- ---------------------------------------------------------------------

CALL v85_add_column_if_missing('sales', 'offer_id',
    'INT NULL COMMENT ''العرض الذي كتب خصم هذا السطر؛ NULL بلا عرض''');
CALL v85_add_column_if_missing('sales', 'offer_discount',
    'DECIMAL(14, 2) NOT NULL DEFAULT 0 COMMENT ''ما في discount من العرض؛ جزء منه لا إضافة إليه''');
CALL add_constraint_if_missing('sales', 'sales_offer_fk',
    'FOREIGN KEY (offer_id) REFERENCES offer (id)');
CALL add_constraint_if_missing('sales', 'sales_offer_discount_chk',
    'CHECK (offer_discount >= 0 AND (offer_id IS NOT NULL OR offer_discount = 0))');

-- المرتجع لا يُطبَّق عليه عرض: سطره يأخذ العرض وحصته من خصمه من سطر المصدر (ق-ع١١).
CALL v85_add_column_if_missing('sales_re', 'offer_id',
    'INT NULL COMMENT ''عرض سطر المصدر، منسوخًا؛ NULL بلا عرض''');
CALL v85_add_column_if_missing('sales_re', 'offer_discount',
    'DECIMAL(14, 2) NOT NULL DEFAULT 0 COMMENT ''حصة المرتجع من خصم عرض سطر المصدر، بنسبة كميته''');
CALL add_constraint_if_missing('sales_re', 'sales_re_offer_fk',
    'FOREIGN KEY (offer_id) REFERENCES offer (id)');
CALL add_constraint_if_missing('sales_re', 'sales_re_offer_discount_chk',
    'CHECK (offer_discount >= 0 AND (offer_id IS NOT NULL OR offer_discount = 0))');

-- ---------------------------------------------------------------------
-- 3) الصلاحيات
-- ---------------------------------------------------------------------

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('offer.show', 'رؤية العروض', 'OFFER', 'offer', 'SHOW', 'LOW', 0, 1, 1),
       ('offer.create', 'إنشاء عرض', 'OFFER', 'offer', 'CREATE', 'HIGH', 0, 1, 1),
       ('offer.update', 'تعديل عرض وتفعيله وإيقافه', 'OFFER', 'offer', 'UPDATE', 'HIGH', 0, 1, 1),
       ('offer.delete', 'حذف عرض لم يُستعمل', 'OFFER', 'offer', 'DELETE', 'CRITICAL', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

-- Whoever could see, edit or delete an item gets the matching offer key: an offer is a statement
-- about items' prices, and nobody loses anything - there was no offer to see before.
INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, offer_key.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held ON held.id = existing.permission_id
         JOIN auth_permission offer_key
              ON (held.permission_key = 'items.show' AND offer_key.permission_key = 'offer.show')
              OR (held.permission_key = 'items.update' AND offer_key.permission_key IN ('offer.create', 'offer.update'))
              OR (held.permission_key = 'items.delete' AND offer_key.permission_key = 'offer.delete');

INSERT IGNORE INTO auth_user_permission_override(user_id, permission_id, effect, reason, expires_at, granted_by)
SELECT existing.user_id, offer_key.id, 'ALLOW', CONCAT('V85: had ', held.permission_key), existing.expires_at, 1
FROM auth_user_permission_override existing
         JOIN auth_permission held ON held.id = existing.permission_id
         JOIN auth_permission offer_key
              ON (held.permission_key = 'items.show' AND offer_key.permission_key = 'offer.show')
              OR (held.permission_key = 'items.update' AND offer_key.permission_key IN ('offer.create', 'offer.update'))
              OR (held.permission_key = 'items.delete' AND offer_key.permission_key = 'offer.delete')
WHERE existing.effect = 'ALLOW';

DROP PROCEDURE IF EXISTS v85_add_column_if_missing;
DROP PROCEDURE IF EXISTS add_constraint_if_missing;
