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

-- add columns to items
CALL add_column_if_not_exists('items', 'item_has_package',
                              'tinyint(1) default 0 not null after alert_days_before_expire');

-- add columns to sales
CALL add_column_if_not_exists('sales', 'item_has_package',
                              'tinyint(1) default 0 not null after expiration_date');

-- drop procedure
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- create table items_package
CREATE TABLE IF NOT EXISTS items_package
(
    id         int AUTO_INCREMENT PRIMARY KEY,
    item_id    int    NOT NULL,
    package_id int    NOT NULL,
    quantity   double NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    constraint items_package_items_id_fk
        foreign key (package_id) references items (id)
            on update cascade on delete cascade
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- create table sales_package
CREATE TABLE IF NOT EXISTS sales_package
(
    id              int auto_increment
        primary key,
    sales_id        int              not null,
    item_id         int              not null,
    unit_id         int    default 1 not null,
    quantity        double           not null,
    price           double           not null,
    buy_price       double           not null,
    total_sel_price double default 0 not null,
    total_buy_price double default 0 not null,
    total_profit    double default 0 not null,
    discount        double default 0 not null,
    unit_value      double default 1 not null,
    expiration_date date             null,
    constraint sales_package_items_id_fk
        foreign key (item_id) references items (id),
    constraint sales_package_sales_id_fk
        foreign key (sales_id) references sales (id)
            on update cascade on delete cascade,
    constraint sales_package_units_unit_id_fk
        foreign key (unit_id) references units (unit_id)
);

create index sales_items_id_fk
    on sales_package (item_id);

create index sales_total_invoice_number_fk
    on sales_package (sales_id);

create index sales_units_unit_id_fk
    on sales_package (unit_id);


-- other data

SELECT table_name,
       DATE_FORMAT(FROM_UNIXTIME(UNIX_TIMESTAMP(update_time)), '%Y-%m-%d %H:%i:%s') as last_modified
FROM information_schema.tables
WHERE table_schema = DATABASE()
ORDER BY update_time DESC;