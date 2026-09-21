-- =====================================================================
-- V77 - مخزن يُوقَف بدل أن يُحذف. المرحلة هـ1 من docs/warehouse-plan.md §10.
--
-- ### لماذا
--
-- حذف مخزن مستحيل عمليًا، وهذا صحيح: كل مخزن يحمل صفًا في `items_stock` لكل صنف منذ لحظة
-- إنشائه (StockService.save يملؤها)، و`DeleteRegistry.STOCKS` يعلن ذلك المرجع - فيُرفض الحذف
-- دائمًا بجملة تعدّ صفوف الأرصدة. فمخزن أُغلق فرعه أو استُبدل بقي في كل قائمة اختيار إلى
-- الأبد: في الفاتورة والتحويل والجرد والاستعلام عن الأسعار. `is_active` هو الجواب الذي أعطته
-- V56 للعملاء وV57 للموظفين: يُوقَف ولا يُمحى، ويبقى تاريخه كاملًا يُقرأ.
--
-- ### كل مخزن موجود يبقى نشطًا
--
-- القيمة الافتراضية 1، فالترقية لا تُخفي شيئًا عن أحد. والإيقاف قرار يتخذه صاحب المحل من شاشة
-- المخازن، تحت `stock.update`، ويُرفض لمخزن ما زال يحمل بضاعة (تُنقل أولًا بتحويل)، وللمخزن
-- الافتراضي، ولمخزن فيه جرد مسودة مفتوح - القواعد في StockService.setActive.
--
-- ### ولا فهرس
--
-- جدول المخازن صفوفه بالعشرات؛ القائمة تُقرأ كاملة وتُصفّى.
--
-- الإجراء المساعد معرَّف هنا ومحذوف في آخر الملف (MigrationHelperProcedureTest).
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_stock_is_active_if_missing$$
CREATE PROCEDURE add_stock_is_active_if_missing()
BEGIN
    DECLARE col_exists INT;

    SELECT COUNT(*)
    INTO col_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stocks'
      AND COLUMN_NAME = 'is_active';

    IF col_exists = 0 THEN
        ALTER TABLE stocks
            ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1
                COMMENT '0 once switched off: kept with its history, offered to no new document'
                AFTER stock_address;
    END IF;
END$$

DELIMITER ;

CALL add_stock_is_active_if_missing();

DROP PROCEDURE IF EXISTS add_stock_is_active_if_missing;
