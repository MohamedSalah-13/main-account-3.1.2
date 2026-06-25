USE account_system_db;

-- =====================================================================
-- 013_delegate_targets.sql
-- Module: Delegates, Targets, Reports, Commissions
-- =====================================================================

-- =====================================================================
-- 1) Delegate Profile
-- بيانات إضافية للمندوب بدون فصل المندوب عن جدول employees
-- =====================================================================

CREATE TABLE IF NOT EXISTS delegate_profile
(
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    employee_id            INT NOT NULL,
    area_id                INT NULL,
    supervisor_id          INT NULL,

    commission_type        VARCHAR(20) DEFAULT 'NONE' NOT NULL,
    commission_value       DECIMAL(14, 2) DEFAULT 0 NOT NULL,

    collection_target      DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    credit_limit           DECIMAL(14, 2) DEFAULT 0 NOT NULL,

    is_active              TINYINT DEFAULT 1 NOT NULL,
    notes                  TEXT NULL,

    created_at             DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id                INT DEFAULT 1 NOT NULL,

    CONSTRAINT delegate_profile_employee_fk
        FOREIGN KEY (employee_id) REFERENCES employees(id)
            ON UPDATE CASCADE ON DELETE CASCADE,

    CONSTRAINT delegate_profile_area_fk
        FOREIGN KEY (area_id) REFERENCES table_area(id),

    CONSTRAINT delegate_profile_supervisor_fk
        FOREIGN KEY (supervisor_id) REFERENCES employees(id),

    CONSTRAINT delegate_profile_user_fk
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT delegate_profile_employee_uk UNIQUE (employee_id),

    CONSTRAINT delegate_profile_active_chk
        CHECK (is_active IN (0, 1)),

    CONSTRAINT delegate_profile_commission_type_chk
        CHECK (commission_type IN (
                                   'NONE',
                                   'SALES_PERCENT',
                                   'PROFIT_PERCENT',
                                   'FIXED_PER_INVOICE'
            ))
);

CREATE INDEX idx_delegate_profile_area
    ON delegate_profile(area_id);

CREATE INDEX idx_delegate_profile_active
    ON delegate_profile(is_active);

CREATE INDEX idx_delegate_profile_supervisor
    ON delegate_profile(supervisor_id);


-- =====================================================================
-- 2) Delegate Targets
-- أهداف / تارجت المندوبين
-- =====================================================================

CREATE TABLE IF NOT EXISTS delegate_targets
(
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    delegate_id          INT NOT NULL,

    target_name          VARCHAR(150) NOT NULL,
    target_type          VARCHAR(30) NOT NULL,

    period_type          VARCHAR(20) DEFAULT 'MONTHLY' NOT NULL,
    period_from          DATE NOT NULL,
    period_to            DATE NOT NULL,

    target_amount        DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    target_quantity      DECIMAL(14, 3) DEFAULT 0 NOT NULL,
    target_count         INT DEFAULT 0 NOT NULL,

    min_profit_percent   DECIMAL(6, 2) DEFAULT 0 NOT NULL,

    status               VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    notes                TEXT NULL,

    created_at           DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id              INT DEFAULT 1 NOT NULL,

    CONSTRAINT delegate_targets_delegate_fk
        FOREIGN KEY (delegate_id) REFERENCES employees(id)
            ON UPDATE CASCADE ON DELETE RESTRICT,

    CONSTRAINT delegate_targets_user_fk
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT delegate_targets_type_chk
        CHECK (target_type IN (
                               'SALES_AMOUNT',
                               'NET_SALES_AMOUNT',
                               'COLLECTION_AMOUNT',
                               'PROFIT_AMOUNT',
                               'PROFIT_PERCENT',
                               'INVOICES_COUNT',
                               'CUSTOMERS_COUNT',
                               'ITEM_QUANTITY'
            )),

    CONSTRAINT delegate_targets_period_chk
        CHECK (period_type IN (
                               'DAILY',
                               'WEEKLY',
                               'MONTHLY',
                               'QUARTERLY',
                               'YEARLY',
                               'CUSTOM'
            )),

    CONSTRAINT delegate_targets_status_chk
        CHECK (status IN (
                          'ACTIVE',
                          'PAUSED',
                          'CANCELLED',
                          'CLOSED'
            )),

    CONSTRAINT delegate_targets_dates_chk
        CHECK (period_to >= period_from)
);

CREATE INDEX idx_delegate_targets_delegate_period
    ON delegate_targets(delegate_id, period_from, period_to);

CREATE INDEX idx_delegate_targets_type_status
    ON delegate_targets(target_type, status);

CREATE INDEX idx_delegate_targets_period
    ON delegate_targets(period_from, period_to);


-- =====================================================================
-- 3) Delegate Target Items
-- أهداف المندوب حسب الأصناف
-- =====================================================================

CREATE TABLE IF NOT EXISTS delegate_target_items
(
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    target_id            INT NOT NULL,
    item_id              INT NOT NULL,

    target_quantity      DECIMAL(14, 3) DEFAULT 0 NOT NULL,
    target_amount        DECIMAL(14, 2) DEFAULT 0 NOT NULL,

    CONSTRAINT delegate_target_items_target_fk
        FOREIGN KEY (target_id) REFERENCES delegate_targets(id)
            ON UPDATE CASCADE ON DELETE CASCADE,

    CONSTRAINT delegate_target_items_item_fk
        FOREIGN KEY (item_id) REFERENCES items(id),

    CONSTRAINT delegate_target_items_uk
        UNIQUE (target_id, item_id)
);

CREATE INDEX idx_delegate_target_items_item
    ON delegate_target_items(item_id);


-- =====================================================================
-- 4) Delegate Commissions
-- عمولات المندوبين
-- =====================================================================

CREATE TABLE IF NOT EXISTS delegate_commissions
(
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    delegate_id          INT NOT NULL,

    commission_date      DATE NOT NULL,

    reference_type       VARCHAR(30) NOT NULL,
    reference_id         BIGINT NULL,

    sales_amount         DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    profit_amount        DECIMAL(14, 2) DEFAULT 0 NOT NULL,

    commission_type      VARCHAR(20) NOT NULL,
    commission_rate      DECIMAL(8, 3) DEFAULT 0 NOT NULL,
    commission_amount    DECIMAL(14, 2) DEFAULT 0 NOT NULL,

    payment_status       VARCHAR(20) DEFAULT 'UNPAID' NOT NULL,
    paid_amount          DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    payment_date         DATE NULL,
    treasury_id          INT NULL,

    notes                TEXT NULL,

    created_at           DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id              INT DEFAULT 1 NOT NULL,

    CONSTRAINT delegate_commissions_delegate_fk
        FOREIGN KEY (delegate_id) REFERENCES employees(id)
            ON UPDATE CASCADE ON DELETE RESTRICT,

    CONSTRAINT delegate_commissions_treasury_fk
        FOREIGN KEY (treasury_id) REFERENCES treasury(id),

    CONSTRAINT delegate_commissions_user_fk
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT delegate_commissions_reference_type_chk
        CHECK (reference_type IN (
                                  'SALE',
                                  'PERIOD',
                                  'TARGET'
            )),

    CONSTRAINT delegate_commissions_commission_type_chk
        CHECK (commission_type IN (
                                   'SALES_PERCENT',
                                   'PROFIT_PERCENT',
                                   'FIXED_PER_INVOICE'
            )),

    CONSTRAINT delegate_commissions_payment_status_chk
        CHECK (payment_status IN (
                                  'UNPAID',
                                  'PARTIAL',
                                  'PAID',
                                  'CANCELLED'
            ))
);

CREATE INDEX idx_delegate_commissions_delegate_date
    ON delegate_commissions(delegate_id, commission_date);

CREATE INDEX idx_delegate_commissions_reference
    ON delegate_commissions(reference_type, reference_id);

CREATE INDEX idx_delegate_commissions_payment_status
    ON delegate_commissions(payment_status);


-- =====================================================================
-- 5) View: Delegates List
-- قائمة المندوبين
-- =====================================================================

CREATE OR REPLACE VIEW v_delegates_list AS
SELECT
    e.id AS delegate_id,
    e.column_name AS delegate_name,
    e.tel,
    e.email,
    e.address,
    e.salary,
    e.hire_date,

    dp.id AS profile_id,
    dp.area_id,
    ta.area_name,

    dp.supervisor_id,
    supervisor.column_name AS supervisor_name,

    COALESCE(dp.commission_type, 'NONE') AS commission_type,
    COALESCE(dp.commission_value, 0) AS commission_value,
    COALESCE(dp.collection_target, 0) AS collection_target,
    COALESCE(dp.credit_limit, 0) AS credit_limit,

    COALESCE(dp.is_active, 1) AS is_active,
    dp.notes,

    e.user_id,
    e.date_insert,
    e.updated_at

FROM employees e
         LEFT JOIN delegate_profile dp
                   ON dp.employee_id = e.id
         LEFT JOIN table_area ta
                   ON ta.id = dp.area_id
         LEFT JOIN employees supervisor
                   ON supervisor.id = dp.supervisor_id
WHERE e.job = 4;


-- =====================================================================
-- 6) View: Delegate Sales Performance Daily
-- أداء المندوب اليومي
-- =====================================================================

CREATE OR REPLACE VIEW v_delegate_sales_performance AS
WITH sales_profit AS (
    SELECT
        invoice_number,
        SUM(total_profit) AS total_profit,
        SUM(total_buy_price) AS total_buy_price,
        SUM(total_sel_price) AS total_sel_price
    FROM sales
    GROUP BY invoice_number
),
     sales_agg AS (
         SELECT
             ts.delegate_id,
             ts.invoice_date,
             COUNT(DISTINCT ts.invoice_number) AS invoices_count,
             COUNT(DISTINCT ts.sup_code) AS customers_count,
             SUM(ts.total) AS gross_sales,
             SUM(ts.discount) AS sales_discount,
             SUM(ts.paid_up) AS invoice_cash_collected,
             SUM(COALESCE(sp.total_profit, 0)) AS total_profit
         FROM total_sales ts
                  LEFT JOIN sales_profit sp
                            ON sp.invoice_number = ts.invoice_number
         GROUP BY ts.delegate_id, ts.invoice_date
     ),
     sales_return_profit AS (
         SELECT
             invoice_number,
             SUM(total_profit) AS total_profit,
             SUM(total_buy_price) AS total_buy_price,
             SUM(total_sel_price) AS total_sel_price
         FROM sales_re
         GROUP BY invoice_number
     ),
     sales_return_agg AS (
         SELECT
             tsr.delegate_id,
             tsr.invoice_date,
             COUNT(DISTINCT tsr.id) AS returns_count,
             SUM(tsr.total) AS gross_returns,
             SUM(tsr.discount) AS returns_discount,
             SUM(tsr.paid_from_treasury) AS returned_cash,
             SUM(COALESCE(srp.total_profit, 0)) AS returned_profit
         FROM total_sales_re tsr
                  LEFT JOIN sales_return_profit srp
                            ON srp.invoice_number = tsr.id
         GROUP BY tsr.delegate_id, tsr.invoice_date
     ),
     collection_agg AS (
         SELECT
             ts.delegate_id,
             ca.account_date,
             SUM(ca.paid) AS account_collections
         FROM customers_accounts ca
                  JOIN total_sales ts
                       ON ts.invoice_number = ca.numberInv
         WHERE ca.numberInv > 0
         GROUP BY ts.delegate_id, ca.account_date
     ),
     delegate_dates AS (
         SELECT delegate_id, invoice_date AS report_date
         FROM total_sales

         UNION

         SELECT delegate_id, invoice_date AS report_date
         FROM total_sales_re

         UNION

         SELECT ts.delegate_id, ca.account_date AS report_date
         FROM customers_accounts ca
                  JOIN total_sales ts
                       ON ts.invoice_number = ca.numberInv
         WHERE ca.numberInv > 0
     )
SELECT
    e.id AS delegate_id,
    e.column_name AS delegate_name,
    dd.report_date,

    COALESCE(sa.invoices_count, 0) AS invoices_count,
    COALESCE(sa.customers_count, 0) AS customers_count,

    COALESCE(sa.gross_sales, 0) AS gross_sales,
    COALESCE(sa.sales_discount, 0) AS sales_discount,

    COALESCE(sra.returns_count, 0) AS returns_count,
    COALESCE(sra.gross_returns, 0) AS gross_returns,
    COALESCE(sra.returns_discount, 0) AS returns_discount,

    COALESCE(sa.gross_sales, 0)
        - COALESCE(sra.gross_returns, 0) AS net_sales,

    COALESCE(sa.invoice_cash_collected, 0) AS invoice_cash_collected,
    COALESCE(ca.account_collections, 0) AS account_collections,

    COALESCE(sa.invoice_cash_collected, 0)
        + COALESCE(ca.account_collections, 0) AS total_collected,

    COALESCE(sra.returned_cash, 0) AS returned_cash,

    COALESCE(sa.total_profit, 0)
        - COALESCE(sra.returned_profit, 0) AS net_profit,

    ROUND(
            (
                COALESCE(sa.total_profit, 0)
                    - COALESCE(sra.returned_profit, 0)
                ) * 100 / NULLIF(
                    COALESCE(sa.gross_sales, 0)
                        - COALESCE(sra.gross_returns, 0),
                    0
                          ),
            2
    ) AS profit_percent

FROM delegate_dates dd
         JOIN employees e
              ON e.id = dd.delegate_id
         LEFT JOIN sales_agg sa
                   ON sa.delegate_id = dd.delegate_id
                       AND sa.invoice_date = dd.report_date
         LEFT JOIN sales_return_agg sra
                   ON sra.delegate_id = dd.delegate_id
                       AND sra.invoice_date = dd.report_date
         LEFT JOIN collection_agg ca
                   ON ca.delegate_id = dd.delegate_id
                       AND ca.account_date = dd.report_date
WHERE e.job = 4;


-- =====================================================================
-- 7) View: Delegate Target Achievement
-- تقرير تحقيق أهداف المندوبين
-- =====================================================================

CREATE OR REPLACE VIEW v_delegate_target_achievement AS
WITH period_performance AS (
    SELECT
        dt.id AS target_id,
        dt.delegate_id,

        SUM(COALESCE(v.net_sales, 0)) AS achieved_net_sales,
        SUM(COALESCE(v.gross_sales, 0)) AS achieved_gross_sales,
        SUM(COALESCE(v.total_collected, 0)) AS achieved_collection,
        SUM(COALESCE(v.net_profit, 0)) AS achieved_profit,
        SUM(COALESCE(v.invoices_count, 0)) AS achieved_invoices_count,
        SUM(COALESCE(v.customers_count, 0)) AS achieved_customers_count,

        ROUND(
                SUM(COALESCE(v.net_profit, 0)) * 100
                    / NULLIF(SUM(COALESCE(v.net_sales, 0)), 0),
                2
        ) AS achieved_profit_percent

    FROM delegate_targets dt
             LEFT JOIN v_delegate_sales_performance v
                       ON v.delegate_id = dt.delegate_id
                           AND v.report_date BETWEEN dt.period_from AND dt.period_to
    GROUP BY dt.id, dt.delegate_id
)
SELECT
    dt.id AS target_id,
    dt.target_name,
    dt.target_type,
    dt.period_type,
    dt.period_from,
    dt.period_to,

    dt.delegate_id,
    e.column_name AS delegate_name,

    dt.target_amount,
    dt.target_quantity,
    dt.target_count,
    dt.min_profit_percent,

    CASE dt.target_type
        WHEN 'SALES_AMOUNT' THEN COALESCE(pp.achieved_gross_sales, 0)
        WHEN 'NET_SALES_AMOUNT' THEN COALESCE(pp.achieved_net_sales, 0)
        WHEN 'COLLECTION_AMOUNT' THEN COALESCE(pp.achieved_collection, 0)
        WHEN 'PROFIT_AMOUNT' THEN COALESCE(pp.achieved_profit, 0)
        WHEN 'PROFIT_PERCENT' THEN COALESCE(pp.achieved_profit_percent, 0)
        WHEN 'INVOICES_COUNT' THEN COALESCE(pp.achieved_invoices_count, 0)
        WHEN 'CUSTOMERS_COUNT' THEN COALESCE(pp.achieved_customers_count, 0)
        ELSE 0
        END AS achieved_value,

    CASE dt.target_type
        WHEN 'INVOICES_COUNT' THEN dt.target_count
        WHEN 'CUSTOMERS_COUNT' THEN dt.target_count
        WHEN 'PROFIT_PERCENT' THEN dt.min_profit_percent
        WHEN 'ITEM_QUANTITY' THEN dt.target_quantity
        ELSE dt.target_amount
        END AS required_value,

    ROUND(
            (
                CASE dt.target_type
                    WHEN 'SALES_AMOUNT' THEN COALESCE(pp.achieved_gross_sales, 0)
                    WHEN 'NET_SALES_AMOUNT' THEN COALESCE(pp.achieved_net_sales, 0)
                    WHEN 'COLLECTION_AMOUNT' THEN COALESCE(pp.achieved_collection, 0)
                    WHEN 'PROFIT_AMOUNT' THEN COALESCE(pp.achieved_profit, 0)
                    WHEN 'PROFIT_PERCENT' THEN COALESCE(pp.achieved_profit_percent, 0)
                    WHEN 'INVOICES_COUNT' THEN COALESCE(pp.achieved_invoices_count, 0)
                    WHEN 'CUSTOMERS_COUNT' THEN COALESCE(pp.achieved_customers_count, 0)
                    ELSE 0
                    END
                ) * 100 / NULLIF(
                    CASE dt.target_type
                        WHEN 'INVOICES_COUNT' THEN dt.target_count
                        WHEN 'CUSTOMERS_COUNT' THEN dt.target_count
                        WHEN 'PROFIT_PERCENT' THEN dt.min_profit_percent
                        WHEN 'ITEM_QUANTITY' THEN dt.target_quantity
                        ELSE dt.target_amount
                        END,
                    0
                          ),
            2
    ) AS achievement_percent,

    (
        CASE dt.target_type
            WHEN 'INVOICES_COUNT' THEN dt.target_count
            WHEN 'CUSTOMERS_COUNT' THEN dt.target_count
            WHEN 'PROFIT_PERCENT' THEN dt.min_profit_percent
            WHEN 'ITEM_QUANTITY' THEN dt.target_quantity
            ELSE dt.target_amount
            END
            -
        CASE dt.target_type
            WHEN 'SALES_AMOUNT' THEN COALESCE(pp.achieved_gross_sales, 0)
            WHEN 'NET_SALES_AMOUNT' THEN COALESCE(pp.achieved_net_sales, 0)
            WHEN 'COLLECTION_AMOUNT' THEN COALESCE(pp.achieved_collection, 0)
            WHEN 'PROFIT_AMOUNT' THEN COALESCE(pp.achieved_profit, 0)
            WHEN 'PROFIT_PERCENT' THEN COALESCE(pp.achieved_profit_percent, 0)
            WHEN 'INVOICES_COUNT' THEN COALESCE(pp.achieved_invoices_count, 0)
            WHEN 'CUSTOMERS_COUNT' THEN COALESCE(pp.achieved_customers_count, 0)
            ELSE 0
            END
        ) AS remaining_value,

    CASE
        WHEN CURDATE() < dt.period_from THEN 'NOT_STARTED'
        WHEN (
                 CASE dt.target_type
                     WHEN 'SALES_AMOUNT' THEN COALESCE(pp.achieved_gross_sales, 0)
                     WHEN 'NET_SALES_AMOUNT' THEN COALESCE(pp.achieved_net_sales, 0)
                     WHEN 'COLLECTION_AMOUNT' THEN COALESCE(pp.achieved_collection, 0)
                     WHEN 'PROFIT_AMOUNT' THEN COALESCE(pp.achieved_profit, 0)
                     WHEN 'PROFIT_PERCENT' THEN COALESCE(pp.achieved_profit_percent, 0)
                     WHEN 'INVOICES_COUNT' THEN COALESCE(pp.achieved_invoices_count, 0)
                     WHEN 'CUSTOMERS_COUNT' THEN COALESCE(pp.achieved_customers_count, 0)
                     ELSE 0
                     END
                 ) >= (
                 CASE dt.target_type
                     WHEN 'INVOICES_COUNT' THEN dt.target_count
                     WHEN 'CUSTOMERS_COUNT' THEN dt.target_count
                     WHEN 'PROFIT_PERCENT' THEN dt.min_profit_percent
                     WHEN 'ITEM_QUANTITY' THEN dt.target_quantity
                     ELSE dt.target_amount
                     END
                 ) THEN 'ACHIEVED'
        WHEN CURDATE() > dt.period_to THEN 'FAILED'
        ELSE 'IN_PROGRESS'
        END AS achievement_status,

    dt.status AS target_status,
    dt.notes,
    dt.created_at,
    dt.updated_at

FROM delegate_targets dt
         JOIN employees e
              ON e.id = dt.delegate_id
         LEFT JOIN period_performance pp
                   ON pp.target_id = dt.id
WHERE e.job = 4;


-- =====================================================================
-- 8) View: Delegate Customer Sales
-- مبيعات المندوب حسب العميل
-- =====================================================================

CREATE OR REPLACE VIEW v_delegate_customer_sales AS
SELECT
    ts.delegate_id,
    e.column_name AS delegate_name,

    ts.sup_code AS customer_id,
    c.name AS customer_name,
    c.tel AS customer_tel,
    c.area_id,
    ta.area_name,

    COUNT(DISTINCT ts.invoice_number) AS invoices_count,
    SUM(ts.total) AS gross_sales,
    SUM(ts.discount) AS total_discount,
    SUM(ts.paid_up) AS invoice_cash_collected,
    SUM(ts.total - ts.discount) AS net_sales,

    MIN(ts.invoice_date) AS first_invoice_date,
    MAX(ts.invoice_date) AS last_invoice_date

FROM total_sales ts
         JOIN employees e
              ON e.id = ts.delegate_id
         JOIN custom c
              ON c.id = ts.sup_code
         LEFT JOIN table_area ta
                   ON ta.id = c.area_id
WHERE e.job = 4
GROUP BY
    ts.delegate_id,
    e.column_name,
    ts.sup_code,
    c.name,
    c.tel,
    c.area_id,
    ta.area_name;


-- =====================================================================
-- 9) View: Delegate Item Sales
-- مبيعات المندوب حسب الصنف
-- =====================================================================

CREATE OR REPLACE VIEW v_delegate_item_sales AS
SELECT
    ts.delegate_id,
    e.column_name AS delegate_name,

    s.num AS item_id,
    i.nameItem AS item_name,
    i.barcode,

    SUM(s.quantity * s.type_value) AS total_quantity,
    SUM(s.total_sel_price) AS total_sales,
    SUM(s.total_buy_price) AS total_cost,
    SUM(s.total_profit) AS total_profit,

    ROUND(
            SUM(s.total_profit) * 100 / NULLIF(SUM(s.total_sel_price), 0),
            2
    ) AS profit_percent,

    COUNT(DISTINCT ts.invoice_number) AS invoices_count,
    MIN(ts.invoice_date) AS first_sale_date,
    MAX(ts.invoice_date) AS last_sale_date

FROM sales s
         JOIN total_sales ts
              ON ts.invoice_number = s.invoice_number
         JOIN employees e
              ON e.id = ts.delegate_id
         JOIN items i
              ON i.id = s.num
WHERE e.job = 4
GROUP BY
    ts.delegate_id,
    e.column_name,
    s.num,
    i.nameItem,
    i.barcode;


-- =====================================================================
-- 10) Stored Procedure: Delegate Performance Report
-- تقرير أداء المندوب في فترة
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_delegate_performance_report;

DELIMITER $$

CREATE PROCEDURE sp_delegate_performance_report(
    IN p_delegate_id INT,
    IN p_date_from DATE,
    IN p_date_to DATE
)
BEGIN
    SELECT
        delegate_id,
        delegate_name,

        SUM(invoices_count) AS invoices_count,
        SUM(customers_count) AS customers_count,

        ROUND(SUM(gross_sales), 2) AS gross_sales,
        ROUND(SUM(sales_discount), 2) AS sales_discount,

        ROUND(SUM(gross_returns), 2) AS gross_returns,
        ROUND(SUM(returns_discount), 2) AS returns_discount,

        ROUND(SUM(net_sales), 2) AS net_sales,

        ROUND(SUM(invoice_cash_collected), 2) AS invoice_cash_collected,
        ROUND(SUM(account_collections), 2) AS account_collections,
        ROUND(SUM(total_collected), 2) AS total_collected,

        ROUND(SUM(returned_cash), 2) AS returned_cash,

        ROUND(SUM(net_profit), 2) AS net_profit,

        ROUND(
                SUM(net_profit) * 100 / NULLIF(SUM(net_sales), 0),
                2
        ) AS profit_percent

    FROM v_delegate_sales_performance
    WHERE report_date BETWEEN p_date_from AND p_date_to
      AND (p_delegate_id IS NULL OR delegate_id = p_delegate_id)
    GROUP BY delegate_id, delegate_name
    ORDER BY net_sales DESC;
END$$

DELIMITER ;


-- =====================================================================
-- 11) Stored Procedure: Delegate Targets Report
-- تقرير أهداف المندوبين
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_delegate_targets_report;

DELIMITER $$

CREATE PROCEDURE sp_delegate_targets_report(
    IN p_delegate_id INT,
    IN p_date_from DATE,
    IN p_date_to DATE
)
BEGIN
    SELECT *
    FROM v_delegate_target_achievement
    WHERE period_from <= p_date_to
      AND period_to >= p_date_from
      AND (p_delegate_id IS NULL OR delegate_id = p_delegate_id)
    ORDER BY period_from DESC, delegate_name, target_id DESC;
END$$

DELIMITER ;


-- =====================================================================
-- 12) Stored Procedure: Calculate Delegate Commission
-- حساب عمولة مندوب في فترة
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_calculate_delegate_commission;

DELIMITER $$

CREATE PROCEDURE sp_calculate_delegate_commission(
    IN p_delegate_id INT,
    IN p_date_from DATE,
    IN p_date_to DATE,
    IN p_user_id INT
)
BEGIN
    DECLARE v_commission_type VARCHAR(20);
    DECLARE v_commission_value DECIMAL(14,2);

    DECLARE v_sales_amount DECIMAL(14,2);
    DECLARE v_profit_amount DECIMAL(14,2);
    DECLARE v_invoices_count INT;

    DECLARE v_commission_amount DECIMAL(14,2);

    SELECT
        COALESCE(commission_type, 'NONE'),
        COALESCE(commission_value, 0)
    INTO
        v_commission_type,
        v_commission_value
    FROM delegate_profile
    WHERE employee_id = p_delegate_id
    LIMIT 1;

    IF v_commission_type IS NULL OR v_commission_type = 'NONE' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'لا يوجد نوع عمولة محدد لهذا المندوب';
    END IF;

    SELECT
        COALESCE(SUM(net_sales), 0),
        COALESCE(SUM(net_profit), 0),
        COALESCE(SUM(invoices_count), 0)
    INTO
        v_sales_amount,
        v_profit_amount,
        v_invoices_count
    FROM v_delegate_sales_performance
    WHERE delegate_id = p_delegate_id
      AND report_date BETWEEN p_date_from AND p_date_to;

    SET v_commission_amount =
            CASE v_commission_type
                WHEN 'SALES_PERCENT' THEN v_sales_amount * v_commission_value / 100
                WHEN 'PROFIT_PERCENT' THEN v_profit_amount * v_commission_value / 100
                WHEN 'FIXED_PER_INVOICE' THEN v_invoices_count * v_commission_value
                ELSE 0
                END;

    INSERT INTO delegate_commissions
    (
        delegate_id,
        commission_date,
        reference_type,
        reference_id,
        sales_amount,
        profit_amount,
        commission_type,
        commission_rate,
        commission_amount,
        payment_status,
        paid_amount,
        notes,
        user_id
    )
    VALUES
        (
            p_delegate_id,
            CURDATE(),
            'PERIOD',
            NULL,
            v_sales_amount,
            v_profit_amount,
            v_commission_type,
            v_commission_value,
            v_commission_amount,
            'UNPAID',
            0,
            CONCAT('عمولة فترة من ', p_date_from, ' إلى ', p_date_to),
            COALESCE(p_user_id, 1)
        );

    SELECT
        LAST_INSERT_ID() AS commission_id,
        p_delegate_id AS delegate_id,
        v_commission_type AS commission_type,
        v_commission_value AS commission_value,
        v_sales_amount AS sales_amount,
        v_profit_amount AS profit_amount,
        v_invoices_count AS invoices_count,
        v_commission_amount AS commission_amount;
END$$

DELIMITER ;


-- =====================================================================
-- 13) Permissions
-- صلاحيات المندوبين
-- =====================================================================

INSERT IGNORE INTO permission
(
    code,
    name_ar,
    module,
    action,
    description,
    sort_order,
    active
)
VALUES
    ('delegates.show', 'عرض المندوبين', 'delegates', 'show', 'عرض قائمة المندوبين', 204, 1),
    ('delegates.update.profile', 'تعديل بيانات المندوب', 'delegates', 'update.profile', 'تعديل إعدادات وبيانات المندوب', 205, 1),
    ('delegates.reports', 'تقارير المندوبين', 'delegates', 'reports', 'عرض تقارير أداء المندوبين', 206, 1),
    ('delegates.commissions', 'عمولات المندوبين', 'delegates', 'commissions', 'إدارة عمولات المندوبين', 207, 1),
    ('delegates.targets', 'أهداف المندوبين', 'delegates', 'targets', 'إدارة أهداف المندوبين', 208, 1);


-- Admin Role
INSERT INTO role_permission
(
    role_id,
    permission_id,
    check_status
)
SELECT
    1,
    id,
    1
FROM permission
WHERE code LIKE 'delegates.%'
   OR code LIKE 'targets.%'
ON DUPLICATE KEY UPDATE
    check_status = 1;


-- Sales Manager Role
INSERT INTO role_permission
(
    role_id,
    permission_id,
    check_status
)
SELECT
    3,
    id,
    1
FROM permission
WHERE code LIKE 'delegates.%'
   OR code LIKE 'targets.%'
ON DUPLICATE KEY UPDATE
    check_status = 1;


-- Admin User Direct Permissions
INSERT INTO user_permission
(
    user_id,
    permission_id,
    check_status
)
SELECT
    1,
    id,
    1
FROM permission
WHERE code LIKE 'delegates.%'
   OR code LIKE 'targets.%'
ON DUPLICATE KEY UPDATE
    check_status = 1;


-- =====================================================================
-- 14) Migration Register
-- =====================================================================

INSERT INTO database_migrations
(
    version,
    description,
    executed_at
)
SELECT
    '4.2.1',
    'Delegates targets reports and commissions module',
    NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM database_migrations
    WHERE version = '4.2.1'
);


SELECT '013_delegate_targets.sql executed successfully' AS status;