-- =====================================================================
-- V68: رسوم التحويل بين الخزائن تعرف تحويلها
-- =====================================================================
--
-- تحويل من محفظة إلى البنك أو إلى الدرج تأخذ عنه المحفظة رسوما، والشاشة لم يكن فيها مكان لها:
-- كانت تُسجَّل مصروفا باليد بعد التحويل أو لا تُسجَّل، فيسبق رصيد الخزينة رصيد المحفظة الفعلي.
--
-- الرسوم مصروف على **خزينة المصدر** في معاملة التحويل نفسها - قاعدة عمولة التحصيل ذاتها
-- (`docs/treasury-plan.md` §17 و§19) - وصفها يحمل ربط V67. و`fee_source_type` يأخذ هنا رقم
-- `ShiftCashSource.TRANSFER_OUT` = 11: الرسوم تخص الطرف الخارج من التحويل.
--
-- V67 قيّد النوع بـ 1-6، فهذه الهجرة تبدّل القيد وحده. **V67 لا تُعدَّل**: وصلت `main`، ونسخة
-- شغّلتها لن تشغّلها ثانية.

DELIMITER $$

DROP PROCEDURE IF EXISTS replace_fee_source_check$$
CREATE PROCEDURE replace_fee_source_check()
BEGIN
    DECLARE con_exists INT;

    SELECT COUNT(*)
    INTO con_exists
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'expenses_details'
      AND CONSTRAINT_NAME = 'expenses_details_fee_source_chk';

    IF con_exists > 0 THEN
        ALTER TABLE expenses_details DROP CHECK expenses_details_fee_source_chk;
    END IF;

    ALTER TABLE expenses_details
        ADD CONSTRAINT expenses_details_fee_source_chk
            CHECK ((fee_source_type IS NULL AND fee_source_id IS NULL)
                OR (fee_source_type IN (1, 2, 3, 4, 5, 6, 11) AND fee_source_id > 0));
END$$

DELIMITER ;

CALL replace_fee_source_check();

DROP PROCEDURE IF EXISTS replace_fee_source_check;
