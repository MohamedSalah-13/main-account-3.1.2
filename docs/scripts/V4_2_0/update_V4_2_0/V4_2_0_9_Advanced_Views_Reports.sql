
-- =====================================================================
-- V4_2_0_5: Views تقارير إضافية ومتقدمة
-- =====================================================================

USE account_system_db;

-- =====================================================================
-- 1) Views تقارير المخزون المتقدمة
-- =====================================================================

-- ---------------------------------------------------------------------
-- A) تقرير الأصناف الراكدة (Slow Moving Items)
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_slow_moving_items AS
WITH item_movements AS (
    SELECT 
        sm.item_id,
        sm.stock_id,
        MAX(sm.movement_date) AS last_movement_date,
        DATEDIFF(CURDATE(), MAX(sm.movement_date)) AS days_since_last_movement,
        SUM(CASE WHEN sm.movement_type = 'SALE' THEN sm.quantity_out ELSE 0 END) AS total_sales_qty
    FROM stock_movements sm
    WHERE sm.movement_date >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
    GROUP BY sm.item_id, sm.stock_id
)
SELECT 
    i.id AS item_id,
    i.barcode,
    i.nameItem,
    s.stock_id,
    s.stock_name,
    ist.current_quantity,
    im.last_movement_date,
    im.days_since_last_movement,
    COALESCE(im.total_sales_qty, 0) AS sales_last_6_months,
    i.buy_price,
    i.sel_price1,
    ROUND(ist.current_quantity * i.buy_price, 2) AS stock_value,
    sg.name AS sub_group_name,
    mg.name_g AS main_group_name,
    CASE 
        WHEN im.days_since_last_movement > 180 THEN 'راكد جداً'
        WHEN im.days_since_last_movement > 90 THEN 'راكد'
        WHEN im.days_since_last_movement > 60 THEN 'بطيء الحركة'
        ELSE 'طبيعي'
    END AS movement_status
FROM items i
JOIN items_stock ist ON ist.item_id = i.id
JOIN stocks s ON s.stock_id = ist.stock_id
JOIN sub_group sg ON sg.id = i.sub_num
JOIN main_group mg ON mg.id = sg.main_id
LEFT JOIN item_movements im ON im.item_id = i.id AND im.stock_id = ist.stock_id
WHERE ist.current_quantity > 0
AND (im.days_since_last_movement > 60 OR im.days_since_last_movement IS NULL)
ORDER BY im.days_since_last_movement DESC;

-- ---------------------------------------------------------------------
-- B) تقرير أصناف قاربت على النفاذ
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_low_stock_items AS
SELECT 
    i.id AS item_id,
    i.barcode,
    i.nameItem,
    i.mini_quantity AS minimum_qty,
    s.stock_id,
    s.stock_name,
    ist.current_quantity,
    (i.mini_quantity - ist.current_quantity) AS shortage_qty,
    i.buy_price,
    i.sel_price1,
    ROUND((i.mini_quantity - ist.current_quantity) * i.buy_price, 2) AS reorder_cost,
    sg.name AS sub_group_name,
    mg.name_g AS main_group_name,
    u.unit_name,
    CASE 
        WHEN ist.current_quantity = 0 THEN 'نفذ'
        WHEN ist.current_quantity <= i.mini_quantity * 0.25 THEN 'حرج جداً'
        WHEN ist.current_quantity <= i.mini_quantity * 0.5 THEN 'حرج'
        ELSE 'قليل'
    END AS stock_status
FROM items i
JOIN items_stock ist ON ist.item_id = i.id
JOIN stocks s ON s.stock_id = ist.stock_id
JOIN sub_group sg ON sg.id = i.sub_num
JOIN main_group mg ON mg.id = sg.main_id
JOIN units u ON u.unit_id = i.unit_id
WHERE ist.current_quantity <= i.mini_quantity
AND i.item_active = 1
ORDER BY stock_status, (i.mini_quantity - ist.current_quantity) DESC;

-- ---------------------------------------------------------------------
-- C) تقرير أصناف قاربت صلاحيتها
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_expiring_items AS
SELECT 
    i.id AS item_id,
    i.barcode,
    i.nameItem,
    s.stock_id,
    s.stock_name,
    -- من جدول المبيعات (نحتفظ بتاريخ الصلاحية)
    p.expiration_date,
    DATEDIFF(p.expiration_date, CURDATE()) AS days_to_expiry,
    SUM(p.quantity * p.type_value) AS quantity_to_expire,
    i.buy_price,
    ROUND(SUM(p.quantity * p.type_value) * i.buy_price, 2) AS potential_loss,
    i.alert_days_before_expire,
    CASE 
        WHEN DATEDIFF(p.expiration_date, CURDATE()) < 0 THEN 'منتهي الصلاحية'
        WHEN DATEDIFF(p.expiration_date, CURDATE()) <= 7 THEN 'خطر - أقل من أسبوع'
        WHEN DATEDIFF(p.expiration_date, CURDATE()) <= 30 THEN 'تحذير - أقل من شهر'
        ELSE 'طبيعي'
    END AS expiry_status
FROM purchase p
JOIN total_buy tb ON tb.invoice_number = p.invoice_number
JOIN items i ON i.id = p.num
JOIN stocks s ON s.stock_id = tb.stock_id
WHERE i.item_has_validity = 1
AND p.expiration_date IS NOT NULL
AND DATEDIFF(p.expiration_date, CURDATE()) <= i.alert_days_before_expire
GROUP BY i.id, i.barcode, i.nameItem, s.stock_id, s.stock_name, 
         p.expiration_date, i.buy_price, i.alert_days_before_expire
ORDER BY days_to_expiry ASC;

-- ---------------------------------------------------------------------
-- D) تقرير حركة المخزون التفصيلي
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_stock_movement_detailed AS
SELECT 
    sm.id,
    sm.movement_date,
    sm.movement_type,
    CASE sm.movement_type
        WHEN 'OPENING' THEN 'رصيد افتتاحي'
        WHEN 'PURCHASE' THEN 'مشتريات'
        WHEN 'PURCHASE_RETURN' THEN 'مرتجع مشتريات'
        WHEN 'SALE' THEN 'مبيعات'
        WHEN 'SALE_RETURN' THEN 'مرتجع مبيعات'
        WHEN 'TRANSFER_IN' THEN 'تحويل وارد'
        WHEN 'TRANSFER_OUT' THEN 'تحويل صادر'
        WHEN 'INVENTORY_ADJUST_IN' THEN 'تسوية جرد - إضافة'
        WHEN 'INVENTORY_ADJUST_OUT' THEN 'تسوية جرد - خصم'
        ELSE sm.movement_type
    END AS movement_type_ar,
    sm.item_id,
    i.barcode,
    i.nameItem,
    sm.stock_id,
    s.stock_name,
    sm.quantity_in,
    sm.quantity_out,
    (sm.quantity_in - sm.quantity_out) AS net_quantity,
    sm.unit_id,
    u.unit_name,
    sm.unit_value,
    sm.reference_type,
    sm.reference_id,
    sm.reference_line_id,
    sm.notes,
    sm.user_id,
    us.user_name,
    -- الرصيد بعد الحركة
    (SELECT SUM(sm2.quantity_in - sm2.quantity_out)
     FROM stock_movements sm2
     WHERE sm2.item_id = sm.item_id 
     AND sm2.stock_id = sm.stock_id
     AND sm2.id <= sm.id) AS balance_after_movement
FROM stock_movements sm
JOIN items i ON i.id = sm.item_id
JOIN stocks s ON s.stock_id = sm.stock_id
LEFT JOIN units u ON u.unit_id = sm.unit_id
JOIN users us ON us.id = sm.user_id;

-- ---------------------------------------------------------------------
-- E) تقرير أكثر الأصناف مبيعاً
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_top_selling_items AS
SELECT 
    i.id AS item_id,
    i.barcode,
    i.nameItem,
    sg.name AS sub_group_name,
    mg.name_g AS main_group_name,
    -- المبيعات الشهر الحالي
    SUM(CASE 
        WHEN MONTH(ts.invoice_date) = MONTH(CURDATE()) 
        AND YEAR(ts.invoice_date) = YEAR(CURDATE())
        THEN s.quantity * s.type_value 
        ELSE 0 
    END) AS current_month_qty,
    -- المبيعات 3 أشهر الأخيرة
    SUM(CASE 
        WHEN ts.invoice_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
        THEN s.quantity * s.type_value 
        ELSE 0 
    END) AS last_3_months_qty,
    -- المبيعات السنة الحالية
    SUM(CASE 
        WHEN YEAR(ts.invoice_date) = YEAR(CURDATE())
        THEN s.quantity * s.type_value 
        ELSE 0 
    END) AS current_year_qty,
    -- إجمالي المبيعات
    SUM(s.quantity * s.type_value) AS total_qty_sold,
    -- إجمالي الربح
    SUM(s.total_profit) AS total_profit,
    -- متوسط سعر البيع
    AVG(s.price) AS avg_selling_price,
    i.buy_price,
    i.sel_price1,
    -- هامش الربح
    ROUND(AVG((s.price - s.buy_price) / NULLIF(s.price, 0) * 100), 2) AS avg_profit_margin,
    COUNT(DISTINCT ts.invoice_number) AS invoice_count
FROM sales s
JOIN total_sales ts ON ts.invoice_number = s.invoice_number
JOIN items i ON i.id = s.num
JOIN sub_group sg ON sg.id = i.sub_num
JOIN main_group mg ON mg.id = sg.main_id
GROUP BY i.id, i.barcode, i.nameItem, sg.name, mg.name_g, i.buy_price, i.sel_price1
ORDER BY last_3_months_qty DESC;

-- =====================================================================
-- 2) Views تقارير الخزينة والمالية
-- =====================================================================

-- ---------------------------------------------------------------------
-- A) تقرير حركة الخزائن التفصيلي
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_treasury_movement_detailed AS
SELECT 
    tm.id,
    tm.treasury_id,
    t.t_name AS treasury_name,
    tm.movement_date,
    tm.movement_type,
    CASE tm.movement_type
        WHEN 'OPENING' THEN 'رصيد افتتاحي'
        WHEN 'DEPOSIT' THEN 'إيداع'
        WHEN 'WITHDRAWAL' THEN 'سحب'
        WHEN 'TRANSFER_IN' THEN 'تحويل وارد'
        WHEN 'TRANSFER_OUT' THEN 'تحويل صادر'
        WHEN 'SALE' THEN 'مبيعات'
        WHEN 'SALE_RETURN' THEN 'مرتجع مبيعات'
        WHEN 'PURCHASE' THEN 'مشتريات'
        WHEN 'PURCHASE_RETURN' THEN 'مرتجع مشتريات'
        WHEN 'EXPENSE' THEN 'مصروفات'
        ELSE tm.movement_type
    END AS movement_type_ar,
    tm.amount_in,
    tm.amount_out,
    tm.balance_after,
    tm.reference_type,
    tm.reference_id,
    tm.notes,
    tm.user_id,
    u.user_name,
    tm.date_insert,
    -- تفاصيل إضافية حسب نوع المرجع
    CASE tm.reference_type
        WHEN 'SALE' THEN (SELECT c.name FROM total_sales ts JOIN custom c ON c.id = ts.sup_code WHERE ts.invoice_number = tm.reference_id)
        WHEN 'PURCHASE' THEN (SELECT sp.name FROM total_buy tb JOIN suppliers sp ON sp.id = tb.sup_code WHERE tb.invoice_number = tm.reference_id)
        ELSE NULL
    END AS related_party_name
FROM treasury_movements tm
JOIN treasury t ON t.id = tm.treasury_id
JOIN users u ON u.id = tm.user_id;

-- ---------------------------------------------------------------------
-- B) تقرير ملخص الخزائن اليومي
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_treasury_daily_summary AS
SELECT 
    tm.treasury_id,
    t.t_name AS treasury_name,
    DATE(tm.movement_date) AS summary_date,
    SUM(tm.amount_in) AS total_in,
    SUM(tm.amount_out) AS total_out,
    (SUM(tm.amount_in) - SUM(tm.amount_out)) AS net_movement,
    -- التفصيل حسب النوع
    SUM(CASE WHEN tm.movement_type = 'SALE' THEN tm.amount_in ELSE 0 END) AS sales_cash,
    SUM(CASE WHEN tm.movement_type = 'PURCHASE' THEN tm.amount_out ELSE 0 END) AS purchases_cash,
    SUM(CASE WHEN tm.movement_type = 'EXPENSE' THEN tm.amount_out ELSE 0 END) AS expenses,
    SUM(CASE WHEN tm.movement_type = 'DEPOSIT' THEN tm.amount_in ELSE 0 END) AS deposits,
    SUM(CASE WHEN tm.movement_type = 'WITHDRAWAL' THEN tm.amount_out ELSE 0 END) AS withdrawals,
    COUNT(*) AS transactions_count,
    MAX(tm.balance_after) AS closing_balance
FROM treasury_movements tm
JOIN treasury t ON t.id = tm.treasury_id
GROUP BY tm.treasury_id, t.t_name, DATE(tm.movement_date)
ORDER BY summary_date DESC, tm.treasury_id;

-- ---------------------------------------------------------------------
-- C) تقرير المقارنة بين الخزائن
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_treasury_comparison AS
SELECT 
    t.id AS treasury_id,
    t.t_name AS treasury_name,
    t.amount AS current_balance,
    -- حركات اليوم
    COALESCE((SELECT SUM(amount_in) FROM treasury_movements 
              WHERE treasury_id = t.id AND DATE(movement_date) = CURDATE()), 0) AS today_in,
    COALESCE((SELECT SUM(amount_out) FROM treasury_movements 
              WHERE treasury_id = t.id AND DATE(movement_date) = CURDATE()), 0) AS today_out,
    -- حركات الشهر
    COALESCE((SELECT SUM(amount_in) FROM treasury_movements 
              WHERE treasury_id = t.id 
              AND MONTH(movement_date) = MONTH(CURDATE())
              AND YEAR(movement_date) = YEAR(CURDATE())), 0) AS month_in,
    COALESCE((SELECT SUM(amount_out) FROM treasury_movements 
              WHERE treasury_id = t.id 
              AND MONTH(movement_date) = MONTH(CURDATE())
              AND YEAR(movement_date) = YEAR(CURDATE())), 0) AS month_out,
    -- عدد المعاملات
    COALESCE((SELECT COUNT(*) FROM treasury_movements 
              WHERE treasury_id = t.id AND DATE(movement_date) = CURDATE()), 0) AS today_transactions,
    -- الورديات المرتبطة
    COALESCE((SELECT COUNT(*) FROM user_shifts 
              WHERE treasury_id = t.id AND is_open = TRUE), 0) AS open_shifts
FROM treasury t;

-- =====================================================================
-- 3) Views تقارير الأرباح والخسائر
-- =====================================================================

-- ---------------------------------------------------------------------
-- A) تقرير الأرباح الشامل
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_comprehensive_profit_report AS
WITH sales_summary AS (
    SELECT 
        DATE_FORMAT(ts.invoice_date, '%Y-%m') AS period,
        YEAR(ts.invoice_date) AS year,
        MONTH(ts.invoice_date) AS month,
        SUM(ts.total) AS total_sales,
        SUM(ts.discount) AS sales_discount,
        SUM(s.total_buy_price) AS cost_of_sales,
        SUM(s.total_profit) AS gross_profit
    FROM total_sales ts
    JOIN sales s ON s.invoice_number = ts.invoice_number
    GROUP BY DATE_FORMAT(ts.invoice_date, '%Y-%m'), YEAR(ts.invoice_date), MONTH(ts.invoice_date)
),
sales_returns_summary AS (
    SELECT 
        DATE_FORMAT(tsr.invoice_date, '%Y-%m') AS period,
        SUM(tsr.total) AS total_returns,
        SUM(tsr.discount) AS returns_discount,
        SUM(sr.total_buy_price) AS cost_of_returns,
        SUM(sr.total_profit) AS returns_profit
    FROM total_sales_re tsr
    JOIN sales_re sr ON sr.invoice_number = tsr.id
    GROUP BY DATE_FORMAT(tsr.invoice_date, '%Y-%m')
),
expenses_summary AS (
    SELECT 
        DATE_FORMAT(ed.date, '%Y-%m') AS period,
        SUM(ed.amount) AS total_expenses
    FROM expenses_details ed
    GROUP BY DATE_FORMAT(ed.date, '%Y-%m')
)
SELECT 
    ss.period,
    ss.year,
    ss.month,
    ss.total_sales,
    ss.sales_discount,
    COALESCE(srs.total_returns, 0) AS total_returns,
    (ss.total_sales - ss.sales_discount - COALESCE(srs.total_returns, 0)) AS net_sales,
    ss.cost_of_sales,
    COALESCE(srs.cost_of_returns, 0) AS cost_of_returns,
    (ss.cost_of_sales - COALESCE(srs.cost_of_returns, 0)) AS net_cost,
    ss.gross_profit,
    COALESCE(srs.returns_profit, 0) AS returns_profit_loss,
    (ss.gross_profit - COALESCE(srs.returns_profit, 0)) AS adjusted_gross_profit,
    COALESCE(es.total_expenses, 0) AS total_expenses,
    (ss.gross_profit - COALESCE(srs.returns_profit, 0) - COALESCE(es.total_expenses, 0)) AS net_profit,
    ROUND((ss.gross_profit - COALESCE(srs.returns_profit, 0) - COALESCE(es.total_expenses, 0)) / 
          NULLIF(ss.total_sales - ss.sales_discount - COALESCE(srs.total_returns, 0), 0) * 100, 2) AS net_profit_margin
FROM sales_summary ss
LEFT JOIN sales_returns_summary srs ON srs.period = ss.period
LEFT JOIN expenses_summary es ON es.period = ss.period
ORDER BY ss.period DESC;

-- ---------------------------------------------------------------------
-- B) تقرير الربحية حسب المجموعة
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_profit_by_group AS
SELECT 
    mg.id AS main_group_id,
    mg.name_g AS main_group_name,
    sg.id AS sub_group_id,
    sg.name AS sub_group_name,
    COUNT(DISTINCT i.id) AS items_count,
    SUM(s.quantity * s.type_value) AS total_qty_sold,
    SUM(s.total_sel_price) AS total_sales,
    SUM(s.discount) AS total_discount,
    SUM(s.total_buy_price) AS total_cost,
    SUM(s.total_profit) AS total_profit,
    ROUND(SUM(s.total_profit) / NULLIF(SUM(s.total_sel_price), 0) * 100, 2) AS profit_margin,
    COUNT(DISTINCT s.invoice_number) AS invoice_count
FROM sales s
JOIN items i ON i.id = s.num
JOIN sub_group sg ON sg.id = i.sub_num
JOIN main_group mg ON mg.id = sg.main_id
GROUP BY mg.id, mg.name_g, sg.id, sg.name
ORDER BY total_profit DESC;

-- ---------------------------------------------------------------------
-- C) تقرير المبيعات حسب العملاء (أفضل العملاء)
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_top_customers AS
SELECT 
    c.id AS customer_id,
    c.name AS customer_name,
    c.tel AS phone,
    c.address,
    ta.area_name,
    COUNT(DISTINCT ts.invoice_number) AS invoice_count,
    SUM(ts.total) AS total_sales,
    SUM(ts.discount) AS total_discount,
    SUM(ts.paid_up) AS total_cash,
    SUM(ts.total - ts.discount - ts.paid_up) AS total_credit,
    -- المرتجعات
    COALESCE((SELECT SUM(tsr.total) 
              FROM total_sales_re tsr 
              WHERE tsr.sup_id = c.id), 0) AS total_returns,
    -- الأرباح
    COALESCE((SELECT SUM(s.total_profit) 
              FROM sales s 
              JOIN total_sales ts2 ON ts2.invoice_number = s.invoice_number 
              WHERE ts2.sup_code = c.id), 0) AS total_profit,
    -- آخر فاتورة
    MAX(ts.invoice_date) AS last_invoice_date,
    DATEDIFF(CURDATE(), MAX(ts.invoice_date)) AS days_since_last_purchase
FROM custom c
LEFT JOIN total_sales ts ON ts.sup_code = c.id
LEFT JOIN table_area ta ON ta.id = c.area_id
GROUP BY c.id, c.name, c.tel, c.address, ta.area_name
HAVING invoice_count > 0
ORDER BY total_sales DESC;

-- =====================================================================
-- 4) Views تقارير الورديات
-- =====================================================================

-- ---------------------------------------------------------------------
-- A) مقارنة أداء المستخدمين في الورديات
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_user_shifts_performance AS
SELECT 
    u.id AS user_id,
    u.user_name,
    COUNT(DISTINCT us.id) AS total_shifts,
    SUM(CASE WHEN us.is_open = FALSE THEN 1 ELSE 0 END) AS closed_shifts,
    SUM(CASE WHEN us.is_open = TRUE THEN 1 ELSE 0 END) AS open_shifts,
    -- الأرصدة
    AVG(us.open_balance) AS avg_open_balance,
    AVG(CASE WHEN us.is_open = FALSE THEN us.close_balance ELSE NULL END) AS avg_close_balance,
    -- المبيعات
    SUM(us.total_sales) AS total_sales,
    SUM(us.total_sales_returns) AS total_returns,
    SUM(us.total_sales - us.total_sales_returns) AS net_sales,
    AVG(us.total_sales) AS avg_sales_per_shift,
    -- المصروفات
    SUM(us.total_expenses) AS total_expenses,
    -- الفواتير
    SUM(us.invoices_count) AS total_invoices,
    AVG(us.invoices_count) AS avg_invoices_per_shift,
    -- الفروقات
    SUM(us.difference) AS total_difference,
    SUM(CASE WHEN us.difference > 0 THEN us.difference ELSE 0 END) AS total_surplus,
    SUM(CASE WHEN us.difference < 0 THEN ABS(us.difference) ELSE 0 END) AS total_shortage,
    -- معدل الدقة
    ROUND((1 - SUM(ABS(us.difference)) / NULLIF(SUM(us.expected_balance), 0)) * 100, 2) AS accuracy_rate
FROM users u
LEFT JOIN user_shifts us ON us.user_id = u.id
WHERE u.user_activity = 1
GROUP BY u.id, u.user_name
HAVING total_shifts > 0
ORDER BY net_sales DESC;

-- ---------------------------------------------------------------------
-- B) تقرير الورديات حسب الخزينة
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_shifts_by_treasury AS
SELECT 
    t.id AS treasury_id,
    t.t_name AS treasury_name,
    DATE(us.open_time) AS shift_date,
    COUNT(DISTINCT us.id) AS shifts_count,
    SUM(us.open_balance) AS total_open_balance,
    SUM(CASE WHEN us.is_open = FALSE THEN us.close_balance ELSE 0 END) AS total_close_balance,
    SUM(us.total_sales) AS total_sales,
    SUM(us.total_expenses) AS total_expenses,
    SUM(us.expected_balance) AS total_expected,
    SUM(us.difference) AS total_difference,
    COUNT(DISTINCT us.user_id) AS users_count
FROM treasury t
LEFT JOIN user_shifts us ON us.treasury_id = t.id
GROUP BY t.id, t.t_name, DATE(us.open_time)
ORDER BY shift_date DESC, t.id;

-- =====================================================================
-- 5) Views تقارير إدارية
-- =====================================================================

-- ---------------------------------------------------------------------
-- A) لوحة التحكم الرئيسية (Dashboard)
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_dashboard_summary AS
SELECT 
    -- معلومات اليوم
    (SELECT SUM(total - discount) FROM total_sales WHERE DATE(invoice_date) = CURDATE()) AS today_sales,
    (SELECT SUM(total_profit) FROM sales s JOIN total_sales ts ON ts.invoice_number = s.invoice_number 
     WHERE DATE(ts.invoice_date) = CURDATE()) AS today_profit,
    (SELECT SUM(amount) FROM expenses_details WHERE DATE(date) = CURDATE()) AS today_expenses,
    (SELECT COUNT(*) FROM total_sales WHERE DATE(invoice_date) = CURDATE()) AS today_invoices,
    -- معلومات الشهر
    (SELECT SUM(total - discount) FROM total_sales 
     WHERE MONTH(invoice_date) = MONTH(CURDATE()) AND YEAR(invoice_date) = YEAR(CURDATE())) AS month_sales,
    (SELECT SUM(total_profit) FROM sales s JOIN total_sales ts ON ts.invoice_number = s.invoice_number 
     WHERE MONTH(ts.invoice_date) = MONTH(CURDATE()) AND YEAR(ts.invoice_date) = YEAR(CURDATE())) AS month_profit,
    -- المخزون
    (SELECT COUNT(*) FROM items WHERE item_active = 1) AS total_active_items,
    (SELECT COUNT(*) FROM v_low_stock_items) AS low_stock_items,
    (SELECT SUM(current_quantity * buy_price) FROM items_stock ist JOIN items i ON i.id = ist.item_id) AS total_stock_value,
    -- الخزائن
    (SELECT SUM(amount) FROM treasury) AS total_treasury_balance,
    (SELECT COUNT(*) FROM user_shifts WHERE is_open = TRUE) AS open_shifts,
    -- العملاء والموردين
    (SELECT COUNT(*) FROM custom) AS total_customers,
    (SELECT COUNT(*) FROM suppliers) AS total_suppliers,
    -- الديون
    (SELECT SUM(purchase - discount - paid) FROM account_customer_totals WHERE amount > 0) AS total_customer_debt,
    (SELECT SUM(purchase - discount - paid) FROM account_suppliers_totals WHERE amount > 0) AS total_supplier_debt;

-- ---------------------------------------------------------------------
-- B) تقرير المؤشرات الرئيسية (KPIs)
-- ---------------------------------------------------------------------

CREATE OR REPLACE VIEW v_kpi_metrics AS
WITH monthly_data AS (
    SELECT 
        DATE_FORMAT(CURDATE(), '%Y-%m') AS current_month,
        DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m') AS previous_month
)
SELECT 
    -- المبيعات
    'المبيعات' AS metric_name,
    (SELECT SUM(total) FROM total_sales 
     WHERE DATE_FORMAT(invoice_date, '%Y-%m') = (SELECT current_month FROM monthly_data)) AS current_value,
    (SELECT SUM(total) FROM total_sales 
     WHERE DATE_FORMAT(invoice_date, '%Y-%m') = (SELECT previous_month FROM monthly_data)) AS previous_value,
    ROUND(((SELECT SUM(total) FROM total_sales 
            WHERE DATE_FORMAT(invoice_date, '%Y-%m') = (SELECT current_month FROM monthly_data)) - 
           (SELECT SUM(total) FROM total_sales 
            WHERE DATE_FORMAT(invoice_date, '%Y-%m') = (SELECT previous_month FROM monthly_data))) / 
          NULLIF((SELECT SUM(total) FROM total_sales 
                  WHERE DATE_FORMAT(invoice_date, '%Y-%m') = (SELECT previous_month FROM monthly_data)), 0) * 100, 2) AS growth_percentage

UNION ALL

SELECT 
    'الأرباح',
    (SELECT SUM(total_profit) FROM sales s JOIN total_sales ts ON ts.invoice_number = s.invoice_number 
     WHERE DATE_FORMAT(ts.invoice_date, '%Y-%m') = (SELECT current_month FROM monthly_data)),
    (SELECT SUM(total_profit) FROM sales s JOIN total_sales ts ON ts.invoice_number = s.invoice_number 
     WHERE DATE_FORMAT(ts.invoice_date, '%Y-%m') = (SELECT previous_month FROM monthly_data)),
    ROUND(((SELECT SUM(total_profit) FROM sales s JOIN total_sales ts ON ts.invoice_number = s.invoice_number 
            WHERE DATE_FORMAT(ts.invoice_date, '%Y-%m') = (SELECT current_month FROM monthly_data)) - 
           (SELECT SUM(total_profit) FROM sales s JOIN total_sales ts ON ts.invoice_number = s.invoice_number 
            WHERE DATE_FORMAT(ts.invoice_date, '%Y-%m') = (SELECT previous_month FROM monthly_data))) / 
          NULLIF((SELECT SUM(total_profit) FROM sales s JOIN total_sales ts ON ts.invoice_number = s.invoice_number 
                  WHERE DATE_FORMAT(ts.invoice_date, '%Y-%m') = (SELECT previous_month FROM monthly_data)), 0) * 100, 2)

UNION ALL

SELECT 
    'عدد الفواتير',
    (SELECT COUNT(*) FROM total_sales 
     WHERE DATE_FORMAT(invoice_date, '%Y-%m') = (SELECT current_month FROM monthly_data)),
    (SELECT COUNT(*) FROM total_sales 
     WHERE DATE_FORMAT(invoice_date, '%Y-%m') = (SELECT previous_month FROM monthly_data)),
    ROUND(((SELECT COUNT(*) FROM total_sales 
            WHERE DATE_FORMAT(invoice_date, '%Y-%m') = (SELECT current_month FROM monthly_data)) - 
           (SELECT COUNT(*) FROM total_sales 
            WHERE DATE_FORMAT(invoice_date, '%Y-%m') = (SELECT previous_month FROM monthly_data))) / 
          NULLIF((SELECT COUNT(*) FROM total_sales 
                  WHERE DATE_FORMAT(invoice_date, '%Y-%m') = (SELECT previous_month FROM monthly_data)), 0) * 100, 2);

-- =====================================================================
-- تسجيل نسخة التحديث
-- =====================================================================

INSERT INTO database_migrations (version, description, executed_at)
VALUES ('4.2.0.5', 'Advanced Views and Reports', NOW());

SELECT 'تم إنشاء جميع Views التقارير الإضافية بنجاح!' AS status;
SELECT COUNT(*) AS views_count FROM information_schema.views 
WHERE table_schema = DATABASE();
