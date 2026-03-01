-- SQL migration: align old schema to the new one (MySQL 5.7+ safe-ish)
SET NAMES utf8mb4;

-- 0) Helpers
SET @db := DATABASE();

-- 1) items: add missing columns (sel_price1/2/3 + validity/expiry flags)
-- sel_price1
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'sel_price1';
SET @sql := IF(@c = 0,
               'ALTER TABLE items ADD COLUMN sel_price1 DOUBLE NOT NULL DEFAULT 0 AFTER buy_price',
               'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- sel_price2
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'sel_price2';
SET @sql := IF(@c = 0,
               'ALTER TABLE items ADD COLUMN sel_price2 DOUBLE NOT NULL DEFAULT 0 AFTER sel_price1',
               'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- sel_price3
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'sel_price3';
SET @sql := IF(@c = 0,
               'ALTER TABLE items ADD COLUMN sel_price3 DOUBLE NOT NULL DEFAULT 0 AFTER sel_price2',
               'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- item_active
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'item_active';
SET @sql := IF(@c = 0,
               'ALTER TABLE items ADD COLUMN item_active TINYINT(1) NOT NULL DEFAULT 1 AFTER item_image',
               'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- item_has_validity
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'item_has_validity';
SET @sql := IF(@c = 0,
               'ALTER TABLE items ADD COLUMN item_has_validity TINYINT(1) NOT NULL DEFAULT 0 AFTER item_active',
               'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- number_validity_days
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'number_validity_days';
SET @sql := IF(@c = 0,
               'ALTER TABLE items ADD COLUMN number_validity_days INT NOT NULL DEFAULT 0 AFTER item_has_validity',
               'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- alert_days_before_expire
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'alert_days_before_expire';
SET @sql := IF(@c = 0,
               'ALTER TABLE items ADD COLUMN alert_days_before_expire INT NOT NULL DEFAULT 0 AFTER number_validity_days',
               'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- 2) Migrate price from items_price -> items.sel_price1 (if items_price exists)
SELECT COUNT(*)
INTO @has_ip
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME = 'items_price';

-- 2.1) If type_price has 'قطاعي', map sel_price1 from that row
SELECT COUNT(*)
INTO @has_retail
FROM information_schema.TABLES t
WHERE t.TABLE_SCHEMA = @db
  AND t.TABLE_NAME = 'type_price';

SET @sql := IF(@has_ip > 0 AND @has_retail > 0,
               'UPDATE items i
                  JOIN items_price ip ON i.id=ip.item_id
                  JOIN type_price tp ON tp.id=ip.price_id AND tp.name=''قطاعي''
                SET i.sel_price1 = ip.sel_price',
               'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- 2.2) Fallback: for items still with sel_price1=0, use MAX price per item from items_price
SET @sql := IF(@has_ip > 0,
               'UPDATE items i
                  JOIN (SELECT item_id, MAX(sel_price) AS p FROM items_price GROUP BY item_id) x
                    ON i.id=x.item_id
                SET i.sel_price1 = x.p
                WHERE i.sel_price1 = 0',
               'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- 3) Set all items active except id=0
UPDATE items
SET item_active = 1
WHERE id <> 0;

-- 4) Add expiration_date to purchase/purchase_re/sales/sales_re if missing
-- purchase.expiration_date
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME = 'purchase'
  AND COLUMN_NAME = 'expiration_date';
SET @sql := IF(@c = 0,
               'ALTER TABLE purchase ADD COLUMN expiration_date DATE NULL AFTER type_value',
               'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- purchase_re.expiration_date
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME = 'purchase_re'
  AND COLUMN_NAME = 'expiration_date';
SET @sql := IF(@c = 0,
               'ALTER TABLE purchase_re ADD COLUMN expiration_date DATE NULL AFTER type_value',
               'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- sales.expiration_date
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME = 'sales'
  AND COLUMN_NAME = 'expiration_date';
SET @sql := IF(@c = 0,
               'ALTER TABLE sales ADD COLUMN expiration_date DATE NULL AFTER type_value',
               'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- sales_re.expiration_date
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME = 'sales_re'
  AND COLUMN_NAME = 'expiration_date';
SET @sql := IF(@c = 0,
               'ALTER TABLE sales_re ADD COLUMN expiration_date DATE NULL AFTER type_value',
               'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- 5) users: cleanup nulls then widen/change types and NOT NULL/defaults
-- Ensure non-null values to pass NOT NULL constraint
UPDATE users
SET user_name = COALESCE(NULLIF(user_name, ''), CONCAT('user_', id))
WHERE user_name IS NULL
   OR user_name = '';

UPDATE users
SET user_pass = COALESCE(user_pass, '')
WHERE user_pass IS NULL;

-- Apply type/length/default changes
ALTER TABLE users
    MODIFY COLUMN user_name VARCHAR(50) NOT NULL,
    MODIFY COLUMN user_pass VARCHAR(255) NOT NULL,
    MODIFY COLUMN user_activity TINYINT(1) NOT NULL DEFAULT 1,
    MODIFY COLUMN user_available TINYINT(1) NOT NULL DEFAULT 0;

-- 6) Reference data: ensure default price types exist (do not truncate existing data)
INSERT IGNORE INTO type_price(name)
VALUES ('قطاعي'),
       ('سعر2'),
       ('سعر3');

-- 7) Drop items_price table (no longer used)
DROP TABLE IF EXISTS items_price;
