-- =================================== test======================
-- 1️⃣ قيمة المخزون في نهاية شهر معين
SET @as_of_date = '2025-01-31';

SELECT *
FROM v_historical_stock_value
ORDER BY stock_name, nameItem;

-- ### 2️⃣ إجمالي قيمة المخزون لكل مخزن في تاريخ معين

SET @as_of_date = '2025-06-30';

SELECT stock_id, stock_name,
       COUNT(DISTINCT item_id)  AS items_count,
       SUM(balance_as_of)       AS total_quantity,
       SUM(value_at_cost)       AS total_cost_value,
       SUM(value_at_sell)       AS total_sell_value,
       SUM(potential_profit)    AS potential_profit
FROM v_historical_stock_value
GROUP BY stock_id, stock_name;

-- ### 3️⃣ إجمالي قيمة المخزون الكلية في تاريخ معين

SET @as_of_date = '2025-12-31';

SELECT
    COUNT(DISTINCT item_id)  AS عدد_الأصناف,
    SUM(balance_as_of)       AS إجمالي_الكميات,
    SUM(value_at_cost)       AS القيمة_بسعر_التكلفة,
    SUM(value_at_sell)       AS القيمة_بسعر_البيع,
    SUM(potential_profit)    AS الربح_المحتمل
FROM v_historical_stock_value;

-- ### 4️⃣ مقارنة قيمة المخزون بين تاريخين
-- قيمة المخزون أول الفترة
SET @as_of_date = '2025-01-01';
CREATE TEMPORARY TABLE tmp_open AS
SELECT stock_id, stock_name,
       SUM(value_at_cost) AS open_value
FROM v_historical_stock_value
GROUP BY stock_id, stock_name;

-- قيمة المخزون آخر الفترة
SET @as_of_date = '2025-12-31';
CREATE TEMPORARY TABLE tmp_close AS
SELECT stock_id, stock_name,
       SUM(value_at_cost) AS close_value
FROM v_historical_stock_value
GROUP BY stock_id, stock_name;

-- المقارنة
SELECT COALESCE(o.stock_name, c.stock_name) AS stock_name,
       COALESCE(o.open_value,  0)            AS قيمة_أول_المدة,
       COALESCE(c.close_value, 0)            AS قيمة_آخر_المدة,
       (COALESCE(c.close_value, 0) - COALESCE(o.open_value, 0)) AS الفرق
FROM tmp_open  o
         LEFT JOIN tmp_close c ON c.stock_id = o.stock_id;

DROP TEMPORARY TABLE tmp_open;
DROP TEMPORARY TABLE tmp_close;

-- ### 5️⃣ قيمة مخزون صنف معين في تاريخ معين في كل المخازن
SET @as_of_date = '2025-06-30';

SELECT stock_name,
       balance_as_of,
       value_at_cost,
       value_at_sell
FROM v_historical_stock_value
WHERE item_id = 5;

-- ### 6️⃣ الأصناف الراكدة (لم تتحرك في فترة معينة)
SET @as_of_date = '2025-12-31';

SELECT h.item_id, h.nameItem, h.stock_name,
       h.balance_as_of,
       h.value_at_cost
FROM v_historical_stock_value h
WHERE NOT EXISTS (
    SELECT 1
    FROM v_item_movements m
    WHERE m.item_id   = h.item_id
      AND m.stock_id  = h.stock_id
      AND m.movement_type <> 'OPENING'
      AND m.movement_date BETWEEN '2025-01-01' AND '2025-12-31'
)
  AND h.balance_as_of > 0
ORDER BY h.value_at_cost DESC;

-- ### 7️⃣ تقرير حركة وقيمة الأصناف خلال فترة (Inventory Movement Report)
-- الرصيد أول الفترة
SET @as_of_date = '2024-12-31';
SELECT i.id, i.nameItem, s.stock_name,
       v.balance_as_of AS opening_qty,
       v.value_at_cost AS opening_value
FROM items i
         JOIN stocks s
         LEFT JOIN v_historical_stock_value v
                   ON v.item_id = i.id AND v.stock_id = s.stock_id
WHERE v.balance_as_of IS NOT NULL;

-- 🔧 طريقة استخدام بديلة بدون متغير الجلسة
-- إذا كنت تريد استدعاء التقرير من Java/JDBC ولا تريد استخدام SET @as_of_date، يمكنك استخدام Stored Procedure بدلاً من ذلك:

DELIMITER //

DROP PROCEDURE IF EXISTS sp_stock_value_at_date //

CREATE PROCEDURE sp_stock_value_at_date(IN p_date DATE)
BEGIN
    SET @as_of_date = p_date;
    SELECT *
    FROM v_historical_stock_value
    ORDER BY stock_name, nameItem;
END //

DROP PROCEDURE IF EXISTS sp_stock_value_summary_at_date //

CREATE PROCEDURE sp_stock_value_summary_at_date(IN p_date DATE)
BEGIN
    SET @as_of_date = p_date;
    SELECT
        COUNT(DISTINCT item_id)  AS items_count,
        SUM(balance_as_of)       AS total_quantity,
        SUM(value_at_cost)       AS total_cost_value,
        SUM(value_at_sell)       AS total_sell_value,
        SUM(potential_profit)    AS potential_profit
    FROM v_historical_stock_value;
END //

DELIMITER ;


# try (CallableStatement cs = connection.prepareCall("{call sp_stock_value_at_date(?)}")) {
#     cs.setDate(1, java.sql.Date.valueOf("2025-12-31"));
# try (ResultSet rs = cs.executeQuery()) {
#         while (rs.next()) {
#             // اقرأ النتائج
#         }
#     }
# }