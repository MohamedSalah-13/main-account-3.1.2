-- ============================================================
-- تحديث قاعدة البيانات من الإصدار القديم إلى الجديد
-- قم بتشغيل هذا السكريبت على قاعدة بيانات العميل الموجودة
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. تعديل الترميز إلى utf8mb4 (اختياري لكن مستحسن)
ALTER DATABASE account_system_db CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================
-- 2. تعديل أنواع الأعمدة (DECIMAL بدلاً من DOUBLE و INT إلى BIGINT)
-- ============================================================

-- تعديل طول كلمة المرور
ALTER TABLE users
    MODIFY user_pass VARCHAR(255) NULL;

-- إضافة قيود CHECK في users (لن تمنع البيانات الحالية في حال وجود قيم غير 0/1)
ALTER TABLE users
    ADD CONSTRAINT users_activity_chk CHECK (user_activity IN (0, 1));
ALTER TABLE users
    ADD CONSTRAINT users_available_chk CHECK (user_available IN (0, 1));

-- treasury
ALTER TABLE treasury
    MODIFY amount DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE treasury
    MODIFY t_name VARCHAR(50) NOT NULL;

-- units
ALTER TABLE units
    MODIFY value_d DECIMAL(14, 3) NOT NULL DEFAULT 1;

-- employees
ALTER TABLE employees
    MODIFY salary DECIMAL(14, 2) NOT NULL;

-- suppliers
ALTER TABLE suppliers
    MODIFY first_balance DECIMAL(14, 2) NOT NULL DEFAULT 0;

-- custom
ALTER TABLE custom
    MODIFY limit_num DECIMAL(14, 2) NOT NULL;
ALTER TABLE custom
    MODIFY first_balance DECIMAL(14, 2) NOT NULL DEFAULT 0;

-- items
ALTER TABLE items
    MODIFY buy_price DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE items
    MODIFY sel_price1 DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE items
    MODIFY sel_price2 DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE items
    MODIFY sel_price3 DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE items
    MODIFY mini_quantity DECIMAL(14, 3) NOT NULL DEFAULT 1;
ALTER TABLE items
    MODIFY first_balance DECIMAL(14, 3) NOT NULL DEFAULT 0;

-- items_package
ALTER TABLE items_package
    MODIFY quantity DECIMAL(14, 3) NOT NULL;
ALTER TABLE items_package
    ADD CONSTRAINT items_package_quantity_chk CHECK (quantity > 0);

-- items_units
ALTER TABLE items_units
    MODIFY quantity DECIMAL(14, 3) NOT NULL;
ALTER TABLE items_units
    MODIFY buy_price DECIMAL(14, 2) NOT NULL;
ALTER TABLE items_units
    MODIFY sel_price DECIMAL(14, 2) NOT NULL;
ALTER TABLE items_units
    ADD CONSTRAINT items_units_quantity_chk CHECK (quantity > 0);

-- stock_transfer_list
ALTER TABLE stock_transfer_list
    MODIFY quantity DECIMAL(14, 3) NOT NULL;

-- treasury_deposit_expenses
ALTER TABLE treasury_deposit_expenses
    MODIFY amount DECIMAL(14, 2) NOT NULL;
ALTER TABLE treasury_deposit_expenses
    ADD CONSTRAINT treasury_deposit_expenses_type_chk CHECK (deposit_or_expenses IN (1, 2));

-- treasury_transfers
ALTER TABLE treasury_transfers
    MODIFY amount DECIMAL(14, 2) NOT NULL;
ALTER TABLE treasury_transfers
    ADD CONSTRAINT treasury_transfers_not_same_chk CHECK (treasury_from <> treasury_to);
ALTER TABLE treasury_transfers
    ADD CONSTRAINT treasury_transfers_amount_chk CHECK (amount > 0);

-- expenses_details
ALTER TABLE expenses_details
    MODIFY amount DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE expenses_details
    ADD CONSTRAINT expenses_details_amount_chk CHECK (amount >= 0);

-- total_buy و التوابع
ALTER TABLE total_buy
    MODIFY total DECIMAL(14, 2) NOT NULL;
ALTER TABLE total_buy
    MODIFY discount DECIMAL(14, 2) NOT NULL;
ALTER TABLE total_buy
    MODIFY paid_up DECIMAL(14, 2) NOT NULL COMMENT 'paid from the treasury مدفوع نقدا من الخزينة';
ALTER TABLE total_buy
    MODIFY invoice_number BIGINT NOT NULL;
ALTER TABLE total_buy
    ADD CONSTRAINT total_buy_invoice_type_chk CHECK (invoice_type IN (1, 2));

ALTER TABLE total_buy_re
    MODIFY total DECIMAL(14, 2) NOT NULL;
ALTER TABLE total_buy_re
    MODIFY discount DECIMAL(14, 2) NOT NULL;
ALTER TABLE total_buy_re
    MODIFY paid_to_treasury DECIMAL(14, 2) NOT NULL;
ALTER TABLE total_buy_re
    MODIFY id BIGINT NOT NULL;
ALTER TABLE total_buy_re
    ADD CONSTRAINT total_buy_re_invoice_type_chk CHECK (invoice_type IN (1, 2));

ALTER TABLE total_sales
    MODIFY total DECIMAL(14, 2) NOT NULL;
ALTER TABLE total_sales
    MODIFY discount DECIMAL(14, 2) NOT NULL;
ALTER TABLE total_sales
    MODIFY paid_up DECIMAL(14, 2) NOT NULL;
ALTER TABLE total_sales
    MODIFY invoice_number BIGINT NOT NULL;
ALTER TABLE total_sales
    ADD CONSTRAINT total_sales_invoice_type_chk CHECK (invoice_type IN (1, 2));

ALTER TABLE total_sales_re
    MODIFY total DECIMAL(14, 2) NOT NULL;
ALTER TABLE total_sales_re
    MODIFY discount DECIMAL(14, 2) NOT NULL;
ALTER TABLE total_sales_re
    MODIFY paid_from_treasury DECIMAL(14, 2) NOT NULL;
ALTER TABLE total_sales_re
    MODIFY id BIGINT NOT NULL;
ALTER TABLE total_sales_re
    ADD CONSTRAINT total_sales_re_invoice_type_chk CHECK (invoice_type IN (1, 2));

-- purchase و purchase_re
ALTER TABLE purchase
    MODIFY quantity DECIMAL(14, 3) NOT NULL;
ALTER TABLE purchase
    MODIFY price DECIMAL(14, 2) NOT NULL;
ALTER TABLE purchase
    MODIFY discount DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE purchase
    MODIFY type_value DECIMAL(14, 3) NOT NULL DEFAULT 1;
ALTER TABLE purchase
    MODIFY invoice_number BIGINT NOT NULL;
ALTER TABLE purchase
    ADD CONSTRAINT purchase_quantity_chk CHECK (quantity > 0);

ALTER TABLE purchase_re
    MODIFY quantity DECIMAL(14, 3) NOT NULL;
ALTER TABLE purchase_re
    MODIFY price DECIMAL(14, 2) NOT NULL;
ALTER TABLE purchase_re
    MODIFY discount DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE purchase_re
    MODIFY type_value DECIMAL(14, 3) NOT NULL DEFAULT 1;
ALTER TABLE purchase_re
    ADD CONSTRAINT purchase_re_quantity_chk CHECK (quantity > 0);

-- sales و sales_re
ALTER TABLE sales
    MODIFY quantity DECIMAL(14, 3) NOT NULL;
ALTER TABLE sales
    MODIFY price DECIMAL(14, 2) NOT NULL;
ALTER TABLE sales
    MODIFY buy_price DECIMAL(14, 2) NOT NULL;
ALTER TABLE sales
    MODIFY total_sel_price DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE sales
    MODIFY total_buy_price DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE sales
    MODIFY total_profit DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE sales
    MODIFY discount DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE sales
    MODIFY type_value DECIMAL(14, 3) NOT NULL DEFAULT 1;
ALTER TABLE sales
    MODIFY invoice_number BIGINT NOT NULL;
ALTER TABLE sales
    ADD CONSTRAINT sales_quantity_chk CHECK (quantity > 0);

ALTER TABLE sales_re
    MODIFY quantity DECIMAL(14, 3) NOT NULL;
ALTER TABLE sales_re
    MODIFY price DECIMAL(14, 2) NOT NULL;
ALTER TABLE sales_re
    MODIFY buy_price DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE sales_re
    MODIFY total_sel_price DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE sales_re
    MODIFY total_buy_price DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE sales_re
    MODIFY total_profit DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE sales_re
    MODIFY discount DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE sales_re
    MODIFY type_value DECIMAL(14, 3) NOT NULL DEFAULT 1;
ALTER TABLE sales_re
    ADD CONSTRAINT sales_re_quantity_chk CHECK (quantity > 0);

-- targeted_sales
ALTER TABLE targeted_sales
    MODIFY target DECIMAL(14, 2) NOT NULL;
ALTER TABLE targeted_sales
    MODIFY target_ratio1 DECIMAL(6, 2) NOT NULL DEFAULT 100;
ALTER TABLE targeted_sales
    MODIFY rate_1 DECIMAL(6, 2) NOT NULL DEFAULT 0;
ALTER TABLE targeted_sales
    MODIFY target_ratio2 DECIMAL(6, 2) NOT NULL DEFAULT 0;
ALTER TABLE targeted_sales
    MODIFY rate_2 DECIMAL(6, 2) NOT NULL DEFAULT 0;
ALTER TABLE targeted_sales
    MODIFY target_ratio3 DECIMAL(6, 2) NOT NULL DEFAULT 0;
ALTER TABLE targeted_sales
    MODIFY rate_3 DECIMAL(6, 2) NOT NULL DEFAULT 0;

-- suppliers_accounts و customers_accounts
ALTER TABLE suppliers_accounts
    MODIFY account_num BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE suppliers_accounts
    MODIFY purchase DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE suppliers_accounts
    MODIFY paid DECIMAL(14, 2) NOT NULL;
ALTER TABLE suppliers_accounts
    MODIFY numberInv BIGINT NOT NULL;
ALTER TABLE suppliers_accounts
    MODIFY invoice_number_return BIGINT NOT NULL DEFAULT 0;

ALTER TABLE customers_accounts
    MODIFY account_num BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE customers_accounts
    MODIFY paid DECIMAL(14, 2) NOT NULL;
ALTER TABLE customers_accounts
    MODIFY purchase DECIMAL(14, 2) NOT NULL DEFAULT 0;
ALTER TABLE customers_accounts
    MODIFY numberInv BIGINT NOT NULL;
ALTER TABLE customers_accounts
    MODIFY invoice_number_return BIGINT NOT NULL DEFAULT 0;

-- ============================================================
-- 3. إضافة أعمدة جديدة في الجداول الموجودة
-- ============================================================

-- user_shifts: إضافة الأعمدة المفقودة (مرحلة V018)
ALTER TABLE user_shifts
    ADD COLUMN total_sales         DECIMAL(14, 2) DEFAULT 0 AFTER close_balance,
    ADD COLUMN total_sales_returns DECIMAL(14, 2) DEFAULT 0 AFTER total_sales,
    ADD COLUMN total_expenses      DECIMAL(14, 2) DEFAULT 0 AFTER total_sales_returns,
    ADD COLUMN total_deposits      DECIMAL(14, 2) DEFAULT 0 AFTER total_expenses,
    ADD COLUMN total_withdrawals   DECIMAL(14, 2) DEFAULT 0 AFTER total_deposits,
    ADD COLUMN expected_balance    DECIMAL(14, 2) DEFAULT 0 AFTER total_withdrawals,
    ADD COLUMN difference          DECIMAL(14, 2) DEFAULT 0 AFTER expected_balance,
    ADD COLUMN invoices_count      INT            DEFAULT 0 AFTER difference;

-- items_stock: إضافة current_quantity
ALTER TABLE items_stock
    ADD COLUMN current_quantity DECIMAL(14, 3) NOT NULL DEFAULT 0 AFTER first_balance;

-- تحديث current_quantity لتعادل first_balance (للبيانات القديمة)
UPDATE items_stock
SET current_quantity = first_balance
WHERE current_quantity = 0
  AND first_balance != 0;

-- items_package: إضافة المفتاحين الأجانب الصحيحين (إذا لم يكونا موجودين)
ALTER TABLE items_package
    DROP FOREIGN KEY items_package_ibfk_1;
ALTER TABLE items_package
    DROP FOREIGN KEY items_package_ibfk_2;
ALTER TABLE items_package
    ADD CONSTRAINT items_package_item_id_fk FOREIGN KEY (item_id) REFERENCES items (id) ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE items_package
    ADD CONSTRAINT items_package_package_id_fk FOREIGN KEY (package_id) REFERENCES items (id) ON UPDATE CASCADE ON DELETE CASCADE;

-- user_permission: إضافة المفتاح الفريد
ALTER TABLE user_permission
    ADD CONSTRAINT user_permission_uk UNIQUE (permission_id, user_id);
ALTER TABLE user_permission
    ADD CONSTRAINT user_permission_chk CHECK (check_status IN (0, 1));

-- ============================================================
-- 4. إضافة الجداول الجديدة
-- ============================================================

-- جدول stock_movements
CREATE TABLE stock_movements
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
    CONSTRAINT stock_movements_quantity_chk CHECK (
        (quantity_in > 0 AND quantity_out = 0) OR
        (quantity_in = 0 AND quantity_out > 0)
        ),
    CONSTRAINT stock_movements_unit_value_chk CHECK (unit_value > 0),
    CONSTRAINT stock_movements_type_chk CHECK (movement_type IN (
                                                                 'OPENING', 'PURCHASE', 'PURCHASE_RETURN', 'SALE',
                                                                 'SALE_RETURN',
                                                                 'TRANSFER_IN', 'TRANSFER_OUT', 'INVENTORY_ADJUST_IN',
                                                                 'INVENTORY_ADJUST_OUT'
        )),
    CONSTRAINT stock_movements_reference_type_chk CHECK (
        reference_type IS NULL OR reference_type IN (
                                                     'ITEM', 'PURCHASE', 'PURCHASE_RETURN', 'SALE', 'SALE_RETURN',
                                                     'STOCK_TRANSFER', 'INVENTORY'
            )
        )
);

CREATE INDEX idx_stock_movements_item_stock_date ON stock_movements (item_id, stock_id, movement_date);
CREATE INDEX idx_stock_movements_reference ON stock_movements (reference_type, reference_id);
CREATE INDEX idx_stock_movements_stock_date ON stock_movements (stock_id, movement_date);

-- جدول audit_log
CREATE TABLE audit_log
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
CREATE INDEX idx_audit_table_record ON audit_log (table_name, record_id);
CREATE INDEX idx_audit_user_time ON audit_log (user_id, action_time);
CREATE INDEX idx_audit_action_time ON audit_log (action_type, action_time);

-- ============================================================
-- 5. إضافة الفهارس المحسنة المفقودة
-- ============================================================

CREATE INDEX processes_data_table_idx ON processes_data (table_name, table_id);
CREATE INDEX processes_data_date_idx ON processes_data (date_insert);
CREATE INDEX stock_transfer_date_idx ON stock_transfer (transfer_date);
CREATE INDEX treasury_deposit_expenses_date_idx ON treasury_deposit_expenses (date_inter);
CREATE INDEX treasury_deposit_expenses_treasury_idx ON treasury_deposit_expenses (treasury_id, date_inter);
CREATE INDEX treasury_transfers_date_idx ON treasury_transfers (transfer_date);
CREATE INDEX expenses_details_date_idx ON expenses_details (date);
CREATE INDEX expenses_details_treasury_idx ON expenses_details (treasury_id, date);
CREATE INDEX total_buy_date_idx ON total_buy (invoice_date);
CREATE INDEX total_buy_treasury_idx ON total_buy (treasury_id, invoice_date);
CREATE INDEX total_buy_re_date_idx ON total_buy_re (invoice_date);
CREATE INDEX total_buy_re_treasury_idx ON total_buy_re (treasury_id, invoice_date);
CREATE INDEX total_buy_re_sup_idx ON total_buy_re (sup_id);
CREATE INDEX total_sales_date_idx ON total_sales (invoice_date);
CREATE INDEX total_sales_treasury_idx ON total_sales (treasury_id, invoice_date);
CREATE INDEX total_sales_re_date_idx ON total_sales_re (invoice_date);
CREATE INDEX total_sales_re_treasury_idx ON total_sales_re (treasury_id, invoice_date);
CREATE INDEX total_sales_re_sup_idx ON total_sales_re (sup_id);
CREATE INDEX total_sales_re_delegate_idx ON total_sales_re (delegate_id);
CREATE INDEX suppliers_accounts_date_idx ON suppliers_accounts (account_date);
CREATE INDEX customers_accounts_date_idx ON customers_accounts (account_date);
CREATE INDEX purchase_item_idx ON purchase (num);
CREATE INDEX purchase_re_item_idx ON purchase_re (item_id);
CREATE INDEX sales_item_idx ON sales (num);
CREATE INDEX sales_re_item_idx ON sales_re (item_id);
CREATE INDEX items_package_item_idx ON items_package (item_id);
CREATE INDEX items_package_package_idx ON items_package (package_id);
CREATE INDEX stock_transfer_list_item_idx ON stock_transfer_list (item_id);

-- ============================================================
-- 6. تحديث العلاقات الأجنبية لتتوافق مع BIGINT حيث لزم
-- (حذف وإعادة إنشاء بعض المفاتيح الأجنبية التي تشير إلى أعمدة BIGINT)
-- ============================================================

-- purchase -> total_buy
ALTER TABLE purchase
    DROP FOREIGN KEY purchase_total_buy_invoice_number_fk;
ALTER TABLE purchase
    ADD CONSTRAINT purchase_total_buy_invoice_number_fk
        FOREIGN KEY (invoice_number) REFERENCES total_buy (invoice_number) ON UPDATE CASCADE ON DELETE CASCADE;

-- purchase_re -> total_buy_re
ALTER TABLE purchase_re
    DROP FOREIGN KEY purchase_re_total_buy_re_id_fk;
ALTER TABLE purchase_re
    ADD CONSTRAINT purchase_re_total_buy_re_id_fk
        FOREIGN KEY (invoice_number) REFERENCES total_buy_re (id) ON UPDATE CASCADE ON DELETE CASCADE;

-- sales -> total_sales
ALTER TABLE sales
    DROP FOREIGN KEY sales_total_invoice_number_fk;
ALTER TABLE sales
    ADD CONSTRAINT sales_total_invoice_number_fk
        FOREIGN KEY (invoice_number) REFERENCES total_sales (invoice_number) ON UPDATE CASCADE ON DELETE CASCADE;

-- sales_re -> total_sales_re
ALTER TABLE sales_re
    DROP FOREIGN KEY sales_re_total_sales_re_id_fk;
ALTER TABLE sales_re
    ADD CONSTRAINT sales_re_total_sales_re_id_fk
        FOREIGN KEY (invoice_number) REFERENCES total_sales_re (id) ON UPDATE CASCADE ON DELETE CASCADE;

-- suppliers_accounts -> total_buy (numberInv)
ALTER TABLE suppliers_accounts
    DROP FOREIGN KEY suppliers_accounts_total_buy_invoice_number_fk;
ALTER TABLE suppliers_accounts
    ADD CONSTRAINT suppliers_accounts_total_buy_invoice_number_fk
        FOREIGN KEY (numberInv) REFERENCES total_buy (invoice_number);

-- customers_accounts -> total_sales (numberInv)
ALTER TABLE customers_accounts
    DROP FOREIGN KEY customers_accounts_total_sales_invoice_number_fk;
ALTER TABLE customers_accounts
    ADD CONSTRAINT customers_accounts_total_sales_invoice_number_fk
        FOREIGN KEY (numberInv) REFERENCES total_sales (invoice_number);

-- stock_transfer_list -> items
ALTER TABLE stock_transfer_list
    DROP FOREIGN KEY stock_transfer_list_ibfk_2;
ALTER TABLE stock_transfer_list
    ADD CONSTRAINT stock_transfer_list_items_id_fk
        FOREIGN KEY (item_id) REFERENCES items (id);

-- إعادة تمكين فحص المفاتيح الأجنبية
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 7. تحديث القيم الافتراضية وملء أي بيانات افتراضية ضرورية
-- ============================================================

-- التأكد من وجود مستخدم افتراضي (id=1) إذا لم يكن موجوداً
INSERT IGNORE INTO users (id, user_name, user_pass, user_activity, user_available)
VALUES (1, 'system', NULL, 1, 0);

-- ============================================================
-- انتهى سكريبت التحديث
-- ============================================================