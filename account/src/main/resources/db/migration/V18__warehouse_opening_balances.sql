-- Make items_stock the per-warehouse source of opening stock.
--
-- The single-warehouse application wrote the editable value to items.first_balance,
-- while the old multi-warehouse schema also carried a copy in items_stock. Existing
-- installations can therefore have a stale stock-1 copy. Preserve the value users
-- have actually seen by moving that items value into the default warehouse first.
UPDATE items_stock ist
         JOIN items i ON i.id = ist.item_id
SET ist.first_balance = i.first_balance
WHERE ist.stock_id = 1;

-- quantity_items_table is driven by items_stock, so every item needs one row for every
-- warehouse. The historic opening belongs to the default warehouse only; a second
-- warehouse must not inherit it merely because its row was missing. Existing rows in
-- non-default warehouses are deliberately left untouched.
INSERT INTO items_stock (item_id, stock_id, first_balance, current_quantity)
SELECT i.id,
       s.stock_id,
       CASE WHEN s.stock_id = 1 THEN i.first_balance ELSE 0 END,
       CASE WHEN s.stock_id = 1 THEN i.first_balance ELSE 0 END
FROM items i
         CROSS JOIN stocks s
         LEFT JOIN items_stock ist
                   ON ist.item_id = i.id AND ist.stock_id = s.stock_id
WHERE ist.id IS NULL;
