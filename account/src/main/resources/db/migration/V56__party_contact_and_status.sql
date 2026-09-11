-- بيانات الطرف التي كانت ناقصة، وحالته.
--
-- ### 1. البريد والرقم الضريبي
--
-- الرقم الضريبي ليس ترفا: الفاتورة الإلكترونية في مصر تطلبه للعميل المسجَّل، ومن لا يحتفظ به في
-- سجل العميل يبحث عنه في ورق عند كل فاتورة. والبريد هو الطريق الوحيد لإرسال كشف حساب بلا طباعة.
--
-- ### 2. شروط السداد
--
-- `payment_terms_days` هو عدد الأيام المتفق عليها مع الطرف - صفر يعني نقدا. وهو **شرط تقرير أعمار
-- الديون**: «متأخر 90 يوما» بلا مدة متفق عليها معناه 90 يوما من تاريخ الفاتورة لا من تاريخ
-- استحقاقها، وهما ليسا نفس الشيء لعميل مهلته ثلاثون يوما. تقرير الأعمار (المرحلة د) يقرأ هذا
-- العمود، فأُضيف قبله.
--
-- ### 3. المندوب الافتراضي
--
-- `default_delegate_id` على العميل وحده: `total_sales.delegate_id` موجود من V1، والمندوب يُختار
-- في كل فاتورة بلا ذاكرة. والمندوبون صفوف في `employees` - وهو ما يشير إليه
-- `total_sales_employees_id_fk` - **ولا قيد أجنبي هنا عمدا**: المفتاح كان سيرفض حذف مندوب ما دام
-- عميل واحد يحمله افتراضا، فيصير تفضيل على شاشة عميل مانعا لعملية على شاشة أخرى. صفر يعني
-- «لا أحد»، وهي القراءة التي يأخذها `total_sales.delegate_id` بالفعل.
--
-- ### 4. تاريخ الرصيد الافتتاحي
--
-- الرصيد الافتتاحي هو الرقم الوحيد على صف الطرف بلا تاريخ، ولهذا يمنع `OpeningBalanceGuard`
-- تعديله بعد أول حركة. والـ view يؤرّخه بـ `created_at` - تاريخ **إدخال** العميل في النظام - وهو
-- ليس تاريخ الرصيد: من يُدخل عملاءه في سبتمبر بأرصدة قائمة من يناير يرى كل تلك الأرصدة مؤرَّخة
-- في سبتمبر، فكشف حساب لشهر يناير يُطبع خاليا.
--
-- `opening_balance_date` يُعبَّأ بـ `created_at` للصفوف القائمة - وهو ما كان الـ view يستعمله
-- فعلا، فلا يتغير رقم لأحد - ويصبح بعد ذلك حقلا يملؤه من يعرف التاريخ الصحيح. **ولا يقرؤه أي
-- view بعد:** تغيير `account_customer_table` ليؤرّخ الرصيد الافتتاحي بهذا العمود ينقل حركة في
-- تاريخ كل عميل قائم، وهو قرار يُتَّخذ وحده لا كأثر جانبي لهجرة تضيف عمودا. المرحلة د.
--
-- ### 5. الحالة
--
-- `is_active` يحل مشكلة قائمة: `DeleteRegistry` يرفض حذف طرف له فاتورة واحدة - وهو صحيح، فتاريخه
-- لا يُحذف معه - و`custom.name` فريد، فالاسم لا يُعاد إصداره. فكل طرف توقف التعامل معه يبقى في كل
-- كومبو إلى الأبد. وهو نفس المنطق الذي تأخذه `UsersService` بـ `updateActive` بدل `delete`.
--
-- الافتراضي 1 لكل صف قائم: لا أحد يختفي عند الترقية.

-- الإجراءان اللذان تستعملهما هذه الهجرة، معرَّفان هنا ويُحذفان في آخرها.
--
-- **ولا يجوز غير ذلك، وهذا هو الدرس الذي تكرر مرتين في يوم واحد.** `V1` ينشئ
-- `add_index_if_missing` ويستعمله ثمانين مرة ثم **يحذفه في سطره 994**؛ و`add_column_if_missing`
-- لا تنشئه هجرة أساسية إطلاقا - بل تعرّفه كل هجرة تحتاجه محليا وتحذفه (V20، V21، V22، V23). فأي
-- نداء على أيٍّ منهما من هجرة جديدة يفشل على **كل** تركيب، جديدا كان أو مرقًّى. أول صيغة من V55
-- وأول صيغة من هذه فعلتا ذلك، والبناء الأخضر لم يرَ أيًّا منهما: هجرة لا تكون خاطئة إلا عند
-- قراءة MySQL لها. وهو نفس صنف العطل الذي يسجّله V1_1__audit_log_procedure.sql.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_party_column_if_missing$$
CREATE PROCEDURE add_party_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64),
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

DROP PROCEDURE IF EXISTS add_party_status_index$$
CREATE PROCEDURE add_party_status_index(IN t_name VARCHAR(64), IN i_name VARCHAR(64),
                                        IN i_columns TEXT)
BEGIN
    DECLARE index_exists INT;

    SELECT COUNT(*)
    INTO index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = t_name
      AND INDEX_NAME = i_name;

    IF index_exists = 0 THEN
        SET @query = CONCAT('CREATE INDEX ', i_name, ' ON ', t_name, ' (', i_columns, ')');
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_party_column_if_missing('custom', 'email', 'VARCHAR(120) NULL');
CALL add_party_column_if_missing('custom', 'tax_number', 'VARCHAR(50) NULL');
CALL add_party_column_if_missing('custom', 'payment_terms_days',
                           'SMALLINT DEFAULT 0 NOT NULL COMMENT ''أيام السداد المتفق عليها. صفر = نقدا''');
CALL add_party_column_if_missing('custom', 'default_delegate_id',
                           'INT DEFAULT 0 NOT NULL COMMENT ''المندوب الافتراضي، أو 0. لا قيد أجنبي: انظر V56''');
CALL add_party_column_if_missing('custom', 'opening_balance_date',
                           'DATE NULL COMMENT ''تاريخ الرصيد الافتتاحي. لا يقرؤه أي view بعد - انظر V56''');
CALL add_party_column_if_missing('custom', 'is_active',
                           'TINYINT(1) DEFAULT 1 NOT NULL COMMENT ''طرف موقوف يبقى بتاريخه ويخرج من الكومبوهات''');

CALL add_party_column_if_missing('suppliers', 'email', 'VARCHAR(120) NULL');
CALL add_party_column_if_missing('suppliers', 'tax_number', 'VARCHAR(50) NULL');
CALL add_party_column_if_missing('suppliers', 'payment_terms_days',
                           'SMALLINT DEFAULT 0 NOT NULL COMMENT ''أيام السداد المتفق عليها. صفر = نقدا''');
CALL add_party_column_if_missing('suppliers', 'opening_balance_date',
                           'DATE NULL COMMENT ''تاريخ الرصيد الافتتاحي. لا يقرؤه أي view بعد - انظر V56''');
CALL add_party_column_if_missing('suppliers', 'is_active',
                           'TINYINT(1) DEFAULT 1 NOT NULL COMMENT ''طرف موقوف يبقى بتاريخه ويخرج من الكومبوهات''');

-- الصفوف القائمة تأخذ تاريخ إدخالها، وهو ما كان الـ view يستعمله بالفعل - فلا يتحرك رقم لأحد.
UPDATE custom SET opening_balance_date = DATE(created_at) WHERE opening_balance_date IS NULL;
UPDATE suppliers SET opening_balance_date = DATE(created_at) WHERE opening_balance_date IS NULL;

-- فهارس ما تفلتر به الشاشات الجديدة.

CALL add_party_status_index('total_sales', 'total_sales_party_date_idx', 'sup_code, invoice_date');
CALL add_party_status_index('total_sales_re', 'total_sales_re_party_date_idx', 'sup_id, invoice_date');
CALL add_party_status_index('total_buy', 'total_buy_party_date_idx', 'sup_code, invoice_date');
CALL add_party_status_index('total_buy_re', 'total_buy_re_party_date_idx', 'sup_id, invoice_date');

-- والفلترة على الحالة والمنطقة معا، وهي ما تقرؤه الكومبوهات الجديدة.
CALL add_party_status_index('custom', 'custom_active_area_idx', 'is_active, area_id');
CALL add_party_status_index('suppliers', 'suppliers_active_area_idx', 'is_active, area_id');

DROP PROCEDURE IF EXISTS add_party_status_index;
DROP PROCEDURE IF EXISTS add_party_column_if_missing;
