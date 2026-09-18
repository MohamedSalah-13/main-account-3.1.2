-- The demo shop the user manual is photographed in.
--
-- Runs against account_manual_demo AFTER the application has migrated it, never against a
-- customer's or the developer's database: ManualDemoDatabase is what creates that schema and
-- points a throwaway config.xml at it.
--
-- Everything here is invented. The names are the kind an Egyptian grocery would use, and no
-- figure is taken from anybody's books - a screenshot goes into a document that is handed out,
-- and a real customer's name in it is a customer list published by accident.
--
-- What V1 already seeded and this file builds on, rather than duplicating:
--   users 1 = admin, type_price 1..3, table_area 1 = القاهرة, custom 1 = بيع نقدي,
--   suppliers 1 = مورد عام, units 1 = قطعة و2 = كرتونه, stocks 1 = الرئيسي,
--   main_group 1 = عام 1, sub_group 1 = فرع 1, employees 1 = بيع مباشر, treasury 1.
-- The ids below therefore start above those.

-- ---------------------------------------------------------------- the shop itself

INSERT INTO company (comp_id, comp_name, comp_tel, comp_address, comp_tax, comp_comm)
VALUES (1, 'مؤسسة النور للتجارة', '0223456789', 'شارع الجمهورية - القاهرة', '345-678-912', '12345')
ON DUPLICATE KEY UPDATE comp_name = VALUES(comp_name), comp_tel = VALUES(comp_tel),
                        comp_address = VALUES(comp_address), comp_tax = VALUES(comp_tax);

UPDATE treasury SET t_name = 'الخزينة الرئيسية', amount = 15000 WHERE id = 1;

INSERT INTO treasury (id, t_name, amount) VALUES
    (2, 'فودافون كاش', 3000),
    (3, 'البنك الأهلي', 50000)
ON DUPLICATE KEY UPDATE t_name = VALUES(t_name), amount = VALUES(amount);

INSERT INTO stocks (stock_id, stock_name, stock_address) VALUES
    (2, 'مخزن الفرع', 'المعادي')
ON DUPLICATE KEY UPDATE stock_name = VALUES(stock_name);

INSERT INTO table_area (id, area_name) VALUES
    (2, 'الجيزة'),
    (3, 'الإسكندرية')
ON DUPLICATE KEY UPDATE area_name = VALUES(area_name);

INSERT INTO units (unit_id, unit_name, value_d) VALUES
    (3, 'كيلو', 1),
    (4, 'لتر', 1),
    (5, 'دستة', 12)
ON DUPLICATE KEY UPDATE unit_name = VALUES(unit_name), value_d = VALUES(value_d);

-- ---------------------------------------------------------------- groups

INSERT INTO main_group (id, name_g) VALUES
    (2, 'مواد غذائية'),
    (3, 'منظفات'),
    (4, 'مشروبات')
ON DUPLICATE KEY UPDATE name_g = VALUES(name_g);

INSERT INTO sub_group (id, name, main_id) VALUES
    (2, 'زيوت وسمن', 2),
    (3, 'أرز ومكرونة', 2),
    (4, 'سكر وشاي', 2),
    (5, 'مساحيق غسيل', 3),
    (6, 'منظفات أسطح', 3),
    (7, 'مياه غازية', 4),
    (8, 'عصائر', 4)
ON DUPLICATE KEY UPDATE name = VALUES(name), main_id = VALUES(main_id);

-- ---------------------------------------------------------------- items

INSERT INTO items (id, barcode, nameItem, sub_num, buy_price, sel_price1, sel_price2, sel_price3,
                   unit_id, mini_quantity, first_balance) VALUES
    (1,  '6221031492015', 'زيت عافية ذرة 1 لتر',        2, 68.00,  78.00,  76.00,  74.00, 4, 12, 0),
    (2,  '6221031492022', 'زيت عافية دوار الشمس 1 لتر', 2, 65.00,  75.00,  73.00,  71.00, 4, 12, 0),
    (3,  '6223000512014', 'سمن بلدنا 800 جم',           2, 92.00, 105.00, 103.00, 100.00, 1, 6,  0),
    (4,  '6221048100118', 'أرز مصري فاخر 1 كجم',        3, 28.00,  34.00,  33.00,  32.00, 3, 20, 0),
    (5,  '6221048100125', 'مكرونة الملكة 400 جم',       3, 11.50,  15.00,  14.50,  14.00, 1, 24, 0),
    (6,  '6221048100132', 'شعرية الملكة 400 جم',        3, 11.00,  14.50,  14.00,  13.50, 1, 24, 0),
    (7,  '6223001800110', 'سكر أبيض 1 كجم',             4, 26.00,  31.00,  30.00,  29.00, 3, 30, 0),
    (8,  '6223001800127', 'شاي العروسة 250 جم',         4, 44.00,  52.00,  51.00,  50.00, 1, 12, 0),
    (9,  '6223001800134', 'نسكافيه كلاسيك 50 جم',       4, 78.00,  92.00,  90.00,  88.00, 1, 8,  0),
    (10, '6221155000113', 'برسيل مسحوق 2 كجم',          5, 135.00, 158.00, 155.00, 152.00, 1, 6, 0),
    (11, '6221155000120', 'أريال مسحوق 1 كجم',          5, 78.00,  92.00,  90.00,  88.00, 1, 8,  0),
    (12, '6221155000137', 'كلوركس 1 لتر',               6, 22.00,  28.00,  27.00,  26.00, 4, 12, 0),
    (13, '6221155000144', 'فيري سائل أطباق 600 مل',     6, 34.00,  42.00,  41.00,  40.00, 1, 12, 0),
    (14, '5449000000101', 'كوكاكولا 1 لتر',             7, 16.00,  20.00,  19.50,  19.00, 4, 24, 0),
    (15, '5449000000118', 'سبرايت 1 لتر',               7, 16.00,  20.00,  19.50,  19.00, 4, 24, 0),
    (16, '6223007700115', 'مياه بركة 1.5 لتر',          7, 4.50,   6.00,   5.75,   5.50,  4, 48, 0),
    (17, '6223007700122', 'عصير جهينة مانجو 1 لتر',     8, 26.00,  32.00,  31.00,  30.00, 4, 12, 0),
    (18, '6223007700139', 'عصير جهينة برتقال 1 لتر',    8, 26.00,  32.00,  31.00,  30.00, 4, 12, 0),
    (19, '6223007700146', 'لبن جهينة كامل الدسم 1 لتر', 8, 30.00,  36.00,  35.00,  34.00, 4, 12, 0),
    (20, '6223007700153', 'زبادي جهينة 105 جم',         8, 6.00,   8.00,   7.75,   7.50,  1, 36, 0)
ON DUPLICATE KEY UPDATE nameItem = VALUES(nameItem), buy_price = VALUES(buy_price),
                        sel_price1 = VALUES(sel_price1);

-- The opening balance lives per warehouse. A missing items_stock row is a silently dropped
-- balance: quantity_items_table is driven by this table, not by items.first_balance.
-- The SELECT is wrapped in a derived table on purpose: after a JOIN, MySQL reads the ON of
-- ON DUPLICATE KEY as the join's own ON and fails near "KEY UPDATE".
INSERT INTO items_stock (item_id, stock_id, first_balance, current_quantity)
SELECT * FROM (
    SELECT i.id AS item_id, s.stock_id AS stock_id,
           CASE WHEN s.stock_id = 1 THEN 120 ELSE 40 END AS first_balance,
           0 AS current_quantity
    FROM items i CROSS JOIN stocks s
) AS seed
ON DUPLICATE KEY UPDATE first_balance = VALUES(first_balance);

-- A second unit for two items, so the unit prices screen has something to show.
-- items_barcode is NOT NULL and globally unique, so a unit carries a code of its own.
INSERT INTO items_units (items_id, items_barcode, unit, quantity, buy_price, sel_price) VALUES
    (14, '5449000000101-12', 5, 12, 180.00, 228.00),
    (16, '6223007700115-12', 5, 12, 50.00,  66.00)
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity), sel_price = VALUES(sel_price);

-- ---------------------------------------------------------------- customers and suppliers

INSERT INTO custom (id, name, tel, address, limit_num, first_balance, price_id, area_id) VALUES
    (2, 'سوبر ماركت الأمانة',  '01001234567', 'شبرا',        20000, 3500.00, 2, 1),
    (3, 'بقالة الإخلاص',       '01012345678', 'فيصل',        10000, 0.00,    1, 2),
    (4, 'مطعم الشيف',          '01023456789', 'المهندسين',   15000, 1250.00, 2, 2),
    (5, 'كافيتيريا الركن',     '01034567890', 'وسط البلد',    5000, 0.00,    1, 1),
    (6, 'ماركت النخبة',        '01045678901', 'سموحة',       25000, 7800.00, 3, 3)
ON DUPLICATE KEY UPDATE name = VALUES(name), tel = VALUES(tel), limit_num = VALUES(limit_num),
                        first_balance = VALUES(first_balance);

INSERT INTO suppliers (id, name, tel, address, first_balance, area_id) VALUES
    (2, 'شركة النيل للتوزيع',     '0224445555', 'العبور',   4200.00, 1),
    (3, 'مصنع الوادي للزيوت',     '0233336666', 'السادس',   0.00,    2),
    (4, 'شركة المصرية للمشروبات', '0227778888', 'مدينة نصر', 9500.00, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), tel = VALUES(tel), first_balance = VALUES(first_balance);

-- ---------------------------------------------------------------- staff

INSERT INTO employees (id, column_name, birth_date, hire_date, salary, job) VALUES
    (2, 'محمود السيد',  '1992-04-11', '2023-01-15', 7000, 4),
    (3, 'كريم عبد الله', '1988-09-02', '2022-06-01', 9000, 4),
    (4, 'هالة فتحي',    '1995-12-20', '2024-03-10', 6500, 1)
ON DUPLICATE KEY UPDATE column_name = VALUES(column_name), salary = VALUES(salary);

-- ---------------------------------------------------------------- a fortnight of trading
-- Ten sales, some cash and some on account, so the totals screens, the statements, the profit
-- report and the treasury balance all have a shape to them rather than a row of zeros.

INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,
                         paid_up, stock_id, delegate_id, treasury_id) VALUES
    (1001, 1, 1, CURRENT_DATE - INTERVAL 13 DAY, 1284.00,  0.00, 1284.00, 1, 1, 1),
    (1002, 2, 2, CURRENT_DATE - INTERVAL 12 DAY, 3160.00, 60.00, 1000.00, 1, 2, 1),
    (1003, 3, 1, CURRENT_DATE - INTERVAL 11 DAY,  646.00,  0.00,  646.00, 1, 1, 1),
    (1004, 4, 2, CURRENT_DATE - INTERVAL  9 DAY, 2190.00,  0.00,    0.00, 1, 2, 1),
    (1005, 1, 1, CURRENT_DATE - INTERVAL  8 DAY,  486.00,  0.00,  486.00, 1, 1, 2),
    (1006, 6, 2, CURRENT_DATE - INTERVAL  6 DAY, 5280.00,180.00, 2000.00, 2, 3, 1),
    (1007, 5, 1, CURRENT_DATE - INTERVAL  5 DAY,  920.00,  0.00,  920.00, 1, 1, 1),
    (1008, 1, 1, CURRENT_DATE - INTERVAL  3 DAY,  742.00,  0.00,  742.00, 1, 1, 1),
    (1009, 2, 2, CURRENT_DATE - INTERVAL  2 DAY, 1896.00,  0.00,  500.00, 1, 2, 1),
    (1010, 1, 1, CURRENT_DATE,                    1130.00,  0.00, 1130.00, 1, 1, 1)
ON DUPLICATE KEY UPDATE total = VALUES(total), paid_up = VALUES(paid_up);

-- The line's own cost is what the profit is computed from, so each carries the item's buy price.
INSERT INTO sales (invoice_number, num, type, quantity, price, buy_price,
                   total_sel_price, total_buy_price, total_profit, discount, type_value)
SELECT line.invoice_number, line.item, i.unit_id, line.qty, line.price, i.buy_price,
       line.qty * line.price, line.qty * i.buy_price,
       line.qty * (line.price - i.buy_price), 0, 1
FROM (
    SELECT 1001 AS invoice_number,  1 AS item,  6 AS qty,  78.00 AS price UNION ALL
    SELECT 1001,  4, 10,  34.00 UNION ALL
    SELECT 1001, 14, 20,  20.00 UNION ALL
    SELECT 1002, 10, 12, 158.00 UNION ALL
    SELECT 1002, 11,  8,  92.00 UNION ALL
    SELECT 1002,  8, 10,  52.00 UNION ALL
    SELECT 1003,  7, 12,  31.00 UNION ALL
    SELECT 1003, 16, 30,   6.00 UNION ALL
    SELECT 1003,  5,  6,  15.00 UNION ALL
    SELECT 1004,  9, 15,  92.00 UNION ALL
    SELECT 1004, 17, 12,  32.00 UNION ALL
    SELECT 1004, 19, 12,  36.00 UNION ALL
    SELECT 1005, 20, 36,   8.00 UNION ALL
    SELECT 1005, 16, 33,   6.00 UNION ALL
    SELECT 1006,  1, 24,  78.00 UNION ALL
    SELECT 1006,  3, 18, 105.00 UNION ALL
    SELECT 1006, 10,  9, 158.00 UNION ALL
    SELECT 1007, 12, 10,  28.00 UNION ALL
    SELECT 1007, 13,  8,  42.00 UNION ALL
    SELECT 1007, 15, 15,  20.00 UNION ALL
    SELECT 1008,  2,  6,  75.00 UNION ALL
    SELECT 1008,  6, 12,  14.50 UNION ALL
    SELECT 1008, 18,  4,  32.00 UNION ALL
    SELECT 1009,  8, 12,  52.00 UNION ALL
    SELECT 1009,  9,  8,  92.00 UNION ALL
    SELECT 1009,  4, 15,  34.00 UNION ALL
    SELECT 1010, 14, 24,  20.00 UNION ALL
    SELECT 1010, 16, 40,   6.00 UNION ALL
    SELECT 1010, 20, 25,   8.00
) AS line
JOIN items i ON i.id = line.item;

-- A couple of collections, so a customer statement shows movement on both sides.
INSERT INTO customers_accounts (account_code, account_date, paid, purchase, treasury_id, numberInv, notes)
VALUES (2, CURRENT_DATE - INTERVAL 7 DAY, 1200.00, 0, 1, 0, 'تحصيل نقدي'),
       (6, CURRENT_DATE - INTERVAL 4 DAY, 2500.00, 0, 1, 0, 'تحصيل نقدي'),
       (4, CURRENT_DATE - INTERVAL 1 DAY,  500.00, 0, 2, 0, 'تحصيل على المحفظة');

-- Two expenses against the headings V1 seeds.
-- Headings 2 and 5 are V1's own كهرباء and إيجارات. A demo whose every expense sits under one
-- heading makes the "largest heading" card on that screen meaningless.
INSERT INTO expenses_details (type_code, date, amount, notes, treasury_id)
VALUES (5, CURRENT_DATE - INTERVAL 12 DAY, 1800.00, 'إيجار المحل عن الشهر', 1),
       (2, CURRENT_DATE - INTERVAL  8 DAY,  640.00, 'فاتورة الكهرباء', 1),
       (4, CURRENT_DATE - INTERVAL  6 DAY,  120.00, 'فاتورة المياه', 1),
       (6, CURRENT_DATE - INTERVAL  3 DAY,  350.00, 'مواصلات وتوصيل طلبات', 1);

-- The numbering must not hand out a number already on a document above.
UPDATE document_sequences
SET current_value = GREATEST(current_value, (SELECT COALESCE(MAX(invoice_number), 0) FROM total_sales))
WHERE document_type = 'SALES';
