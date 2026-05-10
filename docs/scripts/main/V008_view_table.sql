-- =====================================================================
-- V017 — Views (cleaned & optimized)
-- =====================================================================

-- --------------------------------------purchase_names_table---------------------------------------

CREATE OR REPLACE VIEW purchase_names_table AS
SELECT p.id,
       p.invoice_number,
       p.num,
       p.type,
       p.type_value,
       p.quantity AS quantity,
       p.price,
       p.discount,
       p.expiration_date,
       i.nameItem,
       i.barcode,
       u.unit_name,
       t.id       AS name_id,
       t.name,
       tb.invoice_date,
       tb.stock_id
FROM purchase p
         JOIN items     i  ON i.id = p.num
         JOIN units     u  ON p.type = u.unit_id
         JOIN total_buy tb ON tb.invoice_number = p.invoice_number
         JOIN suppliers t  ON t.id = tb.sup_code;

-- --------------------------------------sales_names_table------------------------------------------

CREATE OR REPLACE VIEW sales_names_table AS
SELECT s.id,
       s.invoice_number,
       s.num,
       s.type,
       s.type_value,
       s.quantity,
       s.price,
       s.buy_price,
       s.total_sel_price AS total_sales,
       s.total_buy_price AS total_buy,
       s.total_profit,
       s.discount,
       s.item_has_package,
       s.expiration_date,
       i.nameItem,
       i.barcode,
       u.unit_name,
       c.id              AS name_id,
       c.name,
       ts.invoice_date,
       ts.stock_id
FROM sales s
         JOIN items       i  ON i.id = s.num
         JOIN units       u  ON s.type = u.unit_id
         JOIN total_sales ts ON ts.invoice_number = s.invoice_number
         JOIN custom      c  ON c.id = ts.sup_code;

-- --------------------------------------sales_return_names_table-----------------------------------

CREATE OR REPLACE VIEW sales_return_names_table AS
SELECT sr.id,
       sr.invoice_number,
       sr.item_id,
       sr.type,
       sr.type_value,
       sr.quantity,
       sr.price,
       sr.buy_price,
       sr.total_sel_price,
       sr.total_buy_price,
       sr.total_profit,
       sr.discount,
       sr.expiration_date,
       i.nameItem,
       i.barcode,
       u.unit_name,
       tsr.invoice_date,
       tsr.stock_id
FROM sales_re sr
         JOIN items          i   ON i.id = sr.item_id
         JOIN units          u   ON sr.type = u.unit_id
         JOIN total_sales_re tsr ON tsr.id = sr.invoice_number;

-- --------------------------------------purchase_return_names_table--------------------------------

CREATE OR REPLACE VIEW purchase_return_names_table AS
SELECT pr.id,
       pr.invoice_number,
       pr.item_id,
       pr.type,
       pr.type_value,
       pr.quantity,
       pr.price,
       pr.discount,
       pr.expiration_date,
       i.nameItem,
       i.barcode,
       u.unit_name,
       tbr.invoice_date,
       tbr.stock_id
FROM purchase_re pr
         JOIN items        i   ON i.id = pr.item_id
         JOIN units        u   ON pr.type = u.unit_id
         JOIN total_buy_re tbr ON tbr.id = pr.invoice_number;

-- --------------------------------------stock_transfer_view----------------------------------------

CREATE OR REPLACE VIEW stock_transfer_view AS
SELECT st.id,
       st.transfer_date,
       st.stock_from,
       st.stock_to,
       stf.stock_name AS name_from,
       stt.stock_name AS name_to,
       stl.item_id,
       stl.quantity,
       i.nameItem
FROM stock_transfer st
         JOIN stock_transfer_list stl ON st.id = stl.stock_transfer_id
         JOIN items  i   ON i.id = stl.item_id
         JOIN stocks stf ON stf.stock_id = st.stock_from
         JOIN stocks stt ON stt.stock_id = st.stock_to;

-- --------------------------------------quantity_items_table (optimized via JOINs)----------------

CREATE OR REPLACE VIEW quantity_items_table AS
WITH purchase_agg AS (SELECT stock_id, num AS item_id,
                             SUM(quantity * type_value) AS qty
                      FROM purchase_names_table
                      GROUP BY stock_id, num),
     sales_agg AS (SELECT stock_id, num AS item_id,
                          SUM(quantity * type_value) AS qty
                   FROM sales_names_table
                   GROUP BY stock_id, num),
     purchase_re_agg AS (SELECT stock_id, item_id,
                                SUM(quantity * type_value) AS qty
                         FROM purchase_return_names_table
                         GROUP BY stock_id, item_id),
     sales_re_agg AS (SELECT stock_id, item_id,
                             SUM(quantity * type_value) AS qty
                      FROM sales_return_names_table
                      GROUP BY stock_id, item_id),
     transfer_from_agg AS (SELECT stock_from AS stock_id, item_id, SUM(quantity) AS qty
                           FROM stock_transfer_view
                           GROUP BY stock_from, item_id),
     transfer_to_agg AS (SELECT stock_to AS stock_id, item_id, SUM(quantity) AS qty
                         FROM stock_transfer_view
                         GROUP BY stock_to, item_id)
SELECT ist.item_id,
       ist.stock_id,
       ist.first_balance,
       COALESCE(pa.qty,   0) AS quantityPurchase,
       COALESCE(sa.qty,   0) AS quantitySales,
       COALESCE(pra.qty,  0) AS quantityPurchaseRe,
       COALESCE(sra.qty,  0) AS quantitySalesRe,
       COALESCE(tfa.qty,  0) AS fromStock,
       COALESCE(tta.qty,  0) AS toStock
FROM items_stock ist
         LEFT JOIN purchase_agg     pa  ON pa.stock_id  = ist.stock_id AND pa.item_id  = ist.item_id
         LEFT JOIN sales_agg        sa  ON sa.stock_id  = ist.stock_id AND sa.item_id  = ist.item_id
         LEFT JOIN purchase_re_agg  pra ON pra.stock_id = ist.stock_id AND pra.item_id = ist.item_id
         LEFT JOIN sales_re_agg     sra ON sra.stock_id = ist.stock_id AND sra.item_id = ist.item_id
         LEFT JOIN transfer_from_agg tfa ON tfa.stock_id = ist.stock_id AND tfa.item_id = ist.item_id
         LEFT JOIN transfer_to_agg   tta ON tta.stock_id = ist.stock_id AND tta.item_id = ist.item_id;

-- --------------------------------------total_sales_names_table------------------------------------

CREATE OR REPLACE VIEW total_sales_names_table AS
WITH TotalPaidAmounts AS (SELECT numberInv AS InvoiceNumber,
                                 SUM(paid) AS TotalPaid
                          FROM customers_accounts
                          WHERE numberInv > 0
                          GROUP BY numberInv),
     sales_invoice_profit AS (SELECT invoice_number,
                                     SUM(total_profit)    AS total_profit,
                                     SUM(total_buy_price) AS total_buy_price
                              FROM sales
                              GROUP BY invoice_number)
SELECT ts.invoice_number,
       ts.sup_code,
       ts.invoice_type,
       ts.invoice_date,
       ts.total,
       ts.discount,
       ts.paid_up,
       ts.stock_id,
       ts.delegate_id,
       ts.treasury_id,
       ts.notes,
       ts.date_insert,
       c.name,
       s.stock_name,
       e.column_name,
       t.t_name,
       ts.user_id,
       ROUND(sip.total_profit, 2)                                   AS total_profit,
       sip.total_buy_price,
       ROUND((sip.total_profit * 100) / NULLIF(ts.total, 0), 2)     AS profit_percent,
       COALESCE(tpa.TotalPaid, 0)                                   AS OtherPaid
FROM total_sales ts
         JOIN custom    c  ON c.id = ts.sup_code
         JOIN stocks    s  ON s.stock_id = ts.stock_id
         JOIN employees e  ON ts.delegate_id = e.id
         JOIN treasury  t  ON ts.treasury_id = t.id
         LEFT JOIN sales_invoice_profit sip ON ts.invoice_number = sip.invoice_number
         LEFT JOIN TotalPaidAmounts     tpa ON ts.invoice_number = tpa.InvoiceNumber;

-- --------------------------------------total_purchase_names_table---------------------------------

CREATE OR REPLACE VIEW total_purchase_names_table AS
WITH PaidAmounts AS (SELECT numberInv AS InvoiceNumber, SUM(paid) AS total_paid
                     FROM suppliers_accounts
                     WHERE numberInv > 0
                     GROUP BY numberInv)
SELECT tb.invoice_number,
       tb.sup_code,
       tb.invoice_type,
       tb.invoice_date,
       tb.total,
       tb.discount,
       tb.paid_up,
       tb.stock_id,
       tb.treasury_id,
       tb.notes,
       tb.date_insert,
       c.name,
       s.stock_name,
       t.t_name,
       tb.user_id,
       COALESCE(pa.total_paid, 0) AS OtherPaid
FROM total_buy tb
         JOIN suppliers c ON c.id = tb.sup_code
         JOIN stocks    s ON s.stock_id = tb.stock_id
         JOIN treasury  t ON tb.treasury_id = t.id
         LEFT JOIN PaidAmounts pa ON tb.invoice_number = pa.InvoiceNumber;

-- --------------------------------------total_purchase_return_names_table--------------------------

CREATE OR REPLACE VIEW total_purchase_return_names_table AS
SELECT tbr.id,
       tbr.sup_id,
       tbr.invoice_date,
       tbr.total,
       tbr.discount,
       tbr.paid_to_treasury,
       tbr.stock_id,
       tbr.treasury_id,
       tbr.notes,
       tbr.invoice_type,
       tbr.date_insert,
       c.name,
       s.stock_name,
       t.t_name,
       tbr.user_id
FROM total_buy_re tbr
         JOIN suppliers c ON c.id = tbr.sup_id
         JOIN stocks    s ON s.stock_id = tbr.stock_id
         JOIN treasury  t ON tbr.treasury_id = t.id;

-- --------------------------------------total_sales_return_names_table-----------------------------

CREATE OR REPLACE VIEW total_sales_return_names_table AS
WITH sales_invoice_profit AS (SELECT invoice_number,
                                     SUM(total_profit)    AS total_profit,
                                     SUM(total_buy_price) AS total_buy_price
                              FROM sales_re
                              GROUP BY invoice_number)
SELECT tsr.id,
       tsr.sup_id,
       tsr.invoice_date,
       tsr.total,
       tsr.discount,
       tsr.paid_from_treasury,
       tsr.stock_id,
       tsr.delegate_id,
       tsr.treasury_id,
       tsr.notes,
       tsr.invoice_type,
       tsr.date_insert,
       c.name,
       s.stock_name,
       t.t_name,
       e.column_name,
       tsr.user_id,
       ROUND(sip.total_profit, 2)                                AS total_profit,
       sip.total_buy_price,
       ROUND((sip.total_profit * 100) / NULLIF(tsr.total, 0), 2) AS profit_percent
FROM total_sales_re tsr
         JOIN custom    c ON c.id = tsr.sup_id
         JOIN stocks    s ON s.stock_id = tsr.stock_id
         JOIN treasury  t ON tsr.treasury_id = t.id
         JOIN employees e ON e.id = tsr.delegate_id
         LEFT JOIN sales_invoice_profit sip ON tsr.id = sip.invoice_number;

-- --------------------------------------account_customer_table-------------------------------------

CREATE OR REPLACE VIEW account_customer_table AS
SELECT 0                                     AS account_num,
       c.id                                  AS account_code,
       DATE_FORMAT(c.created_at, '%Y-%m-%d') AS account_date,
       c.first_balance                       AS purchase,
       0                                     AS discount,
       0                                     AS paid,
       'رصيد اول'                            AS notes,
       1                                     AS information,
       0                                     AS type,
       c.created_at                          AS created_at,
       0                                     AS treasury_id,
       0                                     AS numberInv
FROM custom c
UNION ALL
SELECT account_num,
       account_code,
       account_date,
       purchase,
       0        AS discount,
       paid,
       notes,
       2        AS information,
       0        AS type,
       created_at,
       treasury_id,
       numberInv
FROM customers_accounts
UNION ALL
SELECT invoice_number,
       sup_code,
       invoice_date,
       total,
       discount,
       paid_up,
       notes,
       3           AS information,
       invoice_type AS type,
       date_insert,
       treasury_id,
       0           AS numberInv
FROM total_sales
UNION ALL
-- إذا كانت الفاتورة نقدا يتم خصم كل المدفوع
-- واذا كانت اجل: مبلغ من الخزينة ومبلغ من الحساب
SELECT tsr.id,
       tsr.sup_id,
       tsr.invoice_date,
       IF(tsr.invoice_type = 1, tsr.total, 0),
       IF(tsr.invoice_type = 1, tsr.discount, 0),
       tsr.paid_from_treasury,
       tsr.notes,
       4                    AS information,
       tsr.invoice_type     AS type,
       tsr.date_insert,
       tsr.treasury_id,
       0                    AS numberInv
FROM total_sales_re tsr
ORDER BY created_at;

-- --------------------------------------account_suppliers_table------------------------------------

CREATE OR REPLACE VIEW account_suppliers_table AS
SELECT 0                                      AS account_num,
       c.id                                   AS account_code,
       DATE_FORMAT(c.date_insert, '%Y-%m-%d') AS account_date,
       c.first_balance                        AS purchase,
       0                                      AS discount,
       0                                      AS paid,
       'رصيد اول'                             AS notes,
       1                                      AS information,
       0                                      AS type,
       c.date_insert                          AS date_insert,
       0                                      AS treasury_id,
       0                                      AS numberInv
FROM suppliers c
UNION ALL
SELECT account_num,
       account_code,
       account_date,
       purchase,
       0        AS discount,
       paid,
       notes,
       2        AS information,
       0        AS type,
       date_insert,
       treasury_id,
       numberInv
FROM suppliers_accounts
UNION ALL
SELECT invoice_number,
       sup_code,
       invoice_date,
       total,
       discount,
       paid_up,
       total_buy.notes,
       3            AS information,
       invoice_type AS type,
       date_insert,
       treasury_id,
       0            AS numberInv
FROM total_buy
UNION ALL
SELECT tbr.id,
       tbr.sup_id,
       tbr.invoice_date,
       IF(tbr.invoice_type = 1, tbr.total, 0),
       IF(tbr.invoice_type = 1, tbr.discount, 0),
       tbr.paid_to_treasury,
       tbr.notes,
       4                AS information,
       tbr.invoice_type AS type,
       tbr.date_insert,
       tbr.treasury_id,
       0                AS numberInv
FROM total_buy_re tbr
ORDER BY date_insert;

-- --------------------------------------card_item_view---------------------------------------------

CREATE OR REPLACE VIEW card_item_view AS
WITH sales_data AS (SELECT s.id,
                           s.invoice_number,
                           t.invoice_date,
                           s.num       AS item_num,
                           s.type      AS unit_type,
                           s.quantity,
                           s.price,
                           s.buy_price,
                           s.discount,
                           c.name      AS name_custom,
                           t.date_insert,
                           t.delegate_id,
                           'sales'     AS table_name,
                           s.expiration_date
                    FROM sales s
                             JOIN total_sales t ON t.invoice_number = s.invoice_number
                             JOIN custom      c ON t.sup_code = c.id),
     sales_return_data AS (SELECT sre.id,
                                  sre.invoice_number,
                                  t.invoice_date,
                                  sre.item_id AS item_num,
                                  sre.type    AS unit_type,
                                  sre.quantity,
                                  sre.price,
                                  sre.buy_price,
                                  0           AS discount,
                                  c.name      AS name_custom,
                                  t.date_insert,
                                  t.delegate_id,
                                  'sales_re'  AS table_name,
                                  sre.expiration_date
                           FROM sales_re sre
                                    JOIN total_sales_re t ON t.id = sre.invoice_number
                                    JOIN custom         c ON t.sup_id = c.id),
     purchase_data AS (SELECT p.id,
                              p.invoice_number,
                              t.invoice_date,
                              p.num      AS item_num,
                              p.type     AS unit_type,
                              p.quantity,
                              p.price,
                              0          AS buy_price,
                              p.discount,
                              s.name     AS name_custom,
                              t.date_insert,
                              0          AS delegate_id,
                              'purchase' AS table_name,
                              p.expiration_date
                       FROM purchase p
                                JOIN total_buy  t ON t.invoice_number = p.invoice_number
                                JOIN suppliers  s ON t.sup_code = s.id),
     purchase_return_data AS (SELECT pre.id,
                                     pre.invoice_number,
                                     t.invoice_date,
                                     pre.item_id AS item_num,
                                     pre.type    AS unit_type,
                                     pre.quantity,
                                     pre.price,
                                     0           AS buy_price,
                                     0           AS discount,
                                     s.name      AS name_custom,
                                     t.date_insert,
                                     0           AS delegate_id,
                                     'purchase_re' AS table_name,
                                     pre.expiration_date
                              FROM purchase_re pre
                                       JOIN total_buy_re t ON t.id = pre.invoice_number
                                       JOIN suppliers    s ON t.sup_id = s.id)
SELECT * FROM sales_data
UNION ALL
SELECT * FROM sales_return_data
UNION ALL
SELECT * FROM purchase_data
UNION ALL
SELECT * FROM purchase_return_data;

-- --------------------------------------card_item_view_details-------------------------------------

CREATE OR REPLACE VIEW card_item_view_details AS
SELECT c.id,
       c.invoice_number,
       c.invoice_date,
       c.item_num,
       c.unit_type,
       c.quantity,
       c.price,
       c.buy_price,
       IF(c.table_name IN ('purchase','purchase_re'), 0,
          (c.price - c.buy_price) * c.quantity) AS profit,
       c.discount,
       c.name_custom,
       c.date_insert,
       c.delegate_id,
       em.column_name                            AS delegate_name,
       c.table_name,
       i.barcode,
       i.nameItem,
       un.unit_name,
       c.expiration_date
FROM card_item_view c
         JOIN items i  ON c.item_num  = i.id
         JOIN units un ON c.unit_type = un.unit_id
         LEFT JOIN employees em ON c.delegate_id = em.id;

-- --------------------------------------expenses_details_view--------------------------------------

CREATE OR REPLACE VIEW expenses_details_view AS
SELECT ed.id,
       ed.type_code,
       ed.date,
       ed.amount,
       ed.notes,
       ed.treasury_id,
       ed.emp_id,
       e.expenses_name,
       IFNULL(e2.column_name, '') AS column_name
FROM expenses_details ed
         JOIN expenses e ON e.id = ed.type_code
         LEFT JOIN expense_salary es ON ed.id = es.expenses_details_id
         LEFT JOIN employees     e2 ON e2.id = es.employee_id;

-- --------------------------------------mini_quantity_view-----------------------------------------

CREATE OR REPLACE VIEW mini_quantity_view AS
WITH calculated_balance AS (SELECT item_id,
                                   SUM((first_balance + quantityPurchase + quantitySalesRe + toStock) -
                                       (quantitySales + quantityPurchaseRe + fromStock)) AS balance
                            FROM quantity_items_table
                            GROUP BY item_id)
SELECT i.id,
       i.nameItem,
       i.mini_quantity,
       cb.balance
FROM items i
         JOIN calculated_balance cb ON i.id = cb.item_id
WHERE i.mini_quantity >= cb.balance;

-- --------------------------------------target_delegate--------------------------------------------

CREATE OR REPLACE VIEW target_delegate AS
WITH sales_sums AS (SELECT delegate_id,
                           YEAR(invoice_date)  AS sales_year,
                           MONTH(invoice_date) AS sales_month,
                           SUM(total)          AS total_sales_sum
                    FROM total_sales
                    GROUP BY delegate_id, YEAR(invoice_date), MONTH(invoice_date)),
     sales_re_sums AS (SELECT delegate_id,
                              YEAR(invoice_date)  AS sales_year,
                              MONTH(invoice_date) AS sales_month,
                              SUM(total)          AS total_sales_re_sum
                       FROM total_sales_re
                       GROUP BY delegate_id, YEAR(invoice_date), MONTH(invoice_date)),
     sales_data AS (SELECT s.delegate_id,
                           s.sales_year,
                           s.sales_month,
                           COALESCE(s.total_sales_sum,    0)                                  AS total_sales_sum,
                           COALESCE(sr.total_sales_re_sum, 0)                                 AS total_sales_re_sum,
                           COALESCE(s.total_sales_sum, 0) - COALESCE(sr.total_sales_re_sum,0) AS sales_difference
                    FROM sales_sums s
                             LEFT JOIN sales_re_sums sr
                                       ON sr.delegate_id = s.delegate_id
                                           AND sr.sales_year  = s.sales_year
                                           AND sr.sales_month = s.sales_month)
SELECT e.id                                                           AS employee_id,
       e.column_name                                                  AS employee_name,
       sd.total_sales_sum,
       sd.total_sales_re_sum,
       sd.sales_difference                                            AS Amount,
       tgt.target_ratio1,
       tgt.rate_1,
       tgt.target_ratio2,
       tgt.rate_2,
       tgt.target_ratio3,
       tgt.rate_3,
       tgt.target,
       sd.sales_year,
       sd.sales_month,
       IF(sd.sales_difference >= (tgt.target * tgt.target_ratio1) / 100,
          (sd.sales_difference * tgt.rate_1) / 100,
          IF(sd.sales_difference >= (tgt.target * tgt.target_ratio2) / 100,
             (sd.sales_difference * tgt.rate_2) / 100,
             (sd.sales_difference * tgt.rate_3) / 100)) AS commission
FROM employees e
         JOIN sales_data      sd  ON e.id = sd.delegate_id
         JOIN targeted_sales  tgt ON e.id = tgt.delegate_id
ORDER BY e.id;

-- --------------------------------------treasury_balance-------------------------------------------

CREATE OR REPLACE VIEW treasury_balance AS
WITH cte_union_data AS (SELECT invoice_number AS id_no,
                               invoice_date   AS date_val,
                               0              AS income,
                               paid_up        AS output,
                               treasury_id,
                               date_insert,
                               user_id,
                               'المشتريات'    AS information
                        FROM total_buy
                        UNION ALL
                        SELECT id,
                               invoice_date,
                               IF(invoice_type = 1, paid_to_treasury, total - discount - paid_to_treasury) AS income,
                               0              AS output,
                               treasury_id,
                               date_insert,
                               user_id,
                               'مرتجع المشتريات'
                        FROM total_buy_re
                        UNION ALL
                        SELECT invoice_number,
                               invoice_date,
                               paid_up,
                               0,
                               treasury_id,
                               date_insert,
                               user_id,
                               'المبيعات'
                        FROM total_sales
                        UNION ALL
                        SELECT id,
                               invoice_date,
                               0,
                               IF(invoice_type = 1, paid_from_treasury, total - discount - paid_from_treasury) AS output,
                               treasury_id,
                               date_insert,
                               user_id,
                               'مرتجع المبيعات'
                        FROM total_sales_re
                        UNION ALL
                        SELECT account_num,
                               account_date,
                               paid,
                               0,
                               treasury_id,
                               created_at,
                               user_id,
                               'حسابات العملاء'
                        FROM customers_accounts
                        UNION ALL
                        SELECT account_num,
                               account_date,
                               0,
                               paid,
                               treasury_id,
                               date_insert,
                               user_id,
                               'حسابات الموردين'
                        FROM suppliers_accounts
                        UNION ALL
                        SELECT id,
                               date,
                               0,
                               amount,
                               treasury_id,
                               date_insert,
                               user_id,
                               'المصروفات'
                        FROM expenses_details
                        UNION ALL
                        SELECT id,
                               date_inter,
                               IF(deposit_or_expenses = 1, amount, 0) AS income,
                               IF(deposit_or_expenses = 2, amount, 0) AS output,
                               treasury_id,
                               date_insert,
                               user_id,
                               IF(deposit_or_expenses = 1, 'إيداع', 'صرف')
                        FROM treasury_deposit_expenses)
SELECT c.id_no,
       c.date_val,
       c.income,
       c.output,
       c.treasury_id,
       c.date_insert,
       c.user_id,
       c.information,
       t.t_name    AS treasury_name,
       u.user_name AS user_name
FROM cte_union_data c
         JOIN treasury t ON t.id = c.treasury_id
         JOIN users    u ON u.id = c.user_id
ORDER BY date_val;

-- --------------------------------------treasury_transfers_and_names-------------------------------

CREATE OR REPLACE VIEW treasury_transfers_and_names AS
SELECT tt.id,
       tt.treasury_from,
       tt.treasury_to,
       tt.amount,
       tt.transfer_date,
       tt.notes,
       tFrom.t_name AS treasury_name_from,
       tTo.t_name   AS treasury_name_to
FROM treasury_transfers tt
         JOIN treasury tFrom ON tFrom.id = tt.treasury_from
         JOIN treasury tTo   ON tTo.id   = tt.treasury_to;

-- --------------------------------------treasury_balance_after_convert-----------------------------

CREATE OR REPLACE VIEW treasury_balance_after_convert AS
WITH sum_treasury_amount_from AS (SELECT treasury_from, COALESCE(SUM(amount), 0) AS sum_transfer_from
                                  FROM treasury_transfers
                                  GROUP BY treasury_from),
     sum_treasury_amount_to AS (SELECT treasury_to, COALESCE(SUM(amount), 0) AS sum_transfer_to
                                FROM treasury_transfers
                                GROUP BY treasury_to)
SELECT treasury.*,
       f.sum_transfer_from,
       t.sum_transfer_to,
       (treasury.amount + COALESCE(t.sum_transfer_to, 0) - COALESCE(f.sum_transfer_from, 0))
           AS amount_after_transfer
FROM treasury
         LEFT JOIN sum_treasury_amount_from f ON f.treasury_from = treasury.id
         LEFT JOIN sum_treasury_amount_to   t ON t.treasury_to   = treasury.id;

-- --------------------------------------account_customer_totals------------------------------------

CREATE OR REPLACE VIEW account_customer_totals AS
SELECT act.account_code,
       c.name,
       SUM(act.purchase)                                           AS purchase,
       SUM(act.discount)                                           AS discount,
       SUM(act.paid)                                               AS paid,
       ROUND(SUM(act.purchase) - SUM(act.discount) - SUM(act.paid), 2) AS amount,
       MAX(act.account_date)                                       AS account_date,
       ta.id                                                       AS area_id,
       ta.area_name                                                AS area_name
FROM account_customer_table act
         JOIN custom     c  ON act.account_code = c.id
         JOIN table_area ta ON ta.id = c.area_id
GROUP BY act.account_code, c.name, ta.id, ta.area_name;

-- --------------------------------------account_suppliers_totals-----------------------------------

CREATE OR REPLACE VIEW account_suppliers_totals AS
SELECT ast.account_code,
       c.name,
       ROUND(SUM(ast.purchase), 2)                                       AS purchase,
       ROUND(SUM(ast.discount), 2)                                       AS discount,
       ROUND(SUM(ast.paid), 2)                                           AS paid,
       ROUND(SUM(ast.purchase) - SUM(ast.discount) - SUM(ast.paid), 2)   AS amount,
       MAX(ast.account_date)                                             AS account_date
FROM account_suppliers_table ast
         JOIN suppliers c ON ast.account_code = c.id
GROUP BY ast.account_code, c.name;

-- --------------------------------------earnings_reports-------------------------------------------

CREATE OR REPLACE VIEW earnings_reports AS
WITH computed_profit AS (SELECT ts.invoice_number,
                                SUM(snt.total_profit) AS profit
                         FROM sales_names_table snt
                                  JOIN total_sales ts ON snt.invoice_number = ts.invoice_number
                         GROUP BY ts.invoice_number),
     computed_profit_sales_return AS (SELECT ts.id,
                                             SUM(snt.total_profit) AS profit
                                      FROM sales_return_names_table snt
                                               JOIN total_sales_re ts ON snt.invoice_number = ts.id
                                      GROUP BY ts.id),
     sales_query AS (SELECT ts.invoice_number AS id,
                            ts.invoice_date,
                            ts.total,
                            ts.discount,
                            ts.paid_up,
                            ts.treasury_id,
                            ts.date_insert,
                            ts.user_id,
                            'sales'           AS table_name,
                            cp.profit
                     FROM total_sales ts
                              JOIN computed_profit cp ON cp.invoice_number = ts.invoice_number),
     buy_query AS (SELECT invoice_number AS id,
                          invoice_date,
                          total,
                          discount,
                          paid_up,
                          treasury_id,
                          date_insert,
                          user_id,
                          'buy'          AS table_name,
                          0              AS profit
                   FROM total_buy),
     sales_return_query AS (SELECT tsr.id,
                                   tsr.invoice_date,
                                   tsr.total,
                                   tsr.discount,
                                   tsr.paid_from_treasury AS paid_up,
                                   tsr.treasury_id,
                                   tsr.date_insert,
                                   tsr.user_id,
                                   'sales_re'             AS table_name,
                                   cpsr.profit
                            FROM total_sales_re tsr
                                     JOIN computed_profit_sales_return cpsr ON cpsr.id = tsr.id),
     buy_return_query AS (SELECT id,
                                 invoice_date,
                                 total,
                                 discount,
                                 paid_to_treasury AS paid_up,
                                 treasury_id,
                                 date_insert,
                                 user_id,
                                 'buy_re'         AS table_name,
                                 0                AS profit
                          FROM total_buy_re),
     expenses_query AS (SELECT id,
                               date        AS invoice_date,
                               amount      AS total,
                               0           AS discount,
                               0           AS paid_up,
                               treasury_id,
                               date_insert,
                               user_id,
                               'expenses'  AS table_name,
                               0           AS profit
                        FROM expenses_details),
     customer_account_query AS (SELECT account_num          AS id,
                                       account_date         AS invoice_date,
                                       paid                 AS total,
                                       0                    AS discount,
                                       0                    AS paid_up,
                                       treasury_id,
                                       created_at           AS date_insert,
                                       user_id,
                                       'customers_accounts' AS table_name,
                                       0                    AS profit
                                FROM customers_accounts),
     suppliers_accounts_query AS (SELECT account_num          AS id,
                                         account_date         AS invoice_date,
                                         paid                 AS total,
                                         0                    AS discount,
                                         0                    AS paid_up,
                                         treasury_id,
                                         date_insert,
                                         user_id,
                                         'suppliers_accounts' AS table_name,
                                         0                    AS profit
                                  FROM suppliers_accounts),
     treasury_deposit_query AS (SELECT id,
                                       date_inter  AS invoice_date,
                                       amount      AS total,
                                       0           AS discount,
                                       0           AS paid_up,
                                       treasury_id,
                                       date_insert,
                                       user_id,
                                       'deposit'   AS table_name,
                                       0           AS profit
                                FROM treasury_deposit_expenses
                                WHERE deposit_or_expenses = 1),
     treasury_expenses_query AS (SELECT id,
                                        date_inter         AS invoice_date,
                                        amount             AS total,
                                        0                  AS discount,
                                        0                  AS paid_up,
                                        treasury_id,
                                        date_insert,
                                        user_id,
                                        'deposit_expenses' AS table_name,
                                        0                  AS profit
                                 FROM treasury_deposit_expenses
                                 WHERE deposit_or_expenses = 2)
SELECT * FROM sales_query
UNION ALL
SELECT * FROM buy_query
UNION ALL
SELECT * FROM sales_return_query
UNION ALL
SELECT * FROM buy_return_query
UNION ALL
SELECT * FROM expenses_query
UNION ALL
SELECT * FROM customer_account_query
UNION ALL
SELECT * FROM suppliers_accounts_query
UNION ALL
SELECT * FROM treasury_deposit_query
UNION ALL
SELECT * FROM treasury_expenses_query;

CREATE OR REPLACE VIEW daily_dashboard_report AS
SELECT
    -- ==========================================
    -- 1. المبيعات (اليوم، أمس، الأسبوع، الشهر)
    -- ==========================================
    (SELECT COUNT(invoice_number) FROM total_sales WHERE invoice_date = CURDATE()) AS sales_count_today,
    COALESCE((SELECT SUM(total) FROM total_sales WHERE invoice_date = CURDATE()), 0) AS sales_total_today,
    COALESCE((SELECT SUM(total) FROM total_sales WHERE invoice_date = CURDATE() - INTERVAL 1 DAY), 0) AS sales_total_yesterday,
    COALESCE((SELECT SUM(total) FROM total_sales WHERE YEARWEEK(invoice_date, 1) = YEARWEEK(CURDATE(), 1)), 0) AS sales_total_week,
    COALESCE((SELECT SUM(total) FROM total_sales WHERE YEAR(invoice_date) = YEAR(CURDATE()) AND MONTH(invoice_date) = MONTH(CURDATE())), 0) AS sales_total_month,

    -- ==========================================
    -- 2. المشتريات (اليوم)
    -- ==========================================
    (SELECT COUNT(invoice_number) FROM total_buy WHERE invoice_date = CURDATE()) AS purchases_count_today,
    COALESCE((SELECT SUM(total) FROM total_buy WHERE invoice_date = CURDATE()), 0) AS purchases_total_today,

    -- ==========================================
    -- 3. مرتجعات المبيعات (اليوم)
    -- ==========================================
    (SELECT COUNT(id) FROM total_sales_re WHERE invoice_date = CURDATE()) AS sales_returns_count_today,
    COALESCE((SELECT SUM(total) FROM total_sales_re WHERE invoice_date = CURDATE()), 0) AS sales_returns_total_today,

    -- ==========================================
    -- 4. مرتجعات المشتريات (اليوم)
    -- ==========================================
    (SELECT COUNT(id) FROM total_buy_re WHERE invoice_date = CURDATE()) AS purchases_returns_count_today,
    COALESCE((SELECT SUM(total) FROM total_buy_re WHERE invoice_date = CURDATE()), 0) AS purchases_returns_total_today,

    -- ==========================================
    -- 5. المقبوضات (النقدية الداخلة للخزينة اليوم)
    -- ==========================================
    (
        COALESCE((SELECT SUM(paid_up) FROM total_sales WHERE invoice_date = CURDATE()), 0) +
        COALESCE((SELECT SUM(paid_to_treasury) FROM total_buy_re WHERE invoice_date = CURDATE()), 0) +
        COALESCE((SELECT SUM(amount) FROM treasury_deposit_expenses WHERE date_inter = CURDATE() AND deposit_or_expenses = 1), 0)
        ) AS total_receipts_today,

    -- ==========================================
    -- 6. المدفوعات والمصروفات (النقدية الخارجة اليوم)
    -- ==========================================
    (
        COALESCE((SELECT SUM(paid_up) FROM total_buy WHERE invoice_date = CURDATE()), 0) +
        COALESCE((SELECT SUM(paid_from_treasury) FROM total_sales_re WHERE invoice_date = CURDATE()), 0) +
        COALESCE((SELECT SUM(amount) FROM treasury_deposit_expenses WHERE date_inter = CURDATE() AND deposit_or_expenses = 2), 0) +
        COALESCE((SELECT SUM(amount) FROM expenses_details WHERE date = CURDATE()), 0)
        ) AS total_payments_and_expenses_today,

    -- ==========================================
    -- 7. الخصومات (إجمالي خصومات اليوم الممنوحة والمكتسبة)
    -- ==========================================
    (
        COALESCE((SELECT SUM(discount) FROM total_sales WHERE invoice_date = CURDATE()), 0) +
        COALESCE((SELECT SUM(discount) FROM total_buy WHERE invoice_date = CURDATE()), 0) +
        COALESCE((SELECT SUM(discount) FROM total_sales_re WHERE invoice_date = CURDATE()), 0) +
        COALESCE((SELECT SUM(discount) FROM total_buy_re WHERE invoice_date = CURDATE()), 0)
        ) AS total_discounts_today
;


CREATE OR REPLACE VIEW top_selling_items_current_month AS
SELECT
    i.nameItem AS item_name,
    SUM(s.quantity) AS total_quantity,
    CAST((SUM(s.total_sel_price) / SUM(s.quantity)) AS DECIMAL(14,2)) AS average_price
FROM sales s
         JOIN total_sales ts ON s.invoice_number = ts.invoice_number
         JOIN items i ON s.num = i.id
WHERE YEAR(ts.invoice_date) = YEAR(CURDATE())
  AND MONTH(ts.invoice_date) = MONTH(CURDATE())
GROUP BY i.id, i.nameItem
ORDER BY total_quantity DESC
LIMIT 10;

CREATE OR REPLACE VIEW view_monthly_sales AS
SELECT
    YEAR(invoice_date) AS sales_year,

    SUM(CASE WHEN MONTH(invoice_date) = 1 THEN total ELSE 0 END) AS January,
    SUM(CASE WHEN MONTH(invoice_date) = 2 THEN total ELSE 0 END) AS February,
    SUM(CASE WHEN MONTH(invoice_date) = 3 THEN total ELSE 0 END) AS March,
    SUM(CASE WHEN MONTH(invoice_date) = 4 THEN total ELSE 0 END) AS April,
    SUM(CASE WHEN MONTH(invoice_date) = 5 THEN total ELSE 0 END) AS May,
    SUM(CASE WHEN MONTH(invoice_date) = 6 THEN total ELSE 0 END) AS June,
    SUM(CASE WHEN MONTH(invoice_date) = 7 THEN total ELSE 0 END) AS July,
    SUM(CASE WHEN MONTH(invoice_date) = 8 THEN total ELSE 0 END) AS August,
    SUM(CASE WHEN MONTH(invoice_date) = 9 THEN total ELSE 0 END) AS September,
    SUM(CASE WHEN MONTH(invoice_date) = 10 THEN total ELSE 0 END) AS October,
    SUM(CASE WHEN MONTH(invoice_date) = 11 THEN total ELSE 0 END) AS November,
    SUM(CASE WHEN MONTH(invoice_date) = 12 THEN total ELSE 0 END) AS December,

    -- إجمالي مبيعات السنة بالكامل
    SUM(total) AS total_yearly_sales

FROM
    total_sales
GROUP BY
    YEAR(invoice_date);



CREATE OR REPLACE VIEW view_monthly_purchase AS
SELECT
    YEAR(invoice_date) AS sales_year,

    SUM(CASE WHEN MONTH(invoice_date) = 1 THEN total ELSE 0 END) AS January,
    SUM(CASE WHEN MONTH(invoice_date) = 2 THEN total ELSE 0 END) AS February,
    SUM(CASE WHEN MONTH(invoice_date) = 3 THEN total ELSE 0 END) AS March,
    SUM(CASE WHEN MONTH(invoice_date) = 4 THEN total ELSE 0 END) AS April,
    SUM(CASE WHEN MONTH(invoice_date) = 5 THEN total ELSE 0 END) AS May,
    SUM(CASE WHEN MONTH(invoice_date) = 6 THEN total ELSE 0 END) AS June,
    SUM(CASE WHEN MONTH(invoice_date) = 7 THEN total ELSE 0 END) AS July,
    SUM(CASE WHEN MONTH(invoice_date) = 8 THEN total ELSE 0 END) AS August,
    SUM(CASE WHEN MONTH(invoice_date) = 9 THEN total ELSE 0 END) AS September,
    SUM(CASE WHEN MONTH(invoice_date) = 10 THEN total ELSE 0 END) AS October,
    SUM(CASE WHEN MONTH(invoice_date) = 11 THEN total ELSE 0 END) AS November,
    SUM(CASE WHEN MONTH(invoice_date) = 12 THEN total ELSE 0 END) AS December,

    -- إجمالي مبيعات السنة بالكامل
    SUM(total) AS total_yearly_sales

FROM
    total_buy
GROUP BY
    YEAR(invoice_date);


CREATE OR REPLACE VIEW view_customer_purchased_items AS
SELECT
    c.id AS customer_id,
    c.name AS customer_name,
    i.nameItem AS item_name,
    s.quantity,
    s.price AS selling_price,
    ts.invoice_date,
    ts.invoice_number
FROM custom c
         JOIN total_sales ts ON c.id = ts.sup_code
         JOIN sales s ON ts.invoice_number = s.invoice_number
         JOIN items i ON s.num = i.id;

CREATE OR REPLACE VIEW view_suppliers_sales_items AS
SELECT
    c.id AS customer_id,
    c.name AS customer_name,
    i.nameItem AS item_name,
    s.quantity,
    s.price AS selling_price,
    ts.invoice_date,
    ts.invoice_number
FROM suppliers c
         JOIN total_buy ts ON c.id = ts.sup_code
         JOIN purchase s ON ts.invoice_number = s.invoice_number
         JOIN items i ON s.num = i.id;


CREATE OR REPLACE VIEW view_yearly_monthly_report AS
SELECT
    t.action_year AS report_year,
    t.action_month AS report_month,

    ROUND(SUM(t.purchases), 2) AS purchases,
    ROUND(SUM(t.purchases_discount), 2) AS purchases_discount,

    ROUND(SUM(t.sales), 2) AS sales,
    ROUND(SUM(t.sales_discount), 2) AS sales_discount,

    ROUND(SUM(t.purchases_return), 2) AS purchases_return,
    ROUND(SUM(t.purchases_return_discount), 2) AS purchases_return_discount,

    ROUND(SUM(t.sales_return), 2) AS sales_return,
    ROUND(SUM(t.sales_return_discount), 2) AS sales_return_discount,

    ROUND(SUM(t.expenses), 2) AS expenses,

    -- Net Profit Calculation: (Sales - Sales_RE - Sales_Discount) - (Purchases - Purchases_RE - Purchases_Discount) - Expenses
    ROUND(
            (SUM(t.sales) - SUM(t.sales_return) - SUM(t.sales_discount)) -
            (SUM(t.purchases) - SUM(t.purchases_return) - SUM(t.purchases_discount)) -
            SUM(t.expenses),
            2) AS estimated_net_profit

FROM (
         -- 1. Sales
         SELECT
             YEAR(invoice_date) AS action_year, MONTH(invoice_date) AS action_month,
             0 AS purchases, 0 AS purchases_discount,
             total AS sales, discount AS sales_discount,
             0 AS purchases_return, 0 AS purchases_return_discount,
             0 AS sales_return, 0 AS sales_return_discount,
             0 AS expenses
         FROM total_sales

         UNION ALL

         -- 2. Sales Returns
         SELECT
             YEAR(invoice_date), MONTH(invoice_date),
             0, 0,
             0, 0,
             0, 0,
             total, discount,
             0
         FROM total_sales_re

         UNION ALL

         -- 3. Purchases
         SELECT
             YEAR(invoice_date), MONTH(invoice_date),
             total, discount,
             0, 0,
             0, 0,
             0, 0,
             0
         FROM total_buy

         UNION ALL

         -- 4. Purchases Returns
         SELECT
             YEAR(invoice_date), MONTH(invoice_date),
             0, 0,
             0, 0,
             total, discount,
             0, 0,
             0
         FROM total_buy_re

         UNION ALL

         -- 5. Expenses
         SELECT
             YEAR(date_insert), MONTH(date_insert),
             0, 0,
             0, 0,
             0, 0,
             0, 0,
             amount
         FROM treasury_transfers
     ) AS t

GROUP BY
    t.action_year,
    t.action_month
ORDER BY
    t.action_year DESC,
    t.action_month ASC;


CREATE OR REPLACE VIEW view_item_sales_rank AS
SELECT
    num AS item_id,
    nameItem AS item_name,
    YEAR(invoice_date) AS sales_year,
    MONTH(invoice_date) AS sales_month,
    SUM(quantity) AS total_qty,
    ROUND(SUM(total_sales), 2) AS total_amount,
    -- حساب صافي الربح من الصنف (المبيعات - التكلفة)
    ROUND(SUM(total_sales - (quantity * buy_price)), 2) AS total_profit
FROM sales_names_table
GROUP BY num, nameItem, YEAR(invoice_date), MONTH(invoice_date);


CREATE OR REPLACE VIEW view_customer_receivables AS
SELECT
    c.id AS customer_id,
    c.name AS customer_name,
    c.tel AS customer_phone,

    -- رصيد أول المدة (المديونية عند تسجيل العميل)
    ROUND(c.first_balance, 2) AS opening_balance,

    -- إجمالي المبالغ الآجلة (المتبقية) من فواتير المبيعات
    ROUND((SELECT IFNULL(SUM((ts.total - ts.discount) - ts.paid_up), 0)
           FROM total_sales ts
           WHERE ts.sup_code = c.id), 2) AS total_invoices_debt,

    -- إجمالي التحصيلات والمدفوعات من جدول حسابات العملاء
    ROUND((SELECT IFNULL(SUM(paid), 0)
           FROM customers_accounts ca
           WHERE ca.account_code = c.id), 2) AS total_payments,

    -- صافي المديونية النهائية
    ROUND((c.first_balance +
           (SELECT IFNULL(SUM((ts.total - ts.discount) - ts.paid_up), 0) FROM total_sales ts WHERE ts.sup_code = c.id) -
           (SELECT IFNULL(SUM(paid), 0) FROM customers_accounts ca WHERE ca.account_code = c.id)
              ), 2) AS final_balance

FROM custom c
-- إظهار العملاء الذين لديهم تعاملات مادية فقط (اختياري)
WHERE (c.first_balance <> 0 OR
       EXISTS (SELECT 1 FROM total_sales WHERE sup_code = c.id) OR
       EXISTS (SELECT 1 FROM customers_accounts WHERE account_code = c.id));