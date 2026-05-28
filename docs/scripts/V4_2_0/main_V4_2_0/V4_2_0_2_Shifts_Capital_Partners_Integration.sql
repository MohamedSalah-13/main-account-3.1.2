-- =====================================================================
-- V4_2_0_2: تحسينات الورديات + رأس المال + نسب الشركاء
-- =====================================================================

USE account_system_db;

-- =====================================================================
-- 1) تعديل جدول الورديات لربطه بالخزينة
-- =====================================================================

ALTER TABLE user_shifts 
ADD COLUMN treasury_id INT DEFAULT 1 NOT NULL AFTER user_id,
ADD COLUMN shift_status VARCHAR(20) DEFAULT 'OPEN' NOT NULL AFTER is_open,
ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL AFTER notes,
ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP AFTER created_at;

-- إضافة Foreign Key للخزينة
ALTER TABLE user_shifts
ADD CONSTRAINT user_shifts_treasury_fk 
    FOREIGN KEY (treasury_id) REFERENCES treasury(id);

-- إضافة constraint للحالة
ALTER TABLE user_shifts
ADD CONSTRAINT user_shifts_status_chk 
    CHECK (shift_status IN ('OPEN', 'CLOSED', 'FORCE_CLOSED'));

-- إضافة Index للأداء
CREATE INDEX idx_user_shifts_treasury ON user_shifts(treasury_id, open_time);
CREATE INDEX idx_user_shifts_status ON user_shifts(shift_status, is_open);

-- =====================================================================
-- 2) إضافة shift_id للفواتير والمعاملات
-- =====================================================================

-- فواتير المبيعات
ALTER TABLE total_sales
ADD COLUMN shift_id INT NULL AFTER user_id;

ALTER TABLE total_sales
ADD CONSTRAINT total_sales_shift_fk 
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id)
    ON DELETE SET NULL;

CREATE INDEX idx_total_sales_shift ON total_sales(shift_id);

-- مرتجعات المبيعات
ALTER TABLE total_sales_re
ADD COLUMN shift_id INT NULL AFTER user_id;

ALTER TABLE total_sales_re
ADD CONSTRAINT total_sales_re_shift_fk 
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id)
    ON DELETE SET NULL;

CREATE INDEX idx_total_sales_re_shift ON total_sales_re(shift_id);

-- المصروفات
ALTER TABLE expenses_details
ADD COLUMN shift_id INT NULL AFTER user_id;

ALTER TABLE expenses_details
ADD CONSTRAINT expenses_details_shift_fk 
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id)
    ON DELETE SET NULL;

CREATE INDEX idx_expenses_details_shift ON expenses_details(shift_id);

-- الإيداعات والمسحوبات
ALTER TABLE treasury_deposit_expenses
ADD COLUMN shift_id INT NULL AFTER user_id;

ALTER TABLE treasury_deposit_expenses
ADD CONSTRAINT treasury_deposit_expenses_shift_fk 
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id)
    ON DELETE SET NULL;

CREATE INDEX idx_treasury_deposit_expenses_shift ON treasury_deposit_expenses(shift_id);

-- حسابات العملاء (الدفعات)
ALTER TABLE customers_accounts
ADD COLUMN shift_id INT NULL AFTER user_id;

ALTER TABLE customers_accounts
ADD CONSTRAINT customers_accounts_shift_fk 
    FOREIGN KEY (shift_id) REFERENCES user_shifts(id)
    ON DELETE SET NULL;

CREATE INDEX idx_customers_accounts_shift ON customers_accounts(shift_id);


