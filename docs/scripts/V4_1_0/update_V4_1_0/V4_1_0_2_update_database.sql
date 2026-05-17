-- ======================================================
-- Migration Script from script.sql (old) to V4_1_0_1_tables.sql (new)
-- Description: Updates database schema and migrates data SAFELY (Idempotent)
-- ======================================================

-- ======================================================
-- 0. إعداد إجراءات مخزنة للتحقق الآمن قبل التعديل
-- ======================================================
DELIMITER $$

-- أ. إجراء لإضافة أو تعديل الأعمدة
DROP PROCEDURE IF EXISTS ManageColumn$$
CREATE PROCEDURE ManageColumn(IN t_name VARCHAR(255), IN c_name VARCHAR(255), IN col_def TEXT)
BEGIN
    DECLARE col_exists INT;
    SELECT COUNT(*) INTO col_exists FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t_name AND COLUMN_NAME = c_name;

    IF col_exists > 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' MODIFY COLUMN ', c_name, ' ', col_def);
    ELSE
        SET @query = CONCAT('ALTER TABLE ', t_name, ' ADD COLUMN ', c_name, ' ', col_def);
    END IF;
    PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;
END$$

-- ب. إجراء لتغيير اسم العمود بأمان (بدون خطأ إذا تم تغييره مسبقاً)
DROP PROCEDURE IF EXISTS RenameColumnSafe$$
CREATE PROCEDURE RenameColumnSafe(IN t_name VARCHAR(255), IN old_c VARCHAR(255), IN new_c VARCHAR(255), IN col_def TEXT)
BEGIN
    DECLARE old_exists INT; DECLARE new_exists INT;
    SELECT COUNT(*) INTO old_exists FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t_name AND COLUMN_NAME = old_c;
    SELECT COUNT(*) INTO new_exists FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t_name AND COLUMN_NAME = new_c;

    IF new_exists = 0 AND old_exists > 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' CHANGE COLUMN ', old_c, ' ', new_c, ' ', col_def);
        PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    ELSEIF new_exists > 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' MODIFY COLUMN ', new_c, ' ', col_def);
        PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$

-- ج. إجراء لإضافة وتحديث القيود (Constraints & Indexes)
DROP PROCEDURE IF EXISTS ManageConstraint$$
CREATE PROCEDURE ManageConstraint(IN t_name VARCHAR(255), IN c_name VARCHAR(255), IN const_def TEXT, IN c_type VARCHAR(50))
BEGIN
    DECLARE const_exists INT;

    IF c_type = 'UNIQUE' THEN
        SELECT COUNT(*) INTO const_exists FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t_name AND INDEX_NAME = c_name;
    ELSE
        SELECT COUNT(*) INTO const_exists FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = t_name AND CONSTRAINT_NAME = c_name;
    END IF;

    IF const_exists > 0 THEN
        IF c_type = 'UNIQUE' THEN SET @query = CONCAT('ALTER TABLE ', t_name, ' DROP INDEX ', c_name);
        ELSEIF c_type = 'FOREIGN KEY' THEN SET @query = CONCAT('ALTER TABLE ', t_name, ' DROP FOREIGN KEY ', c_name);
        ELSE SET @query = CONCAT('ALTER TABLE ', t_name, ' DROP CONSTRAINT ', c_name);
        END IF;
        PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;

    SET @query = CONCAT('ALTER TABLE ', t_name, ' ADD CONSTRAINT ', c_name, ' ', const_def);
    PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;
END$$

-- د. إجراء ترحيل البيانات وحذف الجداول القديمة بأمان
DROP PROCEDURE IF EXISTS MigrateDataSafe$$
CREATE PROCEDURE MigrateDataSafe()
BEGIN
    DECLARE tbl_exists INT;
    -- ترحيل أسعار العناصر
    SELECT COUNT(*) INTO tbl_exists FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='items_price';
    IF tbl_exists > 0 THEN
        SET @q1 = 'UPDATE items i JOIN items_price ip1 ON i.id = ip1.item_id AND ip1.price_id = 1 SET i.sel_price1 = ip1.sel_price where i.id != 0';
        PREPARE stmt1 FROM @q1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;
        SET @q2 = 'DROP TABLE items_price';
        PREPARE stmt2 FROM @q2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;
    END IF;

    -- نسخ احتياطي لـ processes_data
    SELECT COUNT(*) INTO tbl_exists FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='processes_data';
    IF tbl_exists > 0 THEN
        SET @q1 = 'CREATE TABLE IF NOT EXISTS processes_data_backup AS SELECT * FROM processes_data';
        PREPARE stmt1 FROM @q1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;
        SET @q2 = 'DROP TABLE processes_data';
        PREPARE stmt2 FROM @q2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;
    END IF;
END$$

DELIMITER ;

-- ======================================================
-- 1. بدء التنفيذ
-- ======================================================
START TRANSACTION;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------------
-- تعديل وتحديث الجداول (باستخدام الإجراءات المخزنة للتحقق)
-- -------------------------------
CALL ManageColumn('company', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('users', 'user_pass', 'VARCHAR(255) NULL');
CALL ManageColumn('users', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('users', 'users_activity_chk', 'CHECK (user_activity IN (0, 1))', 'CHECK');
CALL ManageConstraint('users', 'users_available_chk', 'CHECK (user_available IN (0, 1))', 'CHECK');

CALL ManageColumn('main_group', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageColumn('stocks', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('treasury', 'amount', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('treasury', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('type_price', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('units', 'value_d', 'DECIMAL(14, 3) NOT NULL DEFAULT 1');
CALL ManageColumn('units', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('employees', 'salary', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('employees', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('sub_group', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('suppliers', 'id', 'int AUTO_INCREMENT');
CALL ManageColumn('suppliers', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('suppliers', 'first_balance', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('suppliers', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('custom', 'id', 'int AUTO_INCREMENT');
CALL ManageColumn('custom', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('custom', 'limit_num', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('custom', 'first_balance', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL RenameColumnSafe('custom', 'date_insert', 'created_at', 'TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP');
CALL ManageColumn('custom', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('items', 'buy_price', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'sel_price1', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'sel_price2', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'sel_price3', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'mini_quantity', 'DECIMAL(14, 3) NOT NULL DEFAULT 1');
CALL ManageColumn('items', 'first_balance', 'DECIMAL(14, 3) NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'item_active', 'TINYINT(1) NOT NULL DEFAULT 1');
CALL ManageColumn('items', 'item_has_validity', 'TINYINT(1) NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'number_validity_days', 'INT NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'alert_days_before_expire', 'INT NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'item_has_package', 'TINYINT(1) NOT NULL DEFAULT 0');
CALL RenameColumnSafe('items', 'date_insert', 'created_at', 'TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP');
CALL ManageColumn('items', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('items_stock', 'first_balance', 'DECIMAL(14, 3) NOT NULL DEFAULT 0');
CALL ManageColumn('items_stock', 'current_quantity', 'DECIMAL(14, 3) NOT NULL DEFAULT 0');
CALL ManageConstraint('items_stock', 'items_stock_uk', 'UNIQUE (item_id, stock_id)', 'UNIQUE');

CALL ManageColumn('items_units', 'quantity', 'DECIMAL(14, 3) NOT NULL');
CALL ManageColumn('items_units', 'buy_price', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('items_units', 'sel_price', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('items_units', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('items_units', 'items_units_quantity_chk', 'CHECK (quantity > 0)', 'CHECK');

CALL ManageColumn('treasury_deposit_expenses', 'amount', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('treasury_deposit_expenses', 'deposit_or_expenses', 'TINYINT NOT NULL DEFAULT 1');
CALL ManageColumn('treasury_deposit_expenses', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('treasury_deposit_expenses', 'treasury_deposit_expenses_type_chk', 'CHECK (deposit_or_expenses IN (1, 2))', 'CHECK');

CALL ManageColumn('treasury_transfers', 'amount', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('treasury_transfers', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('treasury_transfers', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('treasury_transfers', 'treasury_transfers_not_same_chk', 'CHECK (treasury_from <> treasury_to)', 'CHECK');
CALL ManageConstraint('treasury_transfers', 'treasury_transfers_amount_chk', 'CHECK (amount > 0)', 'CHECK');

CALL ManageColumn('expenses_details', 'amount', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('expenses_details', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('expenses_details', 'expenses_details_amount_chk', 'CHECK (amount >= 0)', 'CHECK');

CALL ManageColumn('total_buy', 'invoice_number', 'BIGINT NOT NULL');
CALL ManageColumn('total_buy', 'discount', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_buy', 'paid_up', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_buy', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('total_buy', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('total_buy', 'total_buy_invoice_type_chk', 'CHECK (invoice_type IN (1, 2))', 'CHECK');

CALL ManageColumn('total_buy_re', 'id', 'BIGINT NOT NULL');
CALL ManageColumn('total_buy_re', 'discount', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_buy_re', 'paid_to_treasury', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_buy_re', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('total_buy_re', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('total_buy_re', 'total_buy_re_invoice_type_chk', 'CHECK (invoice_type IN (1, 2))', 'CHECK');

CALL ManageColumn('total_sales', 'invoice_number', 'BIGINT NOT NULL');
CALL ManageColumn('total_sales', 'discount', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_sales', 'paid_up', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_sales', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('total_sales', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('total_sales', 'total_sales_invoice_type_chk', 'CHECK (invoice_type IN (1, 2))', 'CHECK');

CALL ManageColumn('total_sales_re', 'id', 'BIGINT NOT NULL');
CALL ManageColumn('total_sales_re', 'discount', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_sales_re', 'paid_from_treasury', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_sales_re', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('total_sales_re', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('total_sales_re', 'total_sales_re_invoice_type_chk', 'CHECK (invoice_type IN (1, 2))', 'CHECK');

CALL ManageColumn('suppliers_accounts', 'account_num', 'BIGINT AUTO_INCREMENT');
CALL ManageColumn('suppliers_accounts', 'purchase', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('suppliers_accounts', 'paid', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('suppliers_accounts', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('suppliers_accounts', 'invoice_number_return', 'BIGINT NOT NULL DEFAULT 0');
CALL ManageColumn('suppliers_accounts', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('customers_accounts', 'account_num', 'BIGINT AUTO_INCREMENT');
CALL ManageColumn('customers_accounts', 'paid', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('customers_accounts', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('customers_accounts', 'purchase', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('customers_accounts', 'invoice_number_return', 'BIGINT NOT NULL DEFAULT 0');
CALL RenameColumnSafe('customers_accounts', 'date_insert', 'created_at', 'TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP');
CALL ManageColumn('customers_accounts', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('purchase', 'invoice_number', 'BIGINT NOT NULL');
CALL ManageColumn('purchase', 'quantity', 'DECIMAL(14, 3) NOT NULL');
CALL ManageColumn('purchase', 'price', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('purchase', 'discount', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('purchase', 'type_value', 'DECIMAL(14, 3) NOT NULL DEFAULT 1');
CALL ManageColumn('purchase', 'expiration_date', 'DATE NULL');
CALL ManageConstraint('purchase', 'purchase_quantity_chk', 'CHECK (quantity > 0)', 'CHECK');

CALL ManageColumn('purchase_re', 'invoice_number', 'BIGINT NOT NULL');
CALL ManageColumn('purchase_re', 'quantity', 'DECIMAL(14, 3) NOT NULL');
CALL ManageColumn('purchase_re', 'price', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('purchase_re', 'discount', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('purchase_re', 'type_value', 'DECIMAL(14, 3) NOT NULL DEFAULT 1');
CALL ManageColumn('purchase_re', 'expiration_date', 'DATE NULL');
CALL ManageConstraint('purchase_re', 'purchase_re_quantity_chk', 'CHECK (quantity > 0)', 'CHECK');

CALL ManageColumn('sales', 'invoice_number', 'BIGINT NOT NULL');
CALL ManageColumn('sales', 'quantity', 'DECIMAL(14, 3) NOT NULL');
CALL ManageColumn('sales', 'price', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('sales', 'buy_price', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('sales', 'total_sel_price', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales', 'total_buy_price', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales', 'total_profit', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales', 'discount', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales', 'type_value', 'DECIMAL(14, 3) NOT NULL DEFAULT 1');
CALL ManageColumn('sales', 'expiration_date', 'DATE NULL');
CALL ManageColumn('sales', 'item_has_package', 'TINYINT(1) NOT NULL DEFAULT 0');
CALL ManageConstraint('sales', 'sales_quantity_chk', 'CHECK (quantity > 0)', 'CHECK');

CALL ManageColumn('sales_re', 'invoice_number', 'BIGINT NOT NULL');
CALL ManageColumn('sales_re', 'quantity', 'DECIMAL(14, 3) NOT NULL');
CALL ManageColumn('sales_re', 'price', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('sales_re', 'buy_price', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales_re', 'total_sel_price', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales_re', 'total_buy_price', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales_re', 'total_profit', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales_re', 'discount', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales_re', 'type_value', 'DECIMAL(14, 3) NOT NULL DEFAULT 1');
CALL ManageColumn('sales_re', 'expiration_date', 'DATE NULL');
CALL ManageConstraint('sales_re', 'sales_re_quantity_chk', 'CHECK (quantity > 0)', 'CHECK');

CALL ManageColumn('targeted_sales', 'target', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('targeted_sales', 'target_ratio1', 'DECIMAL(6, 2) NOT NULL DEFAULT 100');
CALL ManageColumn('targeted_sales', 'rate_1', 'DECIMAL(6, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('targeted_sales', 'target_ratio2', 'DECIMAL(6, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('targeted_sales', 'rate_2', 'DECIMAL(6, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('targeted_sales', 'target_ratio3', 'DECIMAL(6, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('targeted_sales', 'rate_3', 'DECIMAL(6, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('targeted_sales', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('user_permission', 'check_status', 'TINYINT NOT NULL DEFAULT 0');
CALL ManageColumn('user_permission', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('user_permission', 'user_permission_uk', 'UNIQUE (permission_id, user_id)', 'UNIQUE');
CALL ManageConstraint('user_permission', 'user_permission_chk', 'CHECK (check_status IN (0, 1))', 'CHECK');

-- ======================================================
-- 2. تنفيذ ترحيل البيانات وحذف الجداول القديمة بآمان
-- ======================================================
CALL MigrateDataSafe();

-- ======================================================
-- 3. إنشاء الجداول الجديدة (فقط إذا لم تكن موجودة)
-- ======================================================

CREATE TABLE IF NOT EXISTS items_package
(
    id         INT AUTO_INCREMENT PRIMARY KEY,
    item_id    INT                                NOT NULL,
    package_id INT                                NOT NULL,
    quantity   DECIMAL(14, 3)                     NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT items_package_item_id_fk FOREIGN KEY (item_id) REFERENCES items (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT items_package_package_id_fk FOREIGN KEY (package_id) REFERENCES items (id) ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT items_package_quantity_chk CHECK (quantity > 0)
);
-- إنشاء الفهارس (تفشل تلقائياً إن وجدت، وهو السلوك المطلوب أو لا تسبب ضرر إذا وضعنا IF NOT EXISTS)

CREATE TABLE IF NOT EXISTS stock_movements
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id           INT                                      NOT NULL,
    stock_id          INT                                      NOT NULL,
    movement_date     DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    movement_type     VARCHAR(30)                              NOT NULL,
    quantity_in       DECIMAL(15, 3) DEFAULT 0                 NOT NULL,
    quantity_out      DECIMAL(15, 3) DEFAULT 0                 NOT NULL,
    unit_id           INT                                      NULL,
    unit_value        DECIMAL(15, 3) DEFAULT 1                 NOT NULL,
    reference_type    VARCHAR(30)                              NULL,
    reference_id      BIGINT                                   NULL,
    reference_line_id BIGINT                                   NULL,
    notes             TEXT                                     NULL,
    user_id           INT            DEFAULT 1                 NOT NULL,
    CONSTRAINT stock_movements_items_id_fk FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT stock_movements_stocks_stock_id_fk FOREIGN KEY (stock_id) REFERENCES stocks (stock_id),
    CONSTRAINT stock_movements_units_unit_id_fk FOREIGN KEY (unit_id) REFERENCES units (unit_id),
    CONSTRAINT stock_movements_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT stock_movements_quantity_chk CHECK ( (quantity_in > 0 AND quantity_out = 0) OR (quantity_in = 0 AND quantity_out > 0) ),
    CONSTRAINT stock_movements_unit_value_chk CHECK (unit_value > 0),
    CONSTRAINT stock_movements_type_chk CHECK (movement_type IN ('OPENING', 'PURCHASE', 'PURCHASE_RETURN', 'SALE', 'SALE_RETURN', 'TRANSFER_IN', 'TRANSFER_OUT', 'INVENTORY_ADJUST_IN', 'INVENTORY_ADJUST_OUT')),
    CONSTRAINT stock_movements_reference_type_chk CHECK (reference_type IS NULL OR reference_type IN ('ITEM', 'PURCHASE', 'PURCHASE_RETURN', 'SALE', 'SALE_RETURN', 'STOCK_TRANSFER', 'INVENTORY'))
);

-- تهيئة stock_movements للمرة الأولى فقط
INSERT INTO stock_movements (item_id, stock_id, movement_date, movement_type, quantity_in, user_id)
SELECT is2.item_id, is2.stock_id, NOW(), 'OPENING', is2.first_balance, 1
FROM items_stock is2
WHERE is2.first_balance > 0 AND NOT EXISTS (SELECT 1 FROM stock_movements);

CREATE TABLE IF NOT EXISTS user_shifts
(
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    user_id             INT                      NOT NULL,
    open_time           DATETIME                 NOT NULL,
    close_time          DATETIME                 NULL,
    open_balance        DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    close_balance       DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_sales         DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_sales_returns DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_expenses      DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_deposits      DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_withdrawals   DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    expected_balance    DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    difference          DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    invoices_count      INT            DEFAULT 0 NOT NULL,
    is_open             BOOLEAN        DEFAULT TRUE,
    notes               TEXT                     NULL,
    CONSTRAINT user_shifts_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS audit_log
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name  VARCHAR(100)                          NOT NULL,
    record_id   VARCHAR(100)                          NULL,
    action_type VARCHAR(20)                           NOT NULL,
    user_id     INT                                   NULL,
    action_time DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    old_data    JSON                                  NULL,
    new_data    JSON                                  NULL,
    source      VARCHAR(50) DEFAULT 'APP'             NOT NULL,
    notes       TEXT                                  NULL,
    CONSTRAINT audit_log_action_chk CHECK (action_type IN ('INSERT', 'UPDATE', 'DELETE')),
    CONSTRAINT audit_log_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

-- قيود Stock Transfer الجديدة
CALL ManageConstraint('stock_transfer', 'stock_transfer_from_fk', 'FOREIGN KEY (stock_from) REFERENCES stocks (stock_id)', 'FOREIGN KEY');
CALL ManageConstraint('stock_transfer', 'stock_transfer_to_fk', 'FOREIGN KEY (stock_to) REFERENCES stocks (stock_id)', 'FOREIGN KEY');
CALL ManageConstraint('stock_transfer', 'stock_transfer_not_same_chk', 'CHECK (stock_from <> stock_to)', 'CHECK');

CALL ManageConstraint('stock_transfer_list', 'stock_transfer_list_items_id_fk', 'FOREIGN KEY (item_id) REFERENCES items (id)', 'FOREIGN KEY');
CALL ManageConstraint('stock_transfer_list', 'stock_transfer_list_quantity_chk', 'CHECK (quantity > 0)', 'CHECK');

-- ======================================================
-- 4. عمليات التحديث الختامية
-- ======================================================

UPDATE items_stock ist
SET current_quantity = (SELECT COALESCE(SUM(quantity_in) - SUM(quantity_out), 0)
                        FROM stock_movements sm
                        WHERE sm.item_id = ist.item_id
                          AND sm.stock_id = ist.stock_id);

TRUNCATE TABLE type_price;
INSERT INTO type_price (name) VALUES ('سعر1'), ('سعر2'), ('سعر3');

DROP TABLE IF EXISTS `sales_package`;
# DROP TABLE IF EXISTS `processes_data_backup`;

CREATE TABLE IF NOT EXISTS treasury_movements
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    treasury_id    INT                                      NOT NULL,
    movement_date  DATE                                     NOT NULL,

    movement_type  VARCHAR(50)                              NOT NULL,

    amount_in      DECIMAL(14, 2) DEFAULT 0                 NOT NULL,
    amount_out     DECIMAL(14, 2) DEFAULT 0                 NOT NULL,
    balance_after  DECIMAL(14, 2) DEFAULT 0                 NOT NULL,

    reference_type VARCHAR(50)                              NULL,
    reference_id   BIGINT                                   NULL,

    notes          TEXT                                     NULL,
    date_insert    DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id        INT            DEFAULT 1                 NOT NULL,

    CONSTRAINT treasury_movements_treasury_id_fk
        FOREIGN KEY (treasury_id) REFERENCES treasury (id),

    CONSTRAINT treasury_movements_users_id_fk
        FOREIGN KEY (user_id) REFERENCES users (id),

    CONSTRAINT treasury_movements_amount_chk
        CHECK (
            (amount_in > 0 AND amount_out = 0)
                OR
            (amount_in = 0 AND amount_out > 0)
            ),

    CONSTRAINT treasury_movements_type_chk
        CHECK (movement_type IN (
                                 'OPENING',
                                 'DEPOSIT',
                                 'WITHDRAWAL',
                                 'TRANSFER_IN',
                                 'TRANSFER_OUT',
                                 'SALE',
                                 'SALE_RETURN',
                                 'PURCHASE',
                                 'PURCHASE_RETURN',
                                 'EXPENSE',
                                 'ADJUSTMENT_IN',
                                 'ADJUSTMENT_OUT'
            )),

    CONSTRAINT treasury_movements_reference_type_chk
        CHECK (
            reference_type IS NULL
                OR reference_type IN (
                                      'TREASURY',
                                      'TREASURY_DEPOSIT_EXPENSES',
                                      'TREASURY_TRANSFER',
                                      'SALE',
                                      'SALE_RETURN',
                                      'PURCHASE',
                                      'PURCHASE_RETURN',
                                      'EXPENSE',
                                      'ADJUSTMENT'
                )
            )
);

CREATE INDEX treasury_movements_treasury_date_idx
    ON treasury_movements (treasury_id, movement_date, id);

CREATE INDEX treasury_movements_reference_idx
    ON treasury_movements (reference_type, reference_id);

CREATE INDEX treasury_movements_date_idx
    ON treasury_movements (movement_date);

#=========================================
CREATE TABLE IF NOT EXISTS system_info (
                                           id INT PRIMARY KEY,
                                           client_code VARCHAR(50),
                                           client_name VARCHAR(255),
                                           app_version VARCHAR(50),
                                           database_version VARCHAR(50),
                                           install_date DATETIME,
                                           last_update DATETIME,
                                           database_name VARCHAR(100),
                                           server_ip VARCHAR(100),
                                           license_key VARCHAR(255),
                                           notes TEXT
);

CREATE TABLE IF NOT EXISTS database_migrations (
                                                   id INT AUTO_INCREMENT PRIMARY KEY,
                                                   version VARCHAR(50) NOT NULL UNIQUE,
                                                   description VARCHAR(255),
                                                   executed_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_info (
    id,
    client_code,
    client_name,
    app_version,
    database_version,
    install_date,
    last_update,
    notes
)
SELECT
    1,
    'CLIENT-001',
    'Default Client',
    '4.1.0',
    '4.1.0',
    NOW(),
    NOW(),
    'Initial system info'
WHERE NOT EXISTS (
    SELECT 1 FROM system_info WHERE id = 1
);

#==============================================================
-- إرجاع الفحص وإنهاء المعاملة
SET FOREIGN_KEY_CHECKS = 1;
COMMIT;

-- تنظيف قاعدة البيانات من الإجراءات المؤقتة
DROP PROCEDURE IF EXISTS ManageColumn;
DROP PROCEDURE IF EXISTS RenameColumnSafe;
DROP PROCEDURE IF EXISTS ManageConstraint;
DROP PROCEDURE IF EXISTS MigrateDataSafe;

-- ======================================================
-- نهاية السكريبت
-- ======================================================