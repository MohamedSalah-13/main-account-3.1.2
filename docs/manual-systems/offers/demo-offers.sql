-- Extra isolated examples for the subsystem guide. This file is applied only to
-- account_manual_demo, after docs/manual/demo-data.sql.
INSERT INTO offer (id, name, kind, status, starts_on, ends_on, priority, percent, notes, user_id)
VALUES (1, 'خصم تجريبي 10%', 'PERCENT', 'ACTIVE', CURRENT_DATE - INTERVAL 7 DAY,
        CURRENT_DATE + INTERVAL 23 DAY, 10, 10, 'بيانات توضيحية لدليل نظام العروض', 1)
ON DUPLICATE KEY UPDATE status = VALUES(status), starts_on = VALUES(starts_on),
                        ends_on = VALUES(ends_on), priority = VALUES(priority), percent = VALUES(percent);

INSERT INTO offer_target (offer_id, scope, item_id, excluded, role)
SELECT 1, 'ITEM', 10, 0, 'QUALIFY'
WHERE NOT EXISTS (SELECT 1 FROM offer_target WHERE offer_id = 1 AND scope = 'ITEM' AND item_id = 10);

INSERT INTO total_sales (invoice_number, sup_code, invoice_type, invoice_date, total, discount,
                         paid_up, stock_id, delegate_id, treasury_id)
VALUES (1011, 2, 1, CURRENT_DATE, 142.20, 0.00, 142.20, 1, 1, 1)
ON DUPLICATE KEY UPDATE total = VALUES(total), paid_up = VALUES(paid_up);

INSERT INTO sales (invoice_number, num, type, quantity, price, buy_price, total_sel_price,
                   total_buy_price, total_profit, discount, type_value, offer_id,
                   offer_discount, offer_quantity)
SELECT 1011, 10, i.unit_id, 1, 158.00, i.buy_price, 158.00,
       i.buy_price, 7.20, 15.80, 1, 1, 15.80, 1
FROM items i WHERE i.id = 10
ON DUPLICATE KEY UPDATE offer_id = VALUES(offer_id), offer_discount = VALUES(offer_discount),
                        offer_quantity = VALUES(offer_quantity), discount = VALUES(discount),
                        total_profit = VALUES(total_profit);
