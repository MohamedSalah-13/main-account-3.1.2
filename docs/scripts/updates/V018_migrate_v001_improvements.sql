-- =====================================================================
-- V018 — Migration from original V001 schema to the improved schema
-- Target DB : account_system_db
-- Author    : team
-- Idempotent: Uses IF EXISTS / IF NOT EXISTS where possible.
--             Re-running should be safe (lookups guarded), but run ONCE on prod.
-- =====================================================================
-- BEFORE RUNNING:
--   1) mysqldump full backup.
--   2) STOP the application.
--   3) Run on a test copy first.
-- =====================================================================

USE account_system_db;

SET @OLD_SQL_MODE = @@SQL_MODE;
SET SQL_MODE = 'STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION';
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================================
-- 0) Drop all dependent VIEWS (will be recreated from V017 after migration)
-- =====================================================================
DROP VIEW IF EXISTS purchase_names_table;
DROP VIEW IF EXISTS sales_with_sales_package;
DROP VIEW IF EXISTS sales_names_table;
DROP VIEW IF EXISTS sales_return_names_table;
DROP VIEW IF EXISTS purchase_return_names_table;
DROP VIEW IF EXISTS stock_transfer_view;
DROP VIEW IF EXISTS quantity_items_table;
DROP VIEW IF EXISTS total_sales_names_table;
DROP VIEW IF EXISTS total_purchase_names_table;
DROP VIEW IF EXISTS total_purchase_return_names_table;
DROP VIEW IF EXISTS total_sales_return_names_table;
DROP VIEW IF EXISTS account_customer_table;
DROP VIEW IF EXISTS account_suppliers_table;
DROP VIEW IF EXISTS card_item_view;
DROP VIEW IF EXISTS card_item_view_details;
DROP VIEW IF EXISTS expenses_details_view;
DROP VIEW IF EXISTS mini_quantity_view;
DROP VIEW IF EXISTS target_delegate;
DROP VIEW IF EXISTS treasury_balance;
DROP VIEW IF EXISTS treasury_transfers_and_names;
DROP VIEW IF EXISTS treasury_balance_after_convert;
DROP VIEW IF EXISTS account_customer_totals;
DROP VIEW IF EXISTS account_suppliers_totals;
DROP VIEW IF EXISTS earnings_reports;

-- =====================================================================
-- 1) users
-- =====================================================================
ALTER TABLE users
    MODIFY COLUMN user_pass      VARCHAR(255) NULL,
    MODIFY COLUMN user_activity  TINYINT DEFAULT 1 NOT NULL,
    MODIFY COLUMN user_available TINYINT DEFAULT 0 NOT NULL;

-- CHECK constraints (MySQL 8.0.16+)
ALTER TABLE users
    ADD CONSTRAINT users_activity_chk  CHECK (user_activity  IN (0, 1)),
    ADD CONSTRAINT users_available_chk CHECK (user_available IN (0, 1));

-- =====================================================================
-- 2) employees
-- =====================================================================
ALTER TABLE employees
    MODIFY COLUMN salary DECIMAL(14, 2) NOT NULL;

-- =====================================================================
-- 3) suppliers / custom / treasury / targeted_sales / units / type_price
-- =====================================================================
ALTER TABLE suppliers
    MODIFY COLUMN first_balance DECIMAL(14, 2) DEFAULT 0 NOT NULL;

ALTER TABLE custom
    MODIFY COLUMN limit_num     DECIMAL(14, 2)           NOT NULL,
    MODIFY COLUMN first_balance DECIMAL(14, 2) DEFAULT 0 NOT NULL;

ALTER TABLE treasury
    MODIFY COLUMN amount DECIMAL(14, 2) DEFAULT 0 NOT NULL;

ALTER TABLE targeted_sales
    MODIFY COLUMN target        DECIMAL(14, 2)             NOT NULL,
    MODIFY COLUMN target_ratio1 DECIMAL(6, 2) DEFAULT 100  NOT NULL,
    MODIFY COLUMN rate_1        DECIMAL(6, 2) DEFAULT 0    NOT NULL,
    MODIFY COLUMN target_ratio2 DECIMAL(6, 2) DEFAULT 0    NOT NULL,
    MODIFY COLUMN rate_2        DECIMAL(6, 2) DEFAULT 0    NOT NULL,
    MODIFY COLUMN target_ratio3 DECIMAL(6, 2) DEFAULT 0    NOT NULL,
    MODIFY COLUMN rate_3        DECIMAL(6, 2) DEFAULT 0    NOT NULL;

ALTER TABLE units
    MODIFY COLUMN value_d DECIMAL(14, 3) DEFAULT 1 NOT NULL;

-- =====================================================================
-- 4) stock_transfer / stock_transfer_list
-- =====================================================================
ALTER TABLE stock_transfer
    ADD CONSTRAINT stock_transfer_from_fk      FOREIGN KEY (stock_from) REFERENCES stocks (stock_id),
    ADD CONSTRAINT stock_transfer_to_fk        FOREIGN KEY (stock_to)   REFERENCES stocks (stock_id),
    ADD CONSTRAINT stock_transfer_not_same_chk CHECK (stock_from <> stock_to);

CREATE INDEX stock_transfer_date_idx ON stock_transfer (transfer_date);

ALTER TABLE stock_transfer_list
    MODIFY COLUMN quantity DECIMAL(14, 3) NOT NULL;
CREATE INDEX stock_transfer_list_item_idx ON stock_transfer_list (item_id);

-- =====================================================================
-- 5) processes_data indexes
-- =====================================================================
CREATE INDEX processes_data_table_idx ON processes_data (table_name, table_id);
CREATE INDEX processes_data_date_idx  ON processes_data (date_insert);

-- =====================================================================
-- 6) items & items_stock & items_package & items_units
-- =====================================================================
ALTER TABLE items
    MODIFY COLUMN buy_price     DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN sel_price1    DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN sel_price2    DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN sel_price3    DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN mini_quantity DECIMAL(14, 3) DEFAULT 1 NOT NULL,
    MODIFY COLUMN first_balance DECIMAL(14, 3) DEFAULT 0 NOT NULL;

ALTER TABLE items_package
    MODIFY COLUMN quantity DECIMAL(14, 3) NOT NULL;
CREATE INDEX items_package_item_idx ON items_package (item_id);

ALTER TABLE items_stock
    MODIFY COLUMN first_balance DECIMAL(14, 3) DEFAULT 0 NOT NULL,
    ADD CONSTRAINT items_stock_uk UNIQUE (item_id, stock_id);

ALTER TABLE items_units
    MODIFY COLUMN quantity  DECIMAL(14, 3) NOT NULL,
    MODIFY COLUMN buy_price DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN sel_price DECIMAL(14, 2) NOT NULL;

-- =====================================================================
-- 7) Upgrade invoice_number PKs INT -> BIGINT
--    Order: FKs must reference a column of the same type, so we:
--      a) drop FKs referencing them,
--      b) modify parent PKs,
--      c) modify child FK columns,
--      d) recreate FKs.
-- =====================================================================

-- 7.1 total_buy / purchase / suppliers_accounts
ALTER TABLE purchase           DROP FOREIGN KEY purchase_total_buy_invoice_number_fk;
-- suppliers_accounts.numberInv is an index (not FK) → safe to alter directly.

ALTER TABLE total_buy
    MODIFY COLUMN invoice_number BIGINT         NOT NULL,
    MODIFY COLUMN total          DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN discount       DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN paid_up        DECIMAL(14, 2) NOT NULL COMMENT 'paid from the treasury مدفوع نقدا من الخزينة',
    MODIFY COLUMN invoice_type   TINYINT DEFAULT 1 NOT NULL,
    ADD CONSTRAINT total_buy_invoice_type_chk CHECK (invoice_type IN (1, 2));

ALTER TABLE purchase
    MODIFY COLUMN invoice_number BIGINT         NOT NULL,
    MODIFY COLUMN quantity       DECIMAL(14, 3) NOT NULL,
    MODIFY COLUMN price          DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN discount       DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN type_value     DECIMAL(14, 3) DEFAULT 1 NOT NULL,
    ADD CONSTRAINT purchase_total_buy_invoice_number_fk
        FOREIGN KEY (invoice_number) REFERENCES total_buy (invoice_number)
            ON UPDATE CASCADE ON DELETE CASCADE;

CREATE INDEX purchase_item_idx ON purchase (num);

ALTER TABLE suppliers_accounts
    MODIFY COLUMN account_num           BIGINT AUTO_INCREMENT,
    MODIFY COLUMN numberInv             BIGINT NOT NULL,
    MODIFY COLUMN invoice_number_return BIGINT DEFAULT 0 NOT NULL
        COMMENT 'This column for number invoice for returns',
    MODIFY COLUMN purchase DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN paid     DECIMAL(14, 2)          NOT NULL;

-- Rename old index name if existed then add date index
CREATE INDEX suppliers_accounts_numberInv_idx ON suppliers_accounts (numberInv);
CREATE INDEX suppliers_accounts_date_idx      ON suppliers_accounts (account_date);

-- 7.2 total_sales / sales / customers_accounts
ALTER TABLE sales DROP FOREIGN KEY sales_total_invoice_number_fk;

ALTER TABLE total_sales
    MODIFY COLUMN invoice_number BIGINT         NOT NULL,
    MODIFY COLUMN total          DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN discount       DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN paid_up        DECIMAL(14, 2) NOT NULL COMMENT 'paid to the treasury مدفوع نقدا الى الخزينة',
    MODIFY COLUMN invoice_type   TINYINT DEFAULT 1 NOT NULL,
    ADD CONSTRAINT total_sales_invoice_type_chk CHECK (invoice_type IN (1, 2));

ALTER TABLE sales
    MODIFY COLUMN invoice_number  BIGINT         NOT NULL,
    MODIFY COLUMN quantity        DECIMAL(14, 3) NOT NULL,
    MODIFY COLUMN price           DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN buy_price       DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN total_sel_price DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN total_buy_price DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN total_profit    DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN discount        DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN type_value      DECIMAL(14, 3) DEFAULT 1 NOT NULL,
    ADD CONSTRAINT sales_total_invoice_number_fk
        FOREIGN KEY (invoice_number) REFERENCES total_sales (invoice_number)
            ON UPDATE CASCADE ON DELETE CASCADE;

CREATE INDEX sales_item_idx ON sales (num);

ALTER TABLE customers_accounts
    MODIFY COLUMN account_num           BIGINT AUTO_INCREMENT,
    MODIFY COLUMN account_code          INT            NOT NULL,
    MODIFY COLUMN account_date          DATE           NOT NULL,
    MODIFY COLUMN paid                  DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN purchase              DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN numberInv             BIGINT         NOT NULL,
    MODIFY COLUMN invoice_number_return BIGINT DEFAULT 0 NOT NULL
        COMMENT 'This column for number invoice for returns';

CREATE INDEX customers_accounts_numberInv_idx ON customers_accounts (numberInv);
CREATE INDEX customers_accounts_date_idx      ON customers_accounts (account_date);

-- 7.3 total_buy_re / purchase_re (id already BIGINT, just adjust money cols)
ALTER TABLE total_buy_re
    MODIFY COLUMN total            DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN discount         DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN paid_to_treasury DECIMAL(14, 2) NOT NULL COMMENT 'Paid to the treasury مدفوعات الى الخزينة',
    MODIFY COLUMN invoice_type     TINYINT DEFAULT 1 NOT NULL,
    ADD CONSTRAINT total_buy_re_invoice_type_chk CHECK (invoice_type IN (1, 2));

ALTER TABLE purchase_re
    MODIFY COLUMN quantity   DECIMAL(14, 3) NOT NULL,
    MODIFY COLUMN price      DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN discount   DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN type_value DECIMAL(14, 3) DEFAULT 1 NOT NULL;
CREATE INDEX purchase_re_item_idx ON purchase_re (item_id);

-- 7.4 total_sales_re / sales_re / sales_package
ALTER TABLE total_sales_re
    MODIFY COLUMN total              DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN discount           DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN paid_from_treasury DECIMAL(14, 2) NOT NULL COMMENT 'paid from the treasury مدفوع نقدا من الخزينة',
    MODIFY COLUMN invoice_type       TINYINT DEFAULT 1 NOT NULL,
    ADD CONSTRAINT total_sales_re_invoice_type_chk CHECK (invoice_type IN (1, 2));

ALTER TABLE sales_re
    MODIFY COLUMN quantity        DECIMAL(14, 3) NOT NULL,
    MODIFY COLUMN price           DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN buy_price       DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN total_sel_price DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN total_buy_price DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN total_profit    DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN discount        DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN type_value      DECIMAL(14, 3) DEFAULT 1 NOT NULL;
CREATE INDEX sales_re_item_idx ON sales_re (item_id);

ALTER TABLE sales_package
    MODIFY COLUMN quantity        DECIMAL(14, 3) NOT NULL,
    MODIFY COLUMN price           DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN buy_price       DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN total_sel_price DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN total_buy_price DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN total_profit    DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN discount        DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN unit_value      DECIMAL(14, 3) DEFAULT 1 NOT NULL;
CREATE INDEX sales_package_item_idx  ON sales_package (item_id);
CREATE INDEX sales_package_sales_idx ON sales_package (sales_id);
CREATE INDEX sales_package_unit_idx  ON sales_package (unit_id);

-- =====================================================================
-- 8) Date indexes for fast period / fiscal-year filtering
-- =====================================================================
CREATE INDEX total_buy_date_idx         ON total_buy (invoice_date);
CREATE INDEX total_buy_treasury_idx     ON total_buy (treasury_id, invoice_date);
CREATE INDEX total_buy_re_date_idx      ON total_buy_re (invoice_date);
CREATE INDEX total_buy_re_treasury_idx  ON total_buy_re (treasury_id, invoice_date);
CREATE INDEX total_buy_re_sup_idx       ON total_buy_re (sup_id);
CREATE INDEX total_sales_date_idx       ON total_sales (invoice_date);
CREATE INDEX total_sales_treasury_idx   ON total_sales (treasury_id, invoice_date);
CREATE INDEX total_sales_re_date_idx    ON total_sales_re (invoice_date);
CREATE INDEX total_sales_re_treasury_idx ON total_sales_re (treasury_id, invoice_date);
CREATE INDEX total_sales_re_sup_idx     ON total_sales_re (sup_id);
CREATE INDEX total_sales_re_delegate_idx ON total_sales_re (delegate_id);

-- =====================================================================
-- 9) expenses_details — change amount INT -> DECIMAL(14,2)
-- =====================================================================
ALTER TABLE expenses_details
    MODIFY COLUMN amount DECIMAL(14, 2) DEFAULT 0 NOT NULL;
CREATE INDEX expenses_details_date_idx     ON expenses_details (date);
CREATE INDEX expenses_details_treasury_idx ON expenses_details (treasury_id, date);

-- =====================================================================
-- 10) treasury_deposit_expenses
-- =====================================================================
ALTER TABLE treasury_deposit_expenses
    MODIFY COLUMN amount              DECIMAL(14, 2) NOT NULL,
    MODIFY COLUMN deposit_or_expenses TINYINT DEFAULT 1 NOT NULL,
    ADD CONSTRAINT treasury_deposit_expenses_type_chk CHECK (deposit_or_expenses IN (1, 2));
CREATE INDEX treasury_deposit_expenses_date_idx     ON treasury_deposit_expenses (date_inter);
CREATE INDEX treasury_deposit_expenses_treasury_idx ON treasury_deposit_expenses (treasury_id, date_inter);

-- =====================================================================
-- 11) treasury_transfers
-- =====================================================================
ALTER TABLE treasury_transfers
    MODIFY COLUMN amount DECIMAL(14, 2) NOT NULL,
    ADD CONSTRAINT treasury_transfers_not_same_chk CHECK (treasury_from <> treasury_to);
CREATE INDEX treasury_transfers_date_idx ON treasury_transfers (transfer_date);

-- =====================================================================
-- 12) user_permission
-- =====================================================================
ALTER TABLE user_permission
    MODIFY COLUMN check_status TINYINT DEFAULT 0 NOT NULL,
    ADD CONSTRAINT user_permission_uk  UNIQUE (permission_id, user_id),
    ADD CONSTRAINT user_permission_chk CHECK (check_status IN (0, 1));

-- =====================================================================
-- 13) user_shifts — align types with improved schema
-- =====================================================================
ALTER TABLE user_shifts
    MODIFY COLUMN open_balance        DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN close_balance       DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN total_sales         DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN total_sales_returns DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN total_expenses      DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN total_deposits      DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN total_withdrawals   DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN expected_balance    DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN difference          DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    MODIFY COLUMN invoices_count      INT            DEFAULT 0 NOT NULL;

-- =====================================================================
-- 14) Restore settings & recreate views
-- =====================================================================
SET FOREIGN_KEY_CHECKS = 1;
SET SQL_MODE = @OLD_SQL_MODE;

-- Views are recreated by re-running docs/scripts/main/V4_1_0_7_view_table.sql
-- (kept separate to keep this migration file focused on schema changes).

-- =====================================================================
-- 15) Post-migration verification (optional manual queries)
-- =====================================================================
-- SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
-- FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA = 'account_system_db'
--   AND COLUMN_NAME IN ('invoice_number','numberInv','account_num','amount','total','paid','discount')
-- ORDER BY TABLE_NAME, COLUMN_NAME;
