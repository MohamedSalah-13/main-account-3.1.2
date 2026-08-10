-- =====================================================================
-- V5 - Per-item unit factors.
--
-- `units.value_d` is one number per unit for the whole database, so "كرتونة"
-- meant the same multiple for every item: a carton of juice and a carton of
-- cigarettes could not both be right. The per-item factor already had a home
-- in `items_units.quantity`; nothing read it, and the invoice screens scaled
-- by `units.value_d` instead.
--
-- This file makes `items_units` usable as that source of truth. `units` keeps
-- `value_d` - old invoice lines stored their factor in `*.type_value` at the
-- time of sale and must keep resolving exactly as before - but it becomes the
-- fallback for an item that has no row of its own, not the answer.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) A unit row does not need its own barcode.
--
-- The column is NOT NULL with a UNIQUE index, so every row added without one
-- carried '' and the second such row for any item was rejected by the index -
-- an item could have a carton or a roll, never both. NULLs repeat freely under
-- a MySQL UNIQUE index, so widen the column and move the empty strings over.
-- ---------------------------------------------------------------------
ALTER TABLE items_units
    MODIFY items_barcode VARCHAR(50) NULL;

UPDATE items_units
SET items_barcode = NULL
WHERE items_barcode = '';

-- ---------------------------------------------------------------------
-- 2) The base unit is not an `items_units` row.
--
-- It is `items.unit_id`, and the loader prepends it to the list with a factor
-- of 1. A row here for the same unit shows up twice on the item and competes
-- with the synthesized one, so drop those.
-- ---------------------------------------------------------------------
DELETE iu
FROM items_units iu
         JOIN items i ON i.id = iu.items_id
WHERE iu.unit = i.unit_id;

-- ---------------------------------------------------------------------
-- 3) One row per (item, unit).
--
-- `before_items_units_insert` enforced this by hand and only on INSERT, which
-- an UPDATE walked straight past. A unique key covers both and reports the
-- clash to the driver as a duplicate key rather than a raw SIGNAL. Delete the
-- older duplicates first - the highest id is the one last saved.
-- ---------------------------------------------------------------------
DELETE iu
FROM items_units iu
         JOIN items_units keep
              ON keep.items_id = iu.items_id
                  AND keep.unit = iu.unit
                  AND keep.id > iu.id;

SET @uk := (SELECT COUNT(*)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'items_units'
              AND index_name = 'items_units_item_unit_uk');
SET @sql := IF(@uk = 0,
               'ALTER TABLE items_units ADD CONSTRAINT items_units_item_unit_uk UNIQUE (items_id, unit)',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TRIGGER IF EXISTS before_items_units_insert;

-- ---------------------------------------------------------------------
-- 4) An item's own unit is its base unit, and a base unit's factor is 1.
--
-- That was not true where someone set `value_d` above 1 on a unit and then
-- made it an item's main unit. The invoice screens multiplied by `value_d`
-- regardless, so such an item's stock was counted in fractions of the unit it
-- was actually sold in: buying one carton wrote quantity 1, type_value 12, and
-- the balance moved by 12 of something the screens still labelled "carton".
-- The old code recognised the case only by refusing to let those items change
-- unit at all (`comboType.setDisable(value > 1)`).
--
-- Restate those items in the unit they are sold in. Quantities on past lines
-- are left alone - one carton bought stays one carton - and everything counted
-- against them is divided by the same factor, so the resulting balance is the
-- same physical stock expressed in cartons rather than in twelfths of one.
-- Prices move the other way: they were held per twelfth and are now per carton.
--
-- On a database where no unit was given a factor (the shipped default is 1)
-- this matches nothing and changes nothing.
-- ---------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS rebased_items;
CREATE TEMPORARY TABLE rebased_items
(
    item_id INT PRIMARY KEY,
    factor  DECIMAL(14, 3) NOT NULL
);

INSERT INTO rebased_items (item_id, factor)
SELECT i.id, u.value_d
FROM items i
         JOIN units u ON u.unit_id = i.unit_id
WHERE u.value_d <> 1
  AND u.value_d > 0;

UPDATE items_stock s
    JOIN rebased_items r ON r.item_id = s.item_id
SET s.first_balance    = s.first_balance / r.factor,
    s.current_quantity = s.current_quantity / r.factor;

UPDATE items i
    JOIN rebased_items r ON r.item_id = i.id
SET i.first_balance = i.first_balance / r.factor,
    i.mini_quantity = i.mini_quantity / r.factor,
    i.buy_price     = i.buy_price * r.factor,
    i.sel_price1    = i.sel_price1 * r.factor,
    i.sel_price2    = i.sel_price2 * r.factor,
    i.sel_price3    = i.sel_price3 * r.factor;

-- Only the lines written in the item's own unit. A line in some other unit of
-- the same item keeps the factor it was saved with, which is the whole reason
-- `type_value` is stored per line.
UPDATE purchase p
    JOIN items i ON i.id = p.num
    JOIN rebased_items r ON r.item_id = i.id
SET p.type_value = 1
WHERE p.type = i.unit_id;

UPDATE purchase_re p
    JOIN items i ON i.id = p.item_id
    JOIN rebased_items r ON r.item_id = i.id
SET p.type_value = 1
WHERE p.type = i.unit_id;

UPDATE sales s
    JOIN items i ON i.id = s.num
    JOIN rebased_items r ON r.item_id = i.id
SET s.type_value = 1
WHERE s.type = i.unit_id;

UPDATE sales_re s
    JOIN items i ON i.id = s.item_id
    JOIN rebased_items r ON r.item_id = i.id
SET s.type_value = 1
WHERE s.type = i.unit_id;

-- An item's extra units were saved with the unit's global factor, so they mean
-- "twelfths per carton" for a rebased item too. Restate them against the new base.
UPDATE items_units iu
    JOIN rebased_items r ON r.item_id = iu.items_id
SET iu.quantity = iu.quantity / r.factor
WHERE iu.quantity / r.factor > 0;

-- `value_d` no longer answers for anything that has a row of its own; it stays
-- on the table as the fallback for units that never got one.
UPDATE units u
SET u.value_d = 1
WHERE u.unit_id IN (SELECT unit_id FROM items);

DROP TEMPORARY TABLE IF EXISTS rebased_items;
