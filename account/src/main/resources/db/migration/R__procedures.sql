-- =====================================================================
-- Repeatable - stored procedures.
--
-- Runs before R__triggers.sql (Flyway orders repeatables by description),
-- which matters because the audit triggers call write_audit_log.
-- Every procedure is preceded by DROP PROCEDURE IF EXISTS, so re-running
-- this file simply replaces the definitions.
-- =====================================================================

DROP PROCEDURE IF EXISTS write_audit_log;

DELIMITER |
CREATE PROCEDURE write_audit_log(
    IN p_table_name VARCHAR(100),
    IN p_record_id VARCHAR(100),
    IN p_action_type VARCHAR(20),
    IN p_user_id INT,
    IN p_old_data JSON,
    IN p_new_data JSON,
    IN p_notes TEXT
)
BEGIN
    INSERT INTO audit_log
    (
        table_name,
        record_id,
        action_type,
        user_id,
        old_data,
        new_data,
        notes
    )
    VALUES
        (
            UPPER(p_table_name),
            p_record_id,
            UPPER(p_action_type),
            p_user_id,
            p_old_data,
            p_new_data,
            p_notes
        );
END;
|
DELIMITER ;

DROP PROCEDURE IF EXISTS max_item_id;

/*----------------------------------------------- max_id -----------------------------------------------*/
DELIMITER |
create
 procedure max_item_id(OUT itemId int)
begin
    SET itemId = (SELECT id
                  from items
                  ORDER BY id DESC
                  LIMIT 1);
end;
|
DELIMITER ;

-- #####################################################################
-- ## Truncate/reset procedures               (was V007_user_truncate) ##
-- #####################################################################
/*------------------------------------ truncateTableSales - 6 tables ------------------------------------ */
DROP procedure if exists truncateTableSales;

DELIMITER |
create
 procedure truncateTableSales(IN salesReturn tinyint(1), IN deleteSales tinyint(1),
                                                          IN deleteAccount tinyint(1), IN deleteName tinyint(1))
begin
    SET FOREIGN_KEY_CHECKS = 0;
    if (salesReturn) THEN
        TRUNCATE table total_sales_re;
        TRUNCATE table sales_re;
    End IF;

    if (deleteSales) THEN
        TRUNCATE table total_sales;
        TRUNCATE table sales;
    End IF;

    IF (deleteAccount) Then
        TRUNCATE table customers_accounts;
    End IF;

    IF (deleteName) Then
        # this use for customer
        TRUNCATE table custom;
        INSERT INTO custom(id, name, limit_num, price_id)
        VALUES (1, 'بيع نقدى', 5000, 1);
    End IF;

    SET FOREIGN_KEY_CHECKS = 1;
END
|
DELIMITER ;

/*------------------------------------ truncateTablePurchase - 6 tables ------------------------------------ */
DROP procedure if exists truncateTablePurchase;

DELIMITER |
create
 procedure truncateTablePurchase(IN deletePurchaseReturn tinyint(1),
                                                             IN deletePurchase tinyint(1),
                                                             IN deleteAccount tinyint(1), IN deleteName tinyint(1))
begin
    SET FOREIGN_KEY_CHECKS = 0;
    if (deletePurchaseReturn) THEN
        TRUNCATE table total_buy_re;
        TRUNCATE table purchase_re;
    End IF;

    if (deletePurchase) THEN
        TRUNCATE table total_buy;
        TRUNCATE table purchase;
    End IF;

    IF (deleteAccount) Then
        TRUNCATE table suppliers_accounts;
    End IF;

    IF (deleteName) Then
        TRUNCATE table suppliers;
        INSERT INTO suppliers(id, name)
        VALUES (1, 'مورد عام');
    End IF;

    SET FOREIGN_KEY_CHECKS = 1;
END
|
DELIMITER ;

/*------------------------------------ truncateTableItems -12 tables ------------------------------------ */
DROP procedure IF EXISTS truncateTableItems;
DELIMITER |
CREATE
 PROCEDURE truncateTableItems(IN deleteItems TINYINT(1),
                                                          IN deleteStock TINYINT(1),
                                                          IN deleteSubGroup TINYINT(1),
                                                          IN deleteMainGroup TINYINT(1))
BEGIN
    SET FOREIGN_KEY_CHECKS = 0;

    IF (deleteItems) THEN
        CALL truncateAndInitializeItemsTables();
    END IF;

    IF (deleteStock) THEN
        CALL truncateAndInitializeStocksTables();
    END IF;

    IF (deleteSubGroup) THEN
        CALL truncateAndInitializeSubGroupTable();
    END IF;

    IF (deleteMainGroup) THEN
        CALL truncateAndInitializeMainGroupTable();
    END IF;

    SET FOREIGN_KEY_CHECKS = 1;
END
|

DELIMITER ;
DROP PROCEDURE IF EXISTS truncateAndInitializeItemsTables;
DELIMITER |
CREATE PROCEDURE truncateAndInitializeItemsTables()
BEGIN
    TRUNCATE TABLE units;
    INSERT INTO units(unit_name) VALUES ('قطعة'), ('كرتونة');

    TRUNCATE TABLE type_price;
    INSERT INTO type_price (name)
    VALUES ('سعر1'),
           ('سعر2'),
           ('سعر3');

    TRUNCATE TABLE items;
    TRUNCATE TABLE items_package;
    TRUNCATE TABLE items_units;
    TRUNCATE TABLE items_stock;
    TRUNCATE TABLE stock_movements;
END
|

DELIMITER ;
DROP PROCEDURE IF EXISTS truncateAndInitializeStocksTables;
DELIMITER |
CREATE PROCEDURE truncateAndInitializeStocksTables()
BEGIN
    TRUNCATE TABLE stock_movements;
    TRUNCATE TABLE stocks;
    TRUNCATE TABLE stock_transfer;
    TRUNCATE TABLE stock_transfer_list;
    INSERT INTO stocks(stock_name) VALUES ('الرئيسى');
END
|

DELIMITER ;
DROP PROCEDURE IF EXISTS truncateAndInitializeSubGroupTable;
DELIMITER |
CREATE PROCEDURE truncateAndInitializeSubGroupTable()
BEGIN
    TRUNCATE TABLE sub_group;
    INSERT INTO sub_group(name, main_id) VALUES ('فرع 1', 1);
END
|

DELIMITER ;
DROP PROCEDURE IF EXISTS truncateAndInitializeMainGroupTable;
DELIMITER |
CREATE PROCEDURE truncateAndInitializeMainGroupTable()
BEGIN
    TRUNCATE TABLE main_group;
    INSERT INTO main_group(name_g) VALUES ('عام 1');
END
|
DELIMITER ;

/*------------------------------------ truncateTableOthers -8 tables ------------------------------------ */
DROP procedure if exists truncateTableOthers;

DELIMITER |
create
 procedure truncateTableOthers(IN deleteEmployees tinyint(1),
                                                           IN deleteProcesses tinyint(1),
                                                           IN deleteExpenses tinyint(1), IN deleteUsers tinyint(1))
begin
    SET FOREIGN_KEY_CHECKS = 0;

    IF (deleteUsers) Then
        TRUNCATE table users;
        TRUNCATE table user_permission;
        INSERT INTO users(id, user_name, user_pass, user_available) VALUES (1, 'admin', 'admin', 1);

    End IF;

    if (deleteEmployees) THEN
        TRUNCATE table employees;
        INSERT INTO employees (column_name, birth_date, hire_date, salary, job)
        VALUES ('بيع مباشر', CURRENT_DATE(), CURRENT_DATE(), 0, 4);
        TRUNCATE table treasury_deposit_expenses;
        TRUNCATE table treasury_transfers;
        TRUNCATE table treasury;
        INSERT INTO treasury(t_name, amount)
        VALUES ('الخزينة الرئيسية', 0);

        TRUNCATE table targeted_sales;

    End IF;

    IF (deleteExpenses) Then
        TRUNCATE table expenses_details;
        TRUNCATE table expense_salary;
    End IF;

    if (deleteProcesses) THEN
        TRUNCATE table audit_log;
    End IF;

    SET FOREIGN_KEY_CHECKS = 1;
END
|
DELIMITER ;

/*------------------------------------ delete all ------------------------------------ */
# CALL truncateTableSales(true, true, true, true);
# CALL truncateTablePurchase(true, true, true, true);
# CALL truncateTableOthers(true, true, true, true);
# CALL truncateTableItems(true, true, true, true);

/*------------------------------------ table not truncate ------------------------------------ */
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'account_system_db'
  AND TABLE_TYPE = 'BASE TABLE'
  AND table_name NOT IN (
                         'total_sales_re', 'sales_re', 'total_sales', 'sales',
                         'customers_accounts', 'custom', 'total_buy_re', 'purchase_re',
                         'total_buy', 'purchase', 'suppliers_accounts', 'suppliers',
                         'units', 'type_price', 'items', 'items_price',
                         'items_units', 'items_stock', 'stocks', 'stock_transfer',
                         'stock_transfer_list', 'sub_group', 'main_group', 'users',
                         'user_permission', 'employees', 'treasury_deposit_expenses',
                         'treasury_transfers', 'treasury', 'targeted_sales', 'expenses_details',
                         'expense_salary', 'processes_data'
    );
