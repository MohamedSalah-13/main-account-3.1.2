-- =====================================================================
-- V76 - ملاحظة على رأس التحويل المخزني. المرحلة د1 من docs/warehouse-plan.md §10.
--
-- ### لماذا
--
-- التحويل يقول من أين وإلى أين ومتى وماذا، ولا يقول **لماذا** ولا **لمن**: «بضاعة معرض
-- الشتاء»، «مرتجع من الفرع للمخزن الرئيسي»، «تسليم للسائق محمود». هذا ما يُكتب على إيصال
-- التحويل الذي يرافق البضاعة، وما يبحث عنه أمين المخزن بعد شهرين حين يسأل أحد أين ذهبت.
-- الخزينة تملك العمود نفسه على تحويلاتها منذ البداية (`treasury_transfers.notes`).
--
-- ### NULL تعني بلا ملاحظة
--
-- لا قيمة افتراضية ولا ترحيل: كل تحويل قبل هذه الـmigration بلا ملاحظة، وهذا صحيح - لم
-- يكتب أحد شيئًا. `StockTransferCommand` يحوّل الفراغ إلى NULL، فلا يوجد معنيان لـ«لا شيء».
--
-- ### 255 حرفًا
--
-- ملاحظة لا مستند: سطر يُطبع تحت جدول الإيصال. `StockTransferService` يرفض ما يزيد برسالة
-- يقرؤها المستخدم قبل أن تصل القاعدة، والحقل على الشاشة لا يقبل أكثر من ذلك أصلًا.
--
-- ### ولا تدقيق للعمود
--
-- `audit_stock_transfer_insert` و`_delete` في `R__triggers.sql` لا يذكران الملاحظة، عمدًا:
-- `DelegateActivityDatabaseAcceptanceTest` يبني قاعدة V70 وينفّذ ملف الـtriggers حتى قسم
-- العمولة، وMySQL يرفض trigger يذكر عمودًا لا يوجد بعد. والتحويل لا يُعدَّل بعد ترحيله،
-- فالملاحظة لا تتغير أبدًا ويقرؤها السجل نفسه.
--
-- الإجراء المساعد معرَّف هنا ومحذوف في آخر الملف (`MigrationHelperProcedureTest`): لا
-- `add_column_if_missing` في أي هجرة أساسية.
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_transfer_notes_if_missing$$
CREATE PROCEDURE add_transfer_notes_if_missing()
BEGIN
    DECLARE col_exists INT;

    SELECT COUNT(*)
    INTO col_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_transfer'
      AND COLUMN_NAME = 'notes';

    IF col_exists = 0 THEN
        ALTER TABLE stock_transfer
            ADD COLUMN notes VARCHAR(255) NULL
                COMMENT 'Why or for whom the goods moved; NULL when nothing was written'
                AFTER stock_to;
    END IF;
END$$

DELIMITER ;

CALL add_transfer_notes_if_missing();

DROP PROCEDURE IF EXISTS add_transfer_notes_if_missing;
