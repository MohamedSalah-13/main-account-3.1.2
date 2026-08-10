-- =====================================================================
-- V6 - A price per unit.
--
-- `items_units` has carried `buy_price` and `sel_price` since the baseline and
-- nothing ever read them: the invoice priced every unit as the item's base
-- price multiplied by the factor, so a carton of twelve cost exactly twelve
-- pieces and there was no way to sell it for less. That is the whole point of
-- selling by the carton.
--
-- The item has three selling prices (the tier a customer is on decides which),
-- so a unit needs three as well - one override cannot answer for a retail and
-- a wholesale customer both. `sel_price` is tier 1; this file adds the others.
--
-- Zero means "not set" and falls back to the item's price times the factor,
-- which is what every existing row holds, so nothing is repriced by this
-- migration.
-- =====================================================================

SET @col := (SELECT COUNT(*)
             FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'items_units'
               AND column_name = 'sel_price2');
SET @sql := IF(@col = 0,
               'ALTER TABLE items_units ADD COLUMN sel_price2 DECIMAL(14, 2) DEFAULT 0 NOT NULL AFTER sel_price',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*)
             FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'items_units'
               AND column_name = 'sel_price3');
SET @sql := IF(@col = 0,
               'ALTER TABLE items_units ADD COLUMN sel_price3 DECIMAL(14, 2) DEFAULT 0 NOT NULL AFTER sel_price2',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- A price is never negative, and a negative one would read as "set" and be
-- charged. Zero stays legal - it is how a unit says it has no price of its own.
UPDATE items_units
SET buy_price = 0
WHERE buy_price < 0;

UPDATE items_units
SET sel_price = 0
WHERE sel_price < 0;

SET @chk := (SELECT COUNT(*)
             FROM information_schema.table_constraints
             WHERE table_schema = DATABASE()
               AND table_name = 'items_units'
               AND constraint_name = 'items_units_prices_chk');
SET @sql := IF(@chk = 0,
               'ALTER TABLE items_units ADD CONSTRAINT items_units_prices_chk
                    CHECK (buy_price >= 0 AND sel_price >= 0 AND sel_price2 >= 0 AND sel_price3 >= 0)',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
