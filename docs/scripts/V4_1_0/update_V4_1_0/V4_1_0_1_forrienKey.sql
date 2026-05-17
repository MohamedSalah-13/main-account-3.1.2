-- ======================================================
-- Migration Script (fixed for foreign key compatibility)
-- ======================================================

-- 0. إنشاء إجراء مخزن لحذف المفاتيح الأجنبية بأمان (بدون أخطاء إذا لم تكن موجودة)
DELIMITER $$

DROP PROCEDURE IF EXISTS DropForeignKeyIfExists$$

CREATE PROCEDURE DropForeignKeyIfExists(
    IN target_table VARCHAR(255),
    IN fk_name VARCHAR(255)
)
BEGIN
    DECLARE fk_exists INT;

    -- التحقق من وجود المفتاح في قاعدة البيانات الحالية
    SELECT COUNT(*)
    INTO fk_exists
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = target_table
      AND CONSTRAINT_NAME = fk_name
      AND CONSTRAINT_TYPE = 'FOREIGN KEY';

    -- إذا وجد المفتاح، قم بحذفه
    IF fk_exists > 0 THEN
        SET @query = CONCAT('ALTER TABLE ', target_table, ' DROP FOREIGN KEY ', fk_name);
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- بدء المعاملة بعد تهيئة الإجراء المخزن
START TRANSACTION;

-- 1. Drop foreign keys that reference columns we will modify (تم التعديل للحذف الآمن)
CALL DropForeignKeyIfExists('purchase', 'purchase_total_buy_invoice_number_fk');
CALL DropForeignKeyIfExists('sales', 'sales_total_invoice_number_fk');
CALL DropForeignKeyIfExists('purchase_re', 'purchase_re_total_buy_re_id_fk');
CALL DropForeignKeyIfExists('sales_re', 'sales_re_total_sales_re_id_fk');
CALL DropForeignKeyIfExists('total_buy_re', 'total_buy_re_total_buy_invoice_number_fk');
CALL DropForeignKeyIfExists('total_buy_re', 'total_buy_re_suppliers_sup_id_fk');
CALL DropForeignKeyIfExists('total_sales_re', 'total_sales_re_custom_id_fk');
CALL DropForeignKeyIfExists('suppliers_accounts', 'suppliers_accounts_suppliers_id_fk');
CALL DropForeignKeyIfExists('customers_accounts', 'customers_accounts_custom_id_fk');

-- 2. Disable foreign key checks temporarily
SET FOREIGN_KEY_CHECKS = 0;

-- 3. Modify column types in parent tables first
ALTER TABLE total_buy MODIFY invoice_number BIGINT NOT NULL;
ALTER TABLE total_sales MODIFY invoice_number BIGINT NOT NULL;
ALTER TABLE total_buy_re MODIFY id BIGINT NOT NULL;
ALTER TABLE total_sales_re MODIFY id BIGINT NOT NULL;
ALTER TABLE suppliers MODIFY id INT NOT NULL;
ALTER TABLE custom MODIFY id INT NOT NULL;

-- 4. Modify child tables
ALTER TABLE purchase MODIFY invoice_number BIGINT NOT NULL;
ALTER TABLE sales MODIFY invoice_number BIGINT NOT NULL;
ALTER TABLE purchase_re MODIFY invoice_number BIGINT NOT NULL;
ALTER TABLE sales_re MODIFY invoice_number BIGINT NOT NULL;

-- 5. Re-create foreign keys
ALTER TABLE purchase ADD CONSTRAINT purchase_total_buy_invoice_number_fk
    FOREIGN KEY (invoice_number) REFERENCES total_buy(invoice_number) ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE sales ADD CONSTRAINT sales_total_invoice_number_fk
    FOREIGN KEY (invoice_number) REFERENCES total_sales(invoice_number) ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE purchase_re ADD CONSTRAINT purchase_re_total_buy_re_id_fk
    FOREIGN KEY (invoice_number) REFERENCES total_buy_re(id) ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE sales_re ADD CONSTRAINT sales_re_total_sales_re_id_fk
    FOREIGN KEY (invoice_number) REFERENCES total_sales_re(id) ON UPDATE CASCADE ON DELETE CASCADE;
# ALTER TABLE total_buy_re ADD CONSTRAINT total_buy_re_total_buy_invoice_number_fk
#     FOREIGN KEY (total_buy_id) REFERENCES total_buy(invoice_number) ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE total_buy_re ADD CONSTRAINT total_buy_re_suppliers_sup_id_fk
    FOREIGN KEY (sup_id) REFERENCES suppliers(id);
ALTER TABLE total_sales_re ADD CONSTRAINT total_sales_re_custom_id_fk
    FOREIGN KEY (sup_id) REFERENCES custom(id);
ALTER TABLE suppliers_accounts ADD CONSTRAINT suppliers_accounts_suppliers_id_fk
    FOREIGN KEY (account_code) REFERENCES suppliers(id);
ALTER TABLE customers_accounts ADD CONSTRAINT customers_accounts_custom_id_fk
    FOREIGN KEY (account_code) REFERENCES custom(id);

-- 6. Re-enable foreign key checks and commit
SET FOREIGN_KEY_CHECKS = 1;
COMMIT;

-- 7. تنظيف قاعدة البيانات بحذف الإجراء المخزن بعد انتهاء استخدامه
DROP PROCEDURE IF EXISTS DropForeignKeyIfExists;