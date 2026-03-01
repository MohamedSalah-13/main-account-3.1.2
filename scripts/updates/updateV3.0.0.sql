alter table customers_accounts
    modify notes longtext null;
alter table suppliers_accounts
    modify notes longtext null;
alter table custom
    modify notes longtext null;
alter table suppliers
    modify notes longtext null;
alter table total_sales
    modify notes longtext null;
alter table total_sales_re
    modify notes longtext null;
alter table total_buy
    modify notes longtext null;
alter table total_buy_re
    modify notes longtext null;
alter table treasury_transfers
    modify notes longtext null;

# --------------------------------- Timestamp Column Management Procedures -------------------------------#
DROP PROCEDURE IF EXISTS rename_date_insert_to_created_at;
DROP PROCEDURE IF EXISTS add_updated_at_column;

DELIMITER |
CREATE PROCEDURE rename_date_insert_to_created_at(IN p_table_name VARCHAR(64))
BEGIN
    DECLARE col_exists INT DEFAULT 0;

    SELECT COUNT(*)
    INTO col_exists
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = p_table_name
      AND column_name = 'date_insert';

    IF col_exists > 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name,
                          ' CHANGE date_insert created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END |

CREATE PROCEDURE add_updated_at_column(IN p_table_name VARCHAR(64), IN p_after_column VARCHAR(64))
BEGIN
    DECLARE col_exists INT DEFAULT 0;

    SELECT COUNT(*)
    INTO col_exists
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = p_table_name
      AND column_name = 'updated_at';

    IF col_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table_name,
                          ' ADD `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL',
                          ' ON UPDATE CURRENT_TIMESTAMP AFTER ', p_after_column);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END |
DELIMITER ;

# --------------------------------- Apply Timestamp Columns to Tables -------------------------------#
-- Update company table
CALL add_updated_at_column('company', 'comp_image');

-- Update users table
CALL add_updated_at_column('users', 'user_available');

-- Update employees table
CALL add_updated_at_column('employees', 'date_insert');

-- Update main_group table
CALL add_updated_at_column('main_group', 'date_insert');

-- Update processes_data table
CALL add_updated_at_column('processes_data', 'date_insert');

-- Update stock_transfer table
CALL add_updated_at_column('stock_transfer', 'date_insert');

-- Update stocks table
CALL add_updated_at_column('stocks', 'date_insert');

-- Update sub_group table
CALL add_updated_at_column('sub_group', 'date_insert');

-- Update suppliers table
CALL add_updated_at_column('suppliers', 'date_insert');

-- Update targeted_sales table
CALL add_updated_at_column('targeted_sales', 'date_insert');

-- Update treasury table
CALL add_updated_at_column('treasury', 'date_insert');

-- Update expenses_details table
CALL add_updated_at_column('expenses_details', 'date_insert');

-- Update suppliers_accounts table
CALL add_updated_at_column('suppliers_accounts', 'date_insert');

-- Update total_buy table
CALL add_updated_at_column('total_buy', 'date_insert');

-- Update total_buy_re table
CALL add_updated_at_column('total_buy_re', 'date_insert');

-- Update treasury_deposit_expenses table
CALL add_updated_at_column('treasury_deposit_expenses', 'date_insert');

-- Update treasury_transfers table
CALL add_updated_at_column('treasury_transfers', 'date_insert');

-- Update type_price table
CALL add_updated_at_column('type_price', 'date_insert');

-- Update custom table
CALL rename_date_insert_to_created_at('custom');
CALL add_updated_at_column('custom', 'created_at');

-- Update customers_accounts table
CALL rename_date_insert_to_created_at('customers_accounts');
CALL add_updated_at_column('customers_accounts', 'created_at');

-- Update total_sales table
CALL add_updated_at_column('total_sales', 'date_insert');

-- Update total_sales_re table
CALL add_updated_at_column('total_sales_re', 'date_insert');

-- Update units table
CALL add_updated_at_column('units', 'date_insert');

-- Update items table
CALL rename_date_insert_to_created_at('items');
CALL add_updated_at_column('items', 'created_at');

-- Update items_units table
CALL add_updated_at_column('items_units', 'date_insert');

-- Update user_permission table
CALL add_updated_at_column('user_permission', 'check_status');


-- Cleanup procedures
DROP PROCEDURE IF EXISTS rename_date_insert_to_created_at;
DROP PROCEDURE IF EXISTS add_updated_at_column;