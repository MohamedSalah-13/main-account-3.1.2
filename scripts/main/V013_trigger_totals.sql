-- total_buy
DROP TRIGGER IF EXISTS after_total_buy_insert;
DROP TRIGGER IF EXISTS after_total_buy_delete;
DROP TRIGGER IF EXISTS after_total_buy_update;
DROP TRIGGER IF EXISTS before_total_buy_insert;

-- ------------------ insert --------------------
DELIMITER |
create trigger before_total_buy_insert
    before insert
    on total_buy
    for each row
begin
    IF NOT EXISTS (SELECT 1
                   FROM users
                   WHERE users.id = NEW.user_id) THEN
        set NEW.user_id = 1;
    END IF;
end;
|
DELIMITER ;
-- ------------------ insert --------------------
DELIMITER |
create trigger after_total_buy_insert
    after insert
    on total_buy
    for each row
begin
    SET @name =
            JSON_ARRAY(NEW.invoice_number, NEW.sup_code, NEW.invoice_type, NEW.invoice_date, NEW.total, NEW.discount,
                       NEW.paid_up, NEW.stock_id, NEW.treasury_id, NEW.date_insert, NEW.notes,
                       NEW.user_id);
#     call insert_processes_data('TOTAL_BUY', NEW.invoice_number, @name);
    CALL handle_processes_data(NEW.user_id, upper('INSERT'), upper('TOTAL_BUY'), NEW.invoice_number, @name);
end;
|
DELIMITER ;

-- ------------------ delete --------------------
DELIMITER |
create trigger after_total_buy_delete
    after delete
    on total_buy
    for each row
begin
    SET @name =
            JSON_ARRAY(OLD.invoice_number, OLD.sup_code, OLD.invoice_type, OLD.invoice_date, OLD.total, OLD.discount,
                       OLD.paid_up, OLD.stock_id, OLD.treasury_id, OLD.date_insert, OLD.notes,
                       OLD.user_id);
#     call delete_processes_data('TOTAL_BUY', OLD.invoice_number, @name);
    CALL handle_processes_data(OLD.user_id, upper('DELETE'), upper('TOTAL_BUY'), OLD.invoice_number, @name);
    # delete all paid in account
    delete from suppliers_accounts where numberInv = OLD.invoice_number;
end;
|
DELIMITER ;
-- ------------------ update --------------------
DELIMITER |
create trigger after_total_buy_update
    after update
    on total_buy
    for each row
begin
    SET @name =
            JSON_ARRAY(OLD.invoice_number, OLD.sup_code, OLD.invoice_type, OLD.invoice_date, OLD.total, OLD.discount,
                       OLD.paid_up, OLD.stock_id, OLD.treasury_id, OLD.date_insert, OLD.notes,
                       NEW.user_id);
#     call update_processes_data('TOTAL_BUY', NEW.invoice_number, @name);
    CALL handle_processes_data(NEW.user_id, upper('UPDATE'), upper('TOTAL_BUY'), OLD.invoice_number, @name);
end;
|
DELIMITER ;

-- total_buy_re
DROP TRIGGER IF EXISTS after_total_buy_re_insert;
DROP TRIGGER IF EXISTS after_total_buy_re_delete;
DROP TRIGGER IF EXISTS after_total_buy_re_update;
DROP TRIGGER IF EXISTS before_total_buy_re_insert;

DELIMITER |
create trigger before_total_buy_re_insert
    before insert
    on total_buy_re
    for each row
begin
    IF NOT EXISTS (SELECT 1
                   FROM users
                   WHERE users.id = NEW.user_id) THEN
        set NEW.user_id = 1;
    END IF;
end;
|
DELIMITER ;
-- ------------------ delete --------------------
DELIMITER |
create trigger after_total_buy_re_delete
    after delete
    on total_buy_re
    for each row
begin
    SET @name = JSON_ARRAY(OLD.id, OLD.sup_id, OLD.invoice_date, OLD.total, OLD.discount,
                           OLD.paid_to_treasury, OLD.stock_id, OLD.treasury_id, OLD.date_insert, OLD.notes,
                           OLD.user_id);
    CALL handle_processes_data(OLD.user_id, upper('DELETE'), upper('TOTAL_BUY_RETURN'), OLD.id, @name);
end;
|
DELIMITER ;
-- ------------------ insert --------------------
DELIMITER |
create trigger after_total_buy_re_insert
    after insert
    on total_buy_re
    for each row
begin
    SET @name = JSON_ARRAY(NEW.id, NEW.sup_id, NEW.invoice_date, NEW.total, NEW.discount,
                           NEW.paid_to_treasury, NEW.stock_id, NEW.treasury_id, NEW.date_insert, NEW.notes,
                           NEW.user_id);
    CALL handle_processes_data(NEW.user_id, upper('INSERT'), upper('TOTAL_BUY_RETURN'), NEW.id, @name);
end;
|
DELIMITER ;
-- ------------------ update --------------------
DELIMITER |
create trigger after_total_buy_re_update
    after update
    on total_buy_re
    for each row
begin
    SET @name = JSON_ARRAY(OLD.id, OLD.sup_id, OLD.invoice_date, OLD.total, OLD.discount,
                           OLD.paid_to_treasury, OLD.stock_id, OLD.treasury_id, OLD.date_insert, OLD.notes,
                           NEW.user_id);
    CALL handle_processes_data(NEW.user_id, upper('UPDATE'), upper('TOTAL_BUY_RETURN'), OLD.id, @name);
end;
|
DELIMITER ;

-- total_sales
DROP TRIGGER IF EXISTS after_total_sales_insert;
DROP TRIGGER IF EXISTS after_total_sales_delete;
DROP TRIGGER IF EXISTS after_total_sales_update;
DROP TRIGGER IF EXISTS before_total_sales_insert;

DELIMITER |
create trigger before_total_sales_insert
    before insert
    on total_sales
    for each row
begin
    IF NOT EXISTS (SELECT 1
                   FROM users
                   WHERE users.id = NEW.user_id) THEN
        set NEW.user_id = 1;
    END IF;
end;
|
DELIMITER ;

-- ------------------ delete --------------------
DELIMITER |
create trigger after_total_sales_delete
    after delete
    on total_sales
    for each row
begin
    SET @name =
            JSON_ARRAY(OLD.invoice_number, OLD.sup_code, OLD.invoice_type, OLD.invoice_date, OLD.total, OLD.discount,
                       OLD.paid_up, OLD.stock_id, OLD.treasury_id, OLD.date_insert, OLD.notes,
                       OLD.user_id);
#     call delete_processes_data('TOTAL_SALES', OLD.invoice_number, @name);
    CALL handle_processes_data(OLD.user_id, upper('DELETE'), upper('TOTAL_SALES'), OLD.invoice_number, @name);
    # delete all paid in account from total by 3
    delete from customers_accounts where numberInv = OLD.invoice_number;
end;
|
DELIMITER ;
-- ------------------ insert --------------------
DELIMITER |
create trigger after_total_sales_insert
    after insert
    on total_sales
    for each row
begin
    SET @name =
            JSON_ARRAY(NEW.invoice_number, NEW.sup_code, NEW.invoice_type, NEW.invoice_date, NEW.total, NEW.discount,
                       NEW.paid_up, NEW.stock_id, NEW.treasury_id, NEW.date_insert, NEW.notes,
                       NEW.user_id);
#     call insert_processes_data('TOTAL_SALES', NEW.invoice_number, @name);
    CALL handle_processes_data(NEW.user_id, upper('INSERT'), upper('TOTAL_SALES'), NEW.invoice_number, @name);
end;
|
DELIMITER ;
-- ------------------ update --------------------
DELIMITER |
create trigger after_total_sales_update
    after update
    on total_sales
    for each row
begin
    SET @name =
            JSON_ARRAY(OLD.invoice_number, OLD.sup_code, OLD.invoice_type, OLD.invoice_date, OLD.total, OLD.discount,
                       OLD.paid_up, OLD.stock_id, OLD.treasury_id, OLD.date_insert, OLD.notes,
                       NEW.user_id);
#     call update_processes_data('TOTAL_SALES', NEW.invoice_number, @name);
    CALL handle_processes_data(NEW.user_id, upper('UPDATE'), upper('TOTAL_SALES'), OLD.invoice_number, @name);
end;
|
DELIMITER ;

-- total_sales_re
DROP TRIGGER IF EXISTS after_total_sales_re_insert;
DROP TRIGGER IF EXISTS after_total_sales_re_delete;
DROP TRIGGER IF EXISTS after_total_sales_re_update;
DROP TRIGGER IF EXISTS before_total_sales_re_insert;

DELIMITER |
create trigger before_total_sales_re_insert
    before insert
    on total_sales_re
    for each row
begin
    IF NOT EXISTS (SELECT 1
                   FROM users
                   WHERE users.id = NEW.user_id) THEN
        set NEW.user_id = 1;
    END IF;

end;
|
DELIMITER ;

-- ------------------ delete --------------------
DELIMITER |
create trigger after_total_sales_re_delete
    after delete
    on total_sales_re
    for each row
begin
    SET @name = JSON_ARRAY(OLD.id, OLD.sup_id, OLD.invoice_date, OLD.total, OLD.discount,
                           OLD.paid_from_treasury, OLD.stock_id, OLD.treasury_id, OLD.date_insert, OLD.notes,
                           OLD.user_id);
#     call delete_processes_data('TOTAL_SALES_RETURN', OLD.id, @name);
    CALL handle_processes_data(OLD.user_id, upper('DELETE'), upper('TOTAL_SALES_RETURN'), OLD.id, @name);
    # delete all paid in account from total by 4
    delete from customers_accounts where invoice_number_return = OLD.id;
end;
|
DELIMITER ;
-- ------------------ insert --------------------
DELIMITER |
create trigger after_total_sales_re_insert
    after insert
    on total_sales_re
    for each row
begin
    SET @name = JSON_ARRAY(NEW.id, NEW.sup_id, NEW.invoice_date, NEW.total, NEW.discount,
                           NEW.paid_from_treasury, NEW.stock_id, NEW.treasury_id, NEW.date_insert, NEW.notes,
                           NEW.user_id);
    CALL handle_processes_data(NEW.user_id, upper('INSERT'), upper('TOTAL_SALES_RETURN'), NEW.id, @name);
end;
|
DELIMITER ;
-- ------------------ update --------------------
DELIMITER |
create trigger after_total_sales_re_update
    after update
    on total_sales_re
    for each row
begin
    SET @name = JSON_ARRAY(OLD.id, OLD.sup_id, OLD.invoice_date, OLD.total, OLD.discount,
                           OLD.paid_from_treasury, OLD.stock_id, OLD.treasury_id, OLD.date_insert, OLD.notes,
                           NEW.user_id);
    CALL handle_processes_data(NEW.user_id, upper('UPDATE'), upper('TOTAL_SALES_RETURN'), OLD.id, @name);
end;
|
DELIMITER ;
