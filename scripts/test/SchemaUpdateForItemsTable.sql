-- MySQL 5.7 compatible migration (idempotent-ish)
SET NAMES utf8mb4;

-- 1) إضافة الأعمدة المفقودة في items بشكل شرطي
-- sel_price1
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'sel_price1';
SET @sql = IF(@c = 0,
              'ALTER TABLE items ADD COLUMN sel_price1 DOUBLE NOT NULL DEFAULT 0 AFTER buy_price',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- sel_price2
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'sel_price2';
SET @sql = IF(@c = 0,
              'ALTER TABLE items ADD COLUMN sel_price2 DOUBLE NOT NULL DEFAULT 0 AFTER sel_price1',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- sel_price3
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'sel_price3';
SET @sql = IF(@c = 0,
              'ALTER TABLE items ADD COLUMN sel_price3 DOUBLE NOT NULL DEFAULT 0 AFTER sel_price2',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- item_active
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'item_active';
SET @sql = IF(@c = 0,
              'ALTER TABLE items ADD COLUMN item_active TINYINT(1) NOT NULL DEFAULT 1 AFTER item_image',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- item_has_validity
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'item_has_validity';
SET @sql = IF(@c = 0,
              'ALTER TABLE items ADD COLUMN item_has_validity TINYINT(1) NOT NULL DEFAULT 0 AFTER item_active',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- number_validity_days
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'number_validity_days';
SET @sql = IF(@c = 0,
              'ALTER TABLE items ADD COLUMN number_validity_days INT NOT NULL DEFAULT 0 AFTER item_has_validity',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- alert_days_before_expire
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'items'
  AND COLUMN_NAME = 'alert_days_before_expire';
SET @sql = IF(@c = 0,
              'ALTER TABLE items ADD COLUMN alert_days_before_expire INT NOT NULL DEFAULT 0 AFTER number_validity_days',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- 2) تحديث sel_price1 من items_price إن وُجد
SELECT COUNT(*)
INTO @t
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'items_price';
SET @sql = IF(@t > 0,
              'UPDATE items i INNER JOIN items_price ip ON i.id = ip.item_id SET i.sel_price1 = ip.sel_price WHERE ip.id >= 1',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- 3) حذف جدول items_price إن وُجد
DROP TABLE IF EXISTS items_price;

-- 4) تفعيل كل العناصر عدا id = 0
UPDATE items
SET item_active = 1
WHERE id <> 0;

-- 5) إضافة expiration_date للحركات إن لم توجد
-- purchase
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'purchase'
  AND COLUMN_NAME = 'expiration_date';
SET @sql = IF(@c = 0,
              'ALTER TABLE purchase ADD COLUMN expiration_date DATE NULL AFTER type_value',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- purchase_re
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'purchase_re'
  AND COLUMN_NAME = 'expiration_date';
SET @sql = IF(@c = 0,
              'ALTER TABLE purchase_re ADD COLUMN expiration_date DATE NULL AFTER type_value',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- sales
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sales'
  AND COLUMN_NAME = 'expiration_date';
SET @sql = IF(@c = 0,
              'ALTER TABLE sales ADD COLUMN expiration_date DATE NULL AFTER type_value',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- sales_re
SELECT COUNT(*)
INTO @c
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sales_re'
  AND COLUMN_NAME = 'expiration_date';
SET @sql = IF(@c = 0,
              'ALTER TABLE sales_re ADD COLUMN expiration_date DATE NULL AFTER type_value',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- 6) تفريغ وإعادة تعبئة type_price
SET @old_fk_checks := @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE type_price;
SET FOREIGN_KEY_CHECKS = @old_fk_checks;

INSERT INTO type_price (name)
VALUES ('قطاعي'),
       ('سعر2'),
       ('سعر3');

-- 7) تحديث items_units
-- 7.1) تعديل items_barcode
ALTER TABLE items_units
    MODIFY COLUMN items_barcode VARCHAR(50) NULL;

-- 7.2) ضبط القيم الافتراضية للأسعار (صيغة مدعومة في MySQL 5.7)
ALTER TABLE items_units
    ALTER COLUMN buy_price SET DEFAULT 0;
ALTER TABLE items_units
    ALTER COLUMN sel_price SET DEFAULT 0;

-- 7.3) إسقاط المفتاح الأجنبي إذا كان موجوداً
SELECT COUNT(*)
INTO @fk_exists
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'items_units_units_unit_id_fk';
SET @sql = IF(@fk_exists > 0,
              'ALTER TABLE items_units DROP FOREIGN KEY items_units_units_unit_id_fk',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- 7.4) إضافة المفتاح الأجنبي إذا لم يكن موجوداً
SELECT COUNT(*)
INTO @fk_exists
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME = 'items_units_units_unit_id_fk';
SET @sql = IF(@fk_exists = 0,
              'ALTER TABLE items_units ADD CONSTRAINT items_units_units_unit_id_fk
                 FOREIGN KEY (unit) REFERENCES units (unit_id)
                 ON UPDATE CASCADE ON DELETE CASCADE',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- 7.5) إسقاط الفهرس items_units_pk إن وجد (فهرس عادي)
SELECT COUNT(*)
INTO @idx_exists
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'items_units'
  AND index_name = 'items_units_pk';
SET @sql = IF(@idx_exists > 0,
              'ALTER TABLE items_units DROP INDEX items_units_pk',
              'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- 8) تعديل أعمدة users (يعاد تنفيذها بأمان)
ALTER TABLE users
    MODIFY COLUMN user_name VARCHAR(50) NOT NULL,
    MODIFY COLUMN user_pass VARCHAR(255) NOT NULL,
    MODIFY COLUMN user_activity TINYINT(1) NOT NULL DEFAULT 1,
    MODIFY COLUMN user_available TINYINT(1) NOT NULL DEFAULT 0;
