-- --------------------------------------purchase_names_table---------------------------------------

CREATE OR REPLACE VIEW purchase_names_table AS
Select p.id,
       p.invoice_number,
       p.num,
       p.type,
       p.type_value,
       p.quantity as quantity,
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

from purchase p
         join items i on i.id = p.num
         join units u on p.type = u.unit_id
         join total_buy tb on tb.invoice_number = p.invoice_number
         join suppliers t on t.id = tb.sup_code;

-- --------------------------------------sales_and_sales_package------------------------------------
-- todo : add sales_package table

CREATE OR REPLACE VIEW sales_with_sales_package AS
with sales_query AS (select id,
                            invoice_number,
                            num,
                            type,
                            quantity,
                            price,
                            buy_price,
                            total_sel_price,
                            total_buy_price,
                            total_profit,
                            discount,
                            type_value,
                            expiration_date,
                            item_has_package
                     from sales),
     sales_package_qurey AS (select 0 as id,
                                    0,
                                    item_id,
                                    unit_id,
                                    quantity,
                                    price,
                                    buy_price,
                                    total_sel_price,
                                    total_buy_price,
                                    total_profit,
                                    discount,
                                    unit_value,
                                    expiration_date,
                                    1 as item_has_package

                             from sales_package)
select *
from sales_query
union all
select *
from sales_package_qurey;

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
         JOIN items i ON i.id = s.num
         JOIN units u ON s.type = u.unit_id
         JOIN total_sales ts ON ts.invoice_number = s.invoice_number
         JOIN custom c ON c.id = ts.sup_code;

# ---------------------------------------sales_return_names_table-----------------------------------

CREATE OR REPLACE VIEW sales_return_names_table AS
Select sr.id,
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
from sales_re sr
         join items i on i.id = sr.item_id
         join units u on sr.type = u.unit_id
         join total_sales_re tsr ON tsr.id = sr.invoice_number;

# ---------------------------------------purchase_return_names_table--------------------------------

CREATE OR REPLACE VIEW purchase_return_names_table AS
Select pr.id,
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
from purchase_re pr
         join items i on i.id = pr.item_id
         join units u on pr.type = u.unit_id
         join total_buy_re tbr ON tbr.id = pr.invoice_number;

# ---------------------------------------stock_transfer_view----------------------------------------

CREATE OR REPLACE VIEW stock_transfer_view AS
SELECT st.id,
       st.transfer_date,
       st.stock_from,
       st.stock_to,
       stf.stock_name as name_from,
       stt.stock_name as name_to,
       stl.item_id,
       stl.quantity,
       i.nameItem
FROM stock_transfer st
         join stock_transfer_list stl on st.id = stl.stock_transfer_id
         join items i on i.id = stl.item_id
         join stocks stf on stf.stock_id = st.stock_from
         join stocks stt on stt.stock_id = st.stock_to;

# ---------------------------------------quantity_items_table---------------------------------------

CREATE OR REPLACE VIEW quantity_items_table AS
SELECT items_stock.item_id,
       items_stock.stock_id,
       items_stock.first_balance,
       (SELECT COALESCE(sum(p.quantity * p.type_value), 0)
        from purchase_names_table p
        where p.stock_id = items_stock.stock_id
          and p.num = items_stock.item_id)        as quantityPurchase,
       (SELECT COALESCE(sum(sa.quantity * sa.type_value), 0)
        from sales_names_table sa
        where sa.stock_id = items_stock.stock_id
          and sa.num = items_stock.item_id)       as quantitySales,
       (SELECT COALESCE(sum(prnt.quantity * prnt.type_value), 0)
        from purchase_return_names_table prnt
        where prnt.stock_id = items_stock.stock_id
          and prnt.item_id = items_stock.item_id) as quantityPurchaseRe,
       (SELECT COALESCE(sum(srnt.quantity * srnt.type_value), 0)
        from sales_return_names_table srnt
        where srnt.stock_id = items_stock.stock_id
          and srnt.item_id = items_stock.item_id) as quantitySalesRe,
       (SELECT COALESCE(sum(quantity), 0)
        from stock_transfer_view st
        where st.stock_from = items_stock.stock_id
          and items_stock.item_id = st.item_id)   as fromStock,
       (SELECT COALESCE(sum(quantity), 0)
        from stock_transfer_view st
        where st.stock_to = items_stock.stock_id
          and items_stock.item_id = st.item_id)   as toStock
from items_stock;

# ---------------------------------------total_sales_names_table------------------------------------

CREATE OR REPLACE VIEW total_sales_names_table AS
WITH TotalPaidAmounts AS (SELECT numberInv AS InvoiceNumber,
                                 SUM(paid) AS TotalPaid
                          FROM customers_accounts
                          WHERE numberInv > 0
                          GROUP BY numberInv),
     sales_invoice_profit AS (SELECT invoice_number,
                                     SUM(total_profit)    AS total_profit,
                                     SUM(total_buy_price) AS total_buy_price
                              from sales
                              group by invoice_number)
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
       round(sip.total_profit, 2)                      AS total_profit,
       sip.total_buy_price,
       round(((sip.total_profit * 100) / ts.total), 2) AS profit_percent,
       COALESCE(tpa.TotalPaid, 0)                      AS OtherPaid
FROM total_sales ts
         JOIN custom c ON c.id = ts.sup_code
         JOIN stocks s ON s.stock_id = ts.stock_id
         JOIN employees e ON ts.delegate_id = e.id
         JOIN treasury t ON ts.treasury_id = t.id
         LEFT JOIN sales_invoice_profit sip On ts.invoice_number = sip.invoice_number
         LEFT JOIN TotalPaidAmounts tpa ON ts.invoice_number = tpa.InvoiceNumber;

# ---------------------------------------total_purchase_names_table---------------------------------

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
         JOIN suppliers c on c.id = tb.sup_code
         JOIN stocks s on s.stock_id = tb.stock_id
         JOIN treasury t on tb.treasury_id = t.id
         LEFT JOIN PaidAmounts pa ON tb.invoice_number = pa.InvoiceNumber;

# ---------------------------------------total_purchase_return_names_table--------------------------

CREATE OR REPLACE VIEW total_purchase_return_names_table AS
SELECT total_buy_re.id,
       sup_id,
       invoice_date,
       total,
       discount,
       paid_to_treasury,
       total_buy_re.stock_id,
       treasury_id,
       total_buy_re.notes,
       total_buy_re.invoice_type,
       total_buy_re.date_insert,
       name,
       stock_name,
       t_name,
       total_buy_re.user_id
from total_buy_re
         join suppliers c on c.id = total_buy_re.sup_id
         join stocks s on s.stock_id = total_buy_re.stock_id
         join treasury t on total_buy_re.treasury_id = t.id;

# ---------------------------------------total_sales_return_names_table-----------------------------

CREATE OR REPLACE VIEW total_sales_return_names_table AS
WITH sales_invoice_profit AS (SELECT invoice_number,
                                     SUM(total_profit)    AS total_profit,
                                     SUM(total_buy_price) AS total_buy_price
                              from sales_re
                              group by invoice_number)
SELECT total_sales_re.id,
       sup_id,
       invoice_date,
       total,
       discount,
       paid_from_treasury,
       total_sales_re.stock_id,
       total_sales_re.delegate_id,
       total_sales_re.treasury_id,
       total_sales_re.notes,
       total_sales_re.invoice_type,
       total_sales_re.date_insert,
       name,
       stock_name,
       t_name,
       e.column_name,
       total_sales_re.user_id,
       round(sip.total_profit, 2)                                  AS total_profit,
       sip.total_buy_price,
       round(((sip.total_profit * 100) / total_sales_re.total), 2) AS profit_percent
from total_sales_re
         join custom c on c.id = total_sales_re.sup_id
         join stocks s on s.stock_id = total_sales_re.stock_id
         join treasury t on total_sales_re.treasury_id = t.id
         join employees e on e.id = total_sales_re.delegate_id
         LEFT JOIN sales_invoice_profit sip On total_sales_re.id = sip.invoice_number;

# ---------------------------------------account_customer_table-------------------------------------

CREATE OR REPLACE VIEW account_customer_table AS
select 0                                     as account_num,
       c.id                                  as account_code,
       Date_Format(c.created_at, '%Y-%m-%d') as account_date,
       c.first_balance                       as purchase,
       0                                     as discount,
       0                                     as paid,
       'رصيد اول'                            as notes,
       1                                     as information,
       0                                     as type,
       c.created_at                          as created_at,
       0                                     as treasury_id,
       0                                     as numberInv
from custom c
union
select account_num  as account_num,
       account_code as account_code,
       account_date as account_date,
       purchase     as purchase,
       0            as discount,
       paid         as paid,
       notes        as notes,
       2            as information,
       0            as type,
       created_at,
       treasury_id  as treasury_id,
       numberInv    as numberInv
from customers_accounts
union
select invoice_number
     , sup_code
     , invoice_date
     , total
     , discount
     , paid_up
     , notes
     , 3
     , invoice_type
     , date_insert
     , treasury_id
     , 0
from total_sales
union
# إ ذا كانت الفاتورة نقدا يتم خصم كل المدفوع,
# واذا كانت اجل مبلغ من الخزينة ومبلغ من الحساب

select total_sales_re.id,
       sup_id,
       total_sales_re.invoice_date,
       IF(invoice_type = 1, total, 0),#total,
       IF(invoice_type = 1, discount, 0), #discount,
       total_sales_re.paid_from_treasury,
       total_sales_re.notes,
       4,
       invoice_type,
       total_sales_re.date_insert,
       total_sales_re.treasury_id,
       0
from total_sales_re
order by created_at;

# ---------------------------------------account_suppliers_table------------------------------------

CREATE OR REPLACE VIEW account_suppliers_table AS
select 0                                      as account_num,
       c.id                                   as account_code,
       Date_Format(c.date_insert, '%Y-%m-%d') as account_date,
       c.first_balance                        as purchase,
       0                                      as discount,
       0                                      as paid,
       'رصيد اول'                             as notes,
       1                                      as information,
       0                                      as type,
       c.date_insert                          as date_insert,
       0                                      as treasury_id,
       0                                      as numberInv
from suppliers c
union
select account_num  as account_num,
       account_code as account_code,
       account_date as account_date,
       purchase     as purchase,
       0            as discount,
       paid         as paid,
       notes        as notes,
       2            as information,
       0            as type,
       date_insert  as date_insert,
       treasury_id  as treasury_id,
       numberInv    as numberInv
from suppliers_accounts
union
select invoice_number
     , sup_code
     , invoice_date
     , total
     , discount
     , paid_up
     , total_buy.notes
     , 3
     , invoice_type
     , date_insert
     , treasury_id
     , 0
from total_buy
union
select total_buy_re.id,
       sup_id,
       total_buy_re.invoice_date,
       IF(invoice_type = 1, total_buy_re.total, 0),
       IF(invoice_type = 1, total_buy_re.discount, 0),
       total_buy_re.paid_to_treasury,
       total_buy_re.notes,
       4,
       invoice_type,
       total_buy_re.date_insert,
       total_buy_re.treasury_id,
       0
from total_buy_re
order by date_insert;

# ---------------------------------------card_item_view---------------------------------------------

CREATE OR REPLACE VIEW card_item_view AS
WITH sales_data AS (SELECT s.id,
                           s.invoice_number,
                           t.invoice_date,
                           s.num       AS item_num,
                           s.type      AS unit_type,
                           s.quantity  AS quantity, # update this
                           s.price,
                           s.buy_price AS buy_price,
                           s.discount,
                           c.name      AS name_custom,
                           t.date_insert,
                           t.delegate_id,
                           'sales'     AS table_name,
                           s.expiration_date
                    FROM sales s
                             JOIN total_sales t ON t.invoice_number = s.invoice_number
                             JOIN custom c ON t.sup_code = c.id),
     sales_return_data AS (SELECT sre.id,
                                  sre.invoice_number,
                                  t.invoice_date,
                                  sre.item_id   AS item_num,
                                  sre.type      AS unit_type,
                                  sre.quantity,
                                  sre.price,
                                  sre.buy_price AS buy_price,
                                  0             AS discount,
                                  c.name        AS name_custom,
                                  t.date_insert,
                                  t.delegate_id,
                                  'sales_re'    AS table_name,
                                  sre.expiration_date
                           FROM sales_re sre
                                    JOIN total_sales_re t ON t.id = sre.invoice_number
                                    JOIN custom c ON t.sup_id = c.id),
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
                                JOIN total_buy t ON t.invoice_number = p.invoice_number
                                JOIN suppliers s ON t.sup_code = s.id),
     purchase_return_data AS (SELECT pre.id,
                                     pre.invoice_number,
                                     t.invoice_date,
                                     pre.item_id   AS item_num,
                                     pre.type      AS unit_type,
                                     pre.quantity,
                                     pre.price,
                                     0             AS buy_price,
                                     0             AS discount,
                                     s.name        AS name_custom,
                                     t.date_insert,
                                     0             AS delegate_id,
                                     'purchase_re' AS table_name,
                                     pre.expiration_date
                              FROM purchase_re pre
                                       JOIN total_buy_re t ON t.id = pre.invoice_number
                                       JOIN suppliers s ON t.sup_id = s.id)


SELECT *
FROM sales_data
UNION
SELECT *
FROM sales_return_data
UNION
SELECT *
FROM purchase_data
UNION
SELECT *
FROM purchase_return_data;

# ---------------------------------------card_item_view_details-------------------------------------

CREATE OR REPLACE VIEW card_item_view_details AS
select c.id,
       c.invoice_number,
       c.invoice_date,
       c.item_num,
       c.unit_type,
       c.quantity,
       c.price,
       c.buy_price,
       if(c.table_name = 'purchase' || c.table_name = 'purchase_re', 0,
          ((c.price - c.buy_price) * c.quantity)) AS profit,
       c.discount,
       c.name_custom,
       c.date_insert,
       c.delegate_id,
       em.column_name                             AS delegate_name,
       c.table_name,
       i.barcode,
       i.nameItem,
       un.unit_name,
       c.expiration_date
from card_item_view c
         join items i on c.item_num = i.id
         join units un on c.unit_type = un.unit_id
         left join employees em on c.delegate_id = em.id;

# ---------------------------------------expenses_details_view--------------------------------------

CREATE OR REPLACE VIEW expenses_details_view AS
SELECT expenses_details.id,
       type_code,
       date,
       amount,
       notes,
       treasury_id,
       emp_id,
       expenses_name,
       IFNULL(column_name, '') as column_name
from expenses_details
         join expenses e on e.id = expenses_details.type_code
         left join expense_salary es on expenses_details.id = es.expenses_details_id
         left join employees e2 on e2.id = es.employee_id;

# ---------------------------------------mini_quantity_view-----------------------------------------

CREATE OR REPLACE VIEW mini_quantity_view AS
WITH calculated_balance AS (SELECT item_id,
                                   SUM((first_balance + quantityPurchase + quantitySalesRe + toStock) -
                                       (quantitySales + quantityPurchaseRe + fromStock)) AS balance
                            FROM quantity_items_table
                            GROUP BY item_id)
SELECT id,
       nameItem,
       mini_quantity,
       cb.balance
FROM items
         JOIN calculated_balance cb ON items.id = cb.item_id
WHERE mini_quantity >= cb.balance;

# ---------------------------------------target_delegate--------------------------------------------

CREATE OR REPLACE VIEW target_delegate AS
WITH total_sales_sums AS (SELECT total_sales.delegate_id,
                                 YEAR(invoice_date)                                           AS sales_year,
                                 MONTH(invoice_date)                                          AS sales_month,
                                 SUM(total)                                                   AS total_sales_sum,
                                 (SELECT SUM(total)
                                  FROM total_sales_re
                                  WHERE YEAR(total_sales_re.invoice_date) = YEAR(total_sales.invoice_date)
                                    AND MONTH(total_sales_re.invoice_date) = MONTH(total_sales.invoice_date)
                                    AND total_sales_re.delegate_id = total_sales.delegate_id) AS total_sales_re_sum
                          FROM total_sales
                          GROUP BY total_sales.delegate_id, sales_month, sales_year, total_sales_re_sum),
     sales_data AS (SELECT tss.delegate_id,
                           tss.sales_year,
                           tss.sales_month,
                           COALESCE(tss.total_sales_sum, 0)                                         as total_sales_sum,
                           COALESCE(tss.total_sales_re_sum, 0)                                      as total_sales_re_sum,
                           (COALESCE(tss.total_sales_sum, 0) - COALESCE(tss.total_sales_re_sum, 0)) AS sales_difference
                    FROM total_sales_sums tss)
SELECT e.id                                                           AS employee_id,
       e.column_name                                                  AS employee_name,
       sales_summary.total_sales_sum,
       sales_summary.total_sales_re_sum,
       sales_summary.sales_difference                                 as Amount,
       tgt.target_ratio1,
       tgt.rate_1,
       tgt.target_ratio2,
       tgt.rate_2,
       tgt.target_ratio3,
       tgt.rate_3,
       tgt.target,
       sales_summary.sales_year,
       sales_summary.sales_month,
       IF(sales_summary.sales_difference >= (tgt.target * tgt.target_ratio1) / 100,
          (sales_summary.sales_difference * tgt.rate_1) / 100,
          IF(sales_summary.sales_difference >= (tgt.target * tgt.target_ratio2) / 100,
             (sales_summary.sales_difference * tgt.rate_2) / 100
              , (sales_summary.sales_difference * tgt.rate_3) / 100)) AS commission
FROM employees e
         JOIN sales_data sales_summary ON e.id = sales_summary.delegate_id
         JOIN targeted_sales tgt ON e.id = tgt.delegate_id
ORDER BY e.id;

# ---------------------------------------treasury_balance-------------------------------------------

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
                        SELECT id                                                                          AS id_no,
                               invoice_date                                                                AS date_val,
                               IF(invoice_type = 1, paid_to_treasury, total - discount - paid_to_treasury) AS income,
                               0                                                                           AS output,
                               treasury_id,
                               date_insert,
                               user_id,
                               'مرتجع المشتريات'                                                           AS information
                        FROM total_buy_re
                        UNION ALL
                        SELECT invoice_number AS id_no,
                               invoice_date   AS date_val,
                               paid_up        AS income,
                               0              AS output,
                               treasury_id,
                               date_insert,
                               user_id,
                               'المبيعات'     AS information
                        FROM total_sales
                        UNION ALL
                        SELECT id                                                           AS id_no,
                               invoice_date                                                 AS date_val,
                               0                                                            AS income,
                               # إذا كانت اجل يتم الخصم من الخزينة البلغ الباقى
                               IF(invoice_type = 1, paid_from_treasury, total - discount -
                                                                        paid_from_treasury) AS output,
                               treasury_id,
                               date_insert,
                               user_id,
                               'مرتجع المبيعات'                                             AS information
                        FROM total_sales_re
                        UNION ALL
                        SELECT account_num      AS id_no,
                               account_date     AS date_val,
                               paid             AS income,
                               0                AS output,
                               treasury_id,
                               created_at,
                               user_id,
                               'حسابات العملاء' AS information
                        FROM customers_accounts
                        UNION ALL
                        SELECT account_num       AS id_no,
                               account_date      AS date_val,
                               0                 AS income,
                               paid              AS output,
                               treasury_id,
                               date_insert,
                               user_id,
                               'حسابات الموردين' AS information
                        FROM suppliers_accounts
                        UNION ALL
                        SELECT id          AS id_no,
                               date        AS date_val,
                               0           AS income,
                               amount      AS output,
                               treasury_id,
                               date_insert,
                               user_id,
                               'المصروفات' AS information
                        FROM expenses_details
                        UNION ALL
                        SELECT id                                          AS id_no,
                               date_inter                                  AS date_val,
                               IF(deposit_or_expenses = 1, amount, 0)      AS income,
                               IF(deposit_or_expenses = 2, amount, 0)      AS output,
                               treasury_id,
                               date_insert,
                               user_id,
                               IF(deposit_or_expenses = 1, 'إيداع', 'صرف') AS information
                        FROM treasury_deposit_expenses)
SELECT id_no,
       date_val,
       income,
       output,
       c.treasury_id,
       c.date_insert,
       c.user_id,
       information,
       t.t_name    as treasury_name,
       u.user_name as user_name
FROM cte_union_data c
         join treasury t on t.id = c.treasury_id
         join users u on u.id = c.user_id
ORDER BY date_val;

# ---------------------------------------treasury_transfers_and_names-------------------------------

CREATE OR REPLACE VIEW treasury_transfers_and_names AS
SELECT treasury_transfers.id
     , treasury_transfers.treasury_from
     , treasury_transfers.treasury_to
     , treasury_transfers.amount
     , treasury_transfers.transfer_date
     , treasury_transfers.notes
     , tFrom.t_name AS treasury_name_from
     , tTo.t_name   AS treasury_name_to
From treasury_transfers
         JOIN treasury tFrom on tFrom.id = treasury_transfers.treasury_from
         JOIN treasury tTo on tTo.id = treasury_transfers.treasury_to;
# -------------------------------------------------------------------------------------
CREATE OR REPLACE VIEW treasury_balance_after_convert AS
With sum_treasury_amount_from AS (SELECT treasury_from, COALESCE(SUM(amount), 0) AS sum_transfer_from
                                  FROM treasury_transfers
                                  GROUP BY treasury_from)
   , sum_treasury_amount_to AS (SELECT treasury_to, COALESCE(SUM(amount), 0) AS sum_transfer_to
                                FROM treasury_transfers
                                GROUP BY treasury_to)
SELECT *,
       ((treasury.amount + COALESCE(t.sum_transfer_to, 0)) - COALESCE(f.sum_transfer_from, 0)) As amount_after_transfer
From treasury
         LEFT JOIN sum_treasury_amount_from f ON f.treasury_from = treasury.id
         LEFT JOIN sum_treasury_amount_to t ON t.treasury_to = treasury.id;

# ---------------------------------------account_customer_totals------------------------------------

CREATE OR REPLACE VIEW account_customer_totals AS
SELECT account_code,
       c.name,
       SUM(purchase)                                       AS purchase,
       SUM(discount)                                       AS discount,
       SUM(paid)                                           AS paid,
       round(sum(purchase) - sum(discount) - sum(paid), 2) AS amount,
       MAX(account_date)                                   AS account_date,
       ta.id                                               AS area_id,
       ta.area_name                                        AS area_name
FROM account_customer_table act
         JOIN custom c ON act.account_code = c.id
         Join account_system_db.table_area ta on ta.id = c.area_id
GROUP BY account_code, name;

# ---------------------------------------account_suppliers_totals-----------------------------------

CREATE OR REPLACE VIEW account_suppliers_totals AS
select account_code,
       name,
       round(sum(purchase), 2)                             as purchase,
       round(sum(discount), 2)                             as discount,
       round(sum(paid), 2)                                 as paid,
       round(sum(purchase) - sum(discount) - sum(paid), 2) as amount,
       max(account_date)                                   as account_date
from account_suppliers_table ast
         join suppliers c on ast.account_code = c.id
group by account_code, name;

# ---------------------------------------earnings_reports-------------------------------------------

CREATE OR REPLACE VIEW earnings_reports AS
WITH computed_profit AS (SELECT ts.invoice_number,
#                                 ROUND(SUM(snt.total_sales) - SUM(snt.total_buy), 2) AS profit
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
                            ts.invoice_date   AS invoice_date,
                            ts.total          As total,
                            ts.discount       AS discount,
                            ts.paid_up        AS paid_up,
                            ts.treasury_id    AS treasury_id,
                            ts.date_insert    AS date_insert,
                            ts.user_id        AS user_id,
                            'sales'           AS table_name,
                            cp.profit         AS profit
                     FROM total_sales ts
                              JOIN computed_profit cp ON cp.invoice_number = ts.invoice_number),
     buy_query AS (SELECT invoice_number AS id,
                          invoice_date   AS invoice_date,
                          total          As total,
                          discount       AS discount,
                          paid_up        AS paid_up,
                          treasury_id    AS treasury_id,
                          date_insert    AS date_insert,
                          user_id        AS user_id,
                          'buy'          AS table_name,
                          0              AS profit
                   FROM total_buy),
     sales_return_query AS (SELECT tsr.id                 AS id,
                                   tsr.invoice_date       AS invoice_date,
                                   tsr.total              As total,
                                   tsr.discount           AS discount,
                                   tsr.paid_from_treasury AS paid_up,
                                   tsr.treasury_id        AS treasury_id,
                                   tsr.date_insert        AS date_insert,
                                   tsr.user_id            AS user_id,
                                   'sales_re'             AS table_name,
                                   cpsr.profit            AS profit
                            FROM total_sales_re tsr
                                     JOIN computed_profit_sales_return cpsr ON cpsr.id = tsr.id),
     buy_return_query AS (SELECT id               AS id,
                                 invoice_date     AS invoice_date,
                                 total            As total,
                                 discount         AS discount,
                                 paid_to_treasury AS paid_up,
                                 treasury_id      AS treasury_id,
                                 date_insert      AS date_insert,
                                 user_id          AS user_id,
                                 'buy_re'         AS table_name,
                                 0                AS profit
                          FROM total_buy_re),
     expenses_query AS (SELECT id          AS id,
                               date        AS invoice_date,
                               amount      As total,
                               0           AS discount,
                               0           AS paid_up,
                               treasury_id AS treasury_id,
                               date_insert AS date_insert,
                               user_id     AS user_id,
                               'expenses'  AS table_name,
                               0           AS profit
                        FROM expenses_details),
     customer_account_query AS (SELECT account_num          AS id,
                                       account_date         AS invoice_date,
                                       paid                 As total,
                                       0                    AS discount,
                                       0                    AS paid_up,
                                       treasury_id          AS treasury_id,
                                       created_at           AS date_insert,
                                       user_id              AS user_id,
                                       'customers_accounts' AS table_name,
                                       0                    AS profit
                                FROM customers_accounts),
     suppliers_accounts_query AS (SELECT account_num          AS id,
                                         account_date         AS invoice_date,
                                         paid                 As total,
                                         0                    AS discount,
                                         0                    AS paid_up,
                                         treasury_id          AS treasury_id,
                                         date_insert          AS date_insert,
                                         user_id              AS user_id,
                                         'suppliers_accounts' AS table_name,
                                         0                    AS profit
                                  FROM suppliers_accounts),
     treasury_deposit_query AS (SELECT id          AS id,
                                       date_inter  AS invoice_date,
                                       amount      AS total,
                                       0           AS discount,
                                       0           AS paid_up,
                                       treasury_id AS treasury_id,
                                       date_insert AS date_insert,
                                       user_id     AS user_id,
                                       'deposit'   AS table_name,
                                       0           AS profit
                                FROM treasury_deposit_expenses
                                where deposit_or_expenses = 1),
     treasury_expenses_query AS (SELECT id                 AS id,
                                        date_inter         AS invoice_date,
                                        amount             AS total,
                                        0                  AS discount,
                                        0                  AS paid_up,
                                        treasury_id        AS treasury_id,
                                        date_insert        AS date_insert,
                                        user_id            AS user_id,
                                        'deposit_expenses' AS table_name,
                                        0                  AS profit
                                 FROM treasury_deposit_expenses
                                 where deposit_or_expenses = 2)

SELECT *
FROM sales_query
UNION
SELECT *
FROM buy_query
UNION
SELECT *
FROM sales_return_query
UNION
SELECT *
FROM buy_return_query
UNION
SELECT *
FROM expenses_query
UNION
SELECT *
FROM customer_account_query
UNION
SELECT *
FROM suppliers_accounts_query
UNION
SELECT *
FROM treasury_deposit_query
UNION
SELECT *
FROM treasury_expenses_query;

