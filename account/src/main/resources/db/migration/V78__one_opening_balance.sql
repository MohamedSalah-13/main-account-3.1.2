-- =====================================================================
-- V78 - رصيد أول مدة واحد للصنف في كل مخزن. المرحلة هـ2 من docs/warehouse-plan.md §10،
-- وهي تنفيذ القرار §9.2.
--
-- ### لماذا
--
-- كان لرصيد أول المدة ثلاثة أماكن:
--
-- - `items_stock.first_balance`: رصيد كل مخزن، وهو ما يقرؤه `quantity_items_table` منذ V18،
--   أي ما تعرضه كل شاشة وكل تقرير.
-- - `items.first_balance`: ما كانت شاشة الصنف تكتبه، ونسخة من المخزن 1 وحده.
-- - الـtrigger `after_items_update`: ينسخ الثاني فوق الأول للمخزن 1 عند **أي** تعديل على الصنف -
--   اسمه أو سعره أو صورته. فالنسختان لا تتفقان إلا بإطلاقه، وأي كتابة وصلت `items_stock`
--   مباشرةً كانت تُمحى بصمت عند أول حفظ للصنف لسبب آخر.
--
-- وقاعدة قفل الرصيد (`OpeningBalanceRegistry.ITEMS`) كانت تقارن بالنسخة الثانية وتعدّ حركات
-- الصنف في كل المخازن، فلا يُحمى رصيد مخزن ثانٍ أصلًا. صار السؤال يُسأل لكل مخزن وحده
-- (`WarehouseOpeningBalance`)، ولم يبقَ ما يقرأ عمود الصنف.
--
-- ### ما يُحذف
--
-- - الـtrigger `after_items_update`.
-- - `items.first_balance`.
-- - `items_stock.current_quantity`: كان يُكتب صفرًا عند كل إنشاء صف، ولم يقرأه شيء كرقم منذ
--   أن صار الرصيد محسوبًا لا مخزَّنًا.
-- - triggers تدقيق الأصناف الثلاثة، لأنها تذكر `NEW.first_balance`؛ يعيد `R__triggers.sql`
--   إنشاءها بعد هذا الملف مباشرةً بلا العمود - trigger يذكر عمودًا محذوفًا يُفشل كل كتابة
--   على الجدول إلى أن يُعاد إنشاؤه.
--
-- ### لا رقم يتحرك
--
-- ما يقرؤه النظام هو `items_stock`، فحذف النسخة الأخرى لا يغيّر رصيدًا على أي شاشة. إلا في حالة
-- واحدة: صنف ليس له صف في المخزن الافتراضي - وقد ملأت V18 هذه الصفوف، فهذه حالة صف أُدخل من
-- خارج البرنامج. عندها كان رصيده في `items` وحده لا يُقرأ من أي مكان، فيُنقل إلى صف جديد قبل أن
-- يُحذف العمود بدل أن يضيع معه.
--
-- الإجراء المساعد معرَّف هنا ومحذوف في آخر الملف (MigrationHelperProcedureTest).
-- =====================================================================

DROP TRIGGER IF EXISTS after_items_update;
DROP TRIGGER IF EXISTS audit_items_insert;
DROP TRIGGER IF EXISTS audit_items_update;
DROP TRIGGER IF EXISTS audit_items_delete;

DELIMITER $$

DROP PROCEDURE IF EXISTS drop_duplicate_opening_balances$$
CREATE PROCEDURE drop_duplicate_opening_balances()
BEGIN
    DECLARE item_column INT;
    DECLARE quantity_column INT;

    SELECT COUNT(*)
    INTO item_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'items'
      AND COLUMN_NAME = 'first_balance';

    IF item_column > 0 THEN
        -- The one case where the item row held a figure nothing else did.
        INSERT INTO items_stock (item_id, stock_id, first_balance)
        SELECT i.id, 1, i.first_balance
        FROM items i
                 LEFT JOIN items_stock ist ON ist.item_id = i.id AND ist.stock_id = 1
        WHERE ist.id IS NULL
          AND EXISTS (SELECT 1 FROM stocks WHERE stock_id = 1);

        ALTER TABLE items DROP COLUMN first_balance;
    END IF;

    SELECT COUNT(*)
    INTO quantity_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'items_stock'
      AND COLUMN_NAME = 'current_quantity';

    IF quantity_column > 0 THEN
        ALTER TABLE items_stock DROP COLUMN current_quantity;
    END IF;
END$$

DELIMITER ;

CALL drop_duplicate_opening_balances();

DROP PROCEDURE IF EXISTS drop_duplicate_opening_balances;

ALTER TABLE items_stock
    MODIFY COLUMN first_balance DECIMAL(14, 3) NOT NULL DEFAULT 0
        COMMENT 'The opening balance of this item in this warehouse - the only one since V78';
