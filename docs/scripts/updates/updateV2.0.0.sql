-- Ensure connection/session uses UTF-8 (utf8mb4) during restore
SET NAMES utf8mb4;

-- Create procedure to add column if it doesn't exist
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

DELIMITER |
CREATE PROCEDURE add_column_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_definition VARCHAR(255)
)
BEGIN
    DECLARE col_exists INT DEFAULT 0;

    SELECT COUNT(*)
    INTO col_exists
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = p_table_name
      AND column_name = p_column_name;

    IF col_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name, ' ADD COLUMN ', p_column_name, ' ', p_column_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END |
DELIMITER ;

-- Items: selling price tiers and validity/expiry tracking
CALL add_column_if_not_exists('items', 'sel_price1', 'DOUBLE DEFAULT 0 NOT NULL AFTER buy_price');
CALL add_column_if_not_exists('items', 'sel_price2', 'DOUBLE DEFAULT 0 NOT NULL AFTER sel_price1');
CALL add_column_if_not_exists('items', 'sel_price3', 'DOUBLE DEFAULT 0 NOT NULL AFTER sel_price2');
CALL add_column_if_not_exists('items', 'item_active', 'TINYINT(1) DEFAULT 1 NOT NULL AFTER item_image');
CALL add_column_if_not_exists('items', 'item_has_validity', 'TINYINT(1) DEFAULT 0 NOT NULL AFTER item_active');
CALL add_column_if_not_exists('items', 'number_validity_days', 'INT DEFAULT 0 NOT NULL AFTER item_has_validity');
CALL add_column_if_not_exists('items', 'alert_days_before_expire', 'INT DEFAULT 0 NOT NULL AFTER number_validity_days');

-- Purchase/Sales lines: add expiration_date when applicable
CALL add_column_if_not_exists('purchase', 'expiration_date', 'DATE NULL AFTER type_value');
CALL add_column_if_not_exists('purchase_re', 'expiration_date', 'DATE NULL AFTER type_value');
CALL add_column_if_not_exists('sales', 'expiration_date', 'DATE NULL AFTER type_value');
CALL add_column_if_not_exists('sales_re', 'expiration_date', 'DATE NULL AFTER type_value');

-- Cleanup procedure
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- update all items set all active
update items i
set i.item_active= true
where i.id != 0;

-- Check if table exists before attempting migration
SET @table_exists = (SELECT COUNT(*)
                     FROM information_schema.tables
                     WHERE table_schema = DATABASE()
                       AND table_name = 'items_price');

-- Only run if table exists
SET @query = IF(@table_exists > 0,
                'UPDATE items i INNER JOIN items_price ip ON i.id = ip.item_id SET i.sel_price1 = ip.sel_price WHERE ip.id >= 1',
                'SELECT "items_price table does not exist, skipping migration" AS message');

PREPARE stmt FROM @query;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Remove objects not present in the reference
DROP TABLE IF EXISTS items_price;

-- type_price update
SET FOREIGN_KEY_CHECKS = 0;
truncate table type_price;
SET FOREIGN_KEY_CHECKS = 1;
INSERT INTO type_price (name)
VALUES ('سعر1'),
       ('سعر2'),
       ('سعر3');


-- update items_units
alter table items_units
    modify items_barcode varchar(50) null;

alter table items_units
    alter column buy_price set default 0;

alter table items_units
    alter column sel_price set default 0;

alter table items_units
    drop foreign key items_units_units_unit_id_fk;

alter table items_units
    add constraint items_units_units_unit_id_fk
        foreign key (unit) references units (unit_id)
            on update cascade on delete cascade;

alter table items_units
    drop key items_units_pk;


-- update users
alter table users
    modify user_name varchar(50) not null;

alter table users
    modify user_pass varchar(255) not null;

alter table users
    modify user_activity tinyint(1) default 1 not null;

alter table users
    modify user_available tinyint(1) default 0 not null;
