-- =====================================================================
-- V69: رقم المحفظة أو الحساب، والحد الأدنى لرصيد الخزينة
-- =====================================================================
--
-- خزينة نوعها محفظة أو بنك كانت اسما ونسبة عمولة فقط: لا رقم المحفظة الذي يُحوَّل عليه، ولا رقم
-- الحساب البنكي، فكان الاسم يحمل الرقم («فودافون 0100...») أو يُحفظ في ورقة. ولا شيء ينبّه
-- حين ينزل الدرج عن القدر الذي يلزم لرد الباقي، أو المحفظة عن القدر الذي يلزم لدفعة مورد.
--
--   account_number  نص حر: رقم محفظة، أو رقم حساب، أو IBAN. NULL لدرج النقدية. ليس فريدا -
--                   محفظة واحدة قد تُفتح لها خزينتان عمدا، ومنع ذلك ليس من شأن المخطط.
--   min_balance     الحد الذي يُنبَّه تحته. صفر = لا حد، وهو ما عليه كل خزينة قائمة، فلا تنبيه
--                   جديد يظهر لأحد بعد الترقية. **تنبيه لا رفض**: السحب ما زال يُرفض عند تجاوز
--                   الرصيد نفسه فقط (`requireEnough`)، والحد الأدنى رأي لا قاعدة.
--
-- لا يُقرأ أيٌّ منهما في `treasury_current_balance`: الرصيد تعريفه واحد ولا يتغير، والتنبيه يضم
-- الجدول إلى الـview بنفسه.

DELIMITER $$

DROP PROCEDURE IF EXISTS add_treasury_detail_column_if_missing$$
CREATE PROCEDURE add_treasury_detail_column_if_missing(IN c_name VARCHAR(64), IN col_def TEXT)
BEGIN
    DECLARE col_exists INT;

    SELECT COUNT(*)
    INTO col_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'treasury'
      AND COLUMN_NAME = c_name;

    IF col_exists = 0 THEN
        SET @query = CONCAT('ALTER TABLE treasury ADD COLUMN ', c_name, ' ', col_def);
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_treasury_detail_column_if_missing('account_number',
     'VARCHAR(60) NULL COMMENT ''رقم المحفظة أو الحساب البنكي؛ NULL لدرج النقدية''');
CALL add_treasury_detail_column_if_missing('min_balance',
     'DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT ''الحد الذي يُنبَّه تحته؛ صفر = لا حد''');

DROP PROCEDURE IF EXISTS add_treasury_detail_column_if_missing;
