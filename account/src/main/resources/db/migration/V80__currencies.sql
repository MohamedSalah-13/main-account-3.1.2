-- =====================================================================
-- V80 - العملات وأسعار الصرف. المرحلة أ من docs/currency-plan.md.
--
-- ### ما الذي يتغير وما الذي لا يتغير
--
-- الدفاتر تبقى بعملة واحدة: **العملة الأساسية**. كل عمود مبلغ في هذه القاعدة كان وما زال بها،
-- وكل view وكل رصيد وكل تقرير يقرأ تلك الأعمدة كما هي. هذه الهجرة لا تلمس عمود مبلغ واحدًا ولا
-- تحرّك رقمًا: هي تسمّي العملة التي كانت الأرقام بها طوال الوقت، وتضيف بجانبها عملات أخرى وأسعار
-- صرفها. المبلغ الأجنبي في المراحل التالية يُسجَّل **بجوار** الرقم الأساسي، لا بدلًا منه.
--
-- ### السعر
--
-- `currency_rate.rate` = كم وحدة من العملة الأساسية تساوي وحدة واحدة من هذه العملة («الدولار
-- بـ 48.50 جنيه»)، ساريًا من `effective_date`. السعر في يوم ما هو آخر سعر مؤرَّخ في ذلك اليوم
-- أو قبله، ولا سعر قبله = لا سعر، لا صفر ولا واحد. العملة الأساسية بلا صفوف: سعرها 1 بالتعريف.
-- `DECIMAL(20, 10)`: عشر خانات عشرية لأن عملة أساسية قوية وعملة ضعيفة تعطيان سعرًا صغيرًا جدًا
-- (الروبية الإندونيسية بالدينار الكويتي 0.0000187)، وعشر صحيحة لعكس ذلك.
--
-- ### العملة الأساسية واحدة، وفي القاعدة لا في الكود وحده
--
-- `base_key` عمود محسوب = 1 للأساسية وNULL لغيرها، وعليه فريد: MySQL يعدّ كل NULL قيمة مختلفة،
-- فعملات كثيرة غير أساسية وأساسية واحدة فقط. حيلة `expense_budget.month_key` (V66) و
-- `commission_run.active_key` (V72). ولا تكون الأساسية موقوفة (`currency_base_active_chk`).
--
-- ### أي عملة تكون الأساسية عند الترقية
--
-- ما اختارته شاشة الإعدادات: `setting.currency` في `app_setting` (V40) يحمل وسم لغة مثل
-- `ar_SA`، وهو ما كانت قائمة العملات هناك تكتبه. الجدول المؤقت أدناه هو خريطة تلك القائمة نفسها -
-- اللغات العربية التي لها عملة في Java، بلا الشيكل الذي كانت تستبعده - بأسمائها ورموزها
-- ومنازلها العشرية كما تعطيها Java. إن لم يُحفظ شيء فالجنيه المصري، وهو ما كان الإعداد يرجع إليه.
-- **خطأ هذا التخمين يُصلَح من شاشة العملات**: تغيير الأساسية مسموح ما دام لا سعر صرف مسجَّلًا،
-- لأنه قبل ذلك إعادة تسمية لا تحويل (docs/currency-plan.md ق-٦).
--
-- ### ما لا تفعله
--
-- لا عملة على الخزينة ولا على الطرف ولا على الفاتورة: كلٌّ في هجرة مرحلته، وهجرة مشحونة لا
-- تُعدَّل. ولا جلب أسعار من الإنترنت: البرنامج يعمل بلا اتصال، والسعر الذي يتعامل به المحل قرار
-- صاحبه لا رقم البنك المركزي.
--
-- `user_id` يقبل NULL ويُفرَّغ عند حذف المستخدم: مسح المستخدمين من شاشة حذف البيانات يُبقي
-- المستخدم 1 وحده، وجدول لا يمسحه أحد لا يصح أن يمنعه.
--
-- لا إجراءات مساعدة هنا (MigrationHelperProcedureTest): جدولان جديدان بقيودهما، وصفوف.
-- =====================================================================

CREATE TABLE IF NOT EXISTS currency
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    code           CHAR(3)                                 NOT NULL
        COMMENT 'ISO 4217: ثلاثة حروف لاتينية كبيرة',
    name           VARCHAR(50)                             NOT NULL,
    symbol         VARCHAR(10)                             NOT NULL,
    decimal_places TINYINT       DEFAULT 2                 NOT NULL
        COMMENT 'المنازل التي يُقرَّب إليها مبلغ بهذه العملة',
    is_base        TINYINT(1)    DEFAULT 0                 NOT NULL
        COMMENT 'العملة التي كل عمود مبلغ في القاعدة بها',
    is_active      TINYINT(1)    DEFAULT 1                 NOT NULL,
    sort_order     INT           DEFAULT 0                 NOT NULL,
    base_key       TINYINT GENERATED ALWAYS AS (IF(is_base = 1, 1, NULL)) STORED
        COMMENT 'فريد: أساسية واحدة، وغير الأساسية NULL فتتكرر',
    user_id        INT                                     NULL,
    created_at     DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT currency_code_uk UNIQUE (code),
    CONSTRAINT currency_name_uk UNIQUE (name),
    CONSTRAINT currency_one_base_uk UNIQUE (base_key),
    CONSTRAINT currency_code_chk CHECK (REGEXP_LIKE(code, '^[A-Z]{3}$', 'c')),
    CONSTRAINT currency_decimals_chk CHECK (decimal_places BETWEEN 0 AND 3),
    CONSTRAINT currency_base_active_chk CHECK (is_base = 0 OR is_active = 1),
    CONSTRAINT currency_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

-- لا ON DELETE CASCADE عمدًا: تاريخ أسعار عملة دليلٌ على ما تعامل به المحل، وحذف العملة لا يمحوه
-- معه. `DeleteRegistry.CURRENCIES` يعلن هذا المرجع فيُرفض الحذف بجملة تعدّ الأسعار، والعملة التي
-- لم تعد تُستعمل تُوقَف.
CREATE TABLE IF NOT EXISTS currency_rate
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    currency_id    INT                                     NOT NULL,
    effective_date DATE                                    NOT NULL,
    rate           DECIMAL(20, 10)                         NOT NULL
        COMMENT 'كم وحدة من العملة الأساسية تساوي وحدة واحدة من هذه العملة',
    notes          VARCHAR(200)                            NULL,
    user_id        INT                                     NULL,
    created_at     DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT currency_rate_day_uk UNIQUE (currency_id, effective_date),
    CONSTRAINT currency_rate_positive_chk CHECK (rate > 0),
    CONSTRAINT currency_rate_currency_id_fk FOREIGN KEY (currency_id) REFERENCES currency (id),
    CONSTRAINT currency_rate_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

-- ---------------------------------------------------------------------
-- العملات الثلاث التي طُلبت، والأساسية
-- ---------------------------------------------------------------------

INSERT INTO currency (code, name, symbol, decimal_places, sort_order)
VALUES ('EGP', 'جنيه مصري', 'ج.م', 2, 1),
       ('SAR', 'ريال سعودي', 'ر.س', 2, 2),
       ('USD', 'دولار أمريكي', '$', 2, 3);

-- The list the settings screen offered (Currency_Setting.selectableCurrencies): every Arabic locale
-- Java gives a currency, less the shekel it filtered out. Names, symbols and minor units are Java's.
CREATE TEMPORARY TABLE v80_settings_currency
(
    locale_tag     VARCHAR(20) NOT NULL PRIMARY KEY,
    code           CHAR(3)     NOT NULL,
    name           VARCHAR(50) NOT NULL,
    symbol         VARCHAR(10) NOT NULL,
    decimal_places TINYINT     NOT NULL
);

INSERT INTO v80_settings_currency (locale_tag, code, name, symbol, decimal_places)
VALUES ('ar_AE', 'AED', 'درهم إماراتي', 'د.إ', 2),
       ('ar_BH', 'BHD', 'دينار بحريني', 'د.ب', 3),
       ('ar_DJ', 'DJF', 'فرنك جيبوتي', 'Fdj', 0),
       ('ar_DZ', 'DZD', 'دينار جزائري', 'د.ج', 2),
       ('ar_EG', 'EGP', 'جنيه مصري', 'ج.م', 2),
       ('ar_EG_#Arab', 'EGP', 'جنيه مصري', 'ج.م', 2),
       ('ar_EH', 'MAD', 'درهم مغربي', 'د.م', 2),
       ('ar_ER', 'ERN', 'ناكفا أريتري', 'Nfk', 2),
       ('ar_IQ', 'IQD', 'دينار عراقي', 'د.ع', 3),
       ('ar_JO', 'JOD', 'دينار أردني', 'د.أ', 3),
       ('ar_KM', 'KMF', 'فرنك جزر القمر', 'CF', 0),
       ('ar_KW', 'KWD', 'دينار كويتي', 'د.ك', 3),
       ('ar_LB', 'LBP', 'جنيه لبناني', 'ل.ل', 2),
       ('ar_LY', 'LYD', 'دينار ليبي', 'د.ل', 3),
       ('ar_MA', 'MAD', 'درهم مغربي', 'د.م', 2),
       ('ar_MR', 'MRU', 'أوقية موريتانية', 'أ.م', 2),
       ('ar_OM', 'OMR', 'ريال عماني', 'ر.ع', 3),
       ('ar_QA', 'QAR', 'ريال قطري', 'ر.ق', 2),
       ('ar_SA', 'SAR', 'ريال سعودي', 'ر.س', 2),
       ('ar_SD', 'SDG', 'جنيه سوداني', 'ج.س', 2),
       ('ar_SO', 'SOS', 'شلن صومالي', 'S', 2),
       ('ar_SS', 'SSP', 'جنيه جنوب السودان', '£', 2),
       ('ar_SY', 'SYP', 'ليرة سورية', 'ل.س', 2),
       ('ar_TD', 'XAF', 'فرنك وسط أفريقي', 'FCFA', 0),
       ('ar_TN', 'TND', 'دينار تونسي', 'د.ت', 3),
       ('ar_YE', 'YER', 'ريال يمني', 'ر.ي', 2);

-- The shop's currency, when it is none of the three above: added so it can be the base.
INSERT INTO currency (code, name, symbol, decimal_places, sort_order)
SELECT chosen.code, chosen.name, chosen.symbol, chosen.decimal_places, 0
FROM v80_settings_currency chosen
WHERE chosen.locale_tag = (SELECT setting_value FROM app_setting WHERE setting_key = 'setting.currency')
  AND NOT EXISTS (SELECT 1 FROM currency existing WHERE existing.code = chosen.code);

-- Exactly one base: what the setting names, else the Egyptian pound the setting fell back to.
UPDATE currency
SET is_base = 1
WHERE code = COALESCE((SELECT chosen.code
                       FROM v80_settings_currency chosen
                       WHERE chosen.locale_tag = (SELECT setting_value
                                                  FROM app_setting
                                                  WHERE setting_key = 'setting.currency')),
                      'EGP');

DROP TEMPORARY TABLE v80_settings_currency;

-- ---------------------------------------------------------------------
-- الصلاحيات الثلاث
-- ---------------------------------------------------------------------
-- `currency.show`: شاشة العملات وأسعارها. `currency.update`: إضافة عملة وتعديلها وإيقافها
-- وتحديد الأساسية. `currency.rate.update`: تسجيل سعر الصرف - منفصلة لأن من يُدخل سعر اليوم ليس
-- بالضرورة من يقرّر بأي العملات يتعامل المحل.
-- تُمنح الثلاث لمن يملك `treasury.update` اليوم: من يعرّف الخزائن هو من يقرّر بم تُعدّ.
-- والأوصاف تحت 50 حرفًا (درس V65).

INSERT INTO auth_permission(permission_key, description, module_key, resource_key, action_key,
                            risk_level, sort_order, system_permission, enabled)
VALUES ('currency.show', 'عرض العملات وأسعار الصرف', 'TREASURY', 'currency',
        'SHOW', 'LOW', 0, 1, 1),
       ('currency.update', 'إضافة العملات وتعديلها وتحديد الأساسية', 'TREASURY', 'currency',
        'UPDATE', 'HIGH', 0, 1, 1),
       ('currency.rate.update', 'تسجيل أسعار الصرف وتعديلها', 'TREASURY', 'currency.rate',
        'UPDATE', 'HIGH', 0, 1, 1)
ON DUPLICATE KEY UPDATE module_key = VALUES(module_key),
                        resource_key = VALUES(resource_key),
                        action_key = VALUES(action_key),
                        risk_level = VALUES(risk_level),
                        system_permission = 1,
                        enabled = 1;

INSERT IGNORE INTO auth_role_permission(role_id, permission_id, granted_by)
SELECT existing.role_id, granted.id, 1
FROM auth_role_permission existing
         JOIN auth_permission held
              ON held.id = existing.permission_id
             AND held.permission_key = 'treasury.update'
         JOIN auth_permission granted
              ON granted.permission_key IN ('currency.show', 'currency.update', 'currency.rate.update');
