
-- customer
DROP TRIGGER IF EXISTS after_custom_insert;
DROP TRIGGER IF EXISTS after_custom_delete;
DROP TRIGGER IF EXISTS after_custom_update;
DROP TRIGGER IF EXISTS before_custom_insert;

DELIMITER |
create trigger before_custom_insert
    before insert
    on custom
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

/*----------------------------------------------- delete -----------------------------------------------*/
DELIMITER |
create trigger after_custom_delete
    after delete
    on custom
    for each row
begin
    SET @name = JSON_ARRAY(OLD.id, OLD.name, OLD.tel, OLD.notes, OLD.limit_num, OLD.first_balance, OLD.price_id,
                           OLD.created_at, OLD.user_id);
#     call delete_processes_data('CUSTOMER', OLD.id, @name);
    CALL handle_processes_data(OLD.user_id, 'DELETE', 'CUSTOMER', OLD.id, @name);
end;
|
DELIMITER ;

/*----------------------------------------------- insert -----------------------------------------------*/
DELIMITER |
create trigger after_custom_insert
    after insert
    on custom
    for each row
begin
    SET @name = JSON_ARRAY(NEW.id, NEW.name, NEW.tel, NEW.notes, NEW.limit_num, NEW.first_balance, NEW.price_id,
                           NEW.created_at, NEW.user_id);
#     call insert_processes_data('CUSTOMER', NEW.id, @name);
    CALL handle_processes_data(NEW.user_id, 'INSERT', 'CUSTOMER', NEW.id, @name);
end;
|
DELIMITER ;

/*----------------------------------------------- update -----------------------------------------------*/
DELIMITER |
create trigger after_custom_update
    after update
    on custom
    for each row
begin
    SET @name = JSON_ARRAY(OLD.id, OLD.name, OLD.tel, OLD.notes, OLD.limit_num, OLD.first_balance, OLD.price_id,
                           NEW.updated_at, NEW.user_id);
#     call update_processes_data('CUSTOMER', NEW.id, @name);
    CALL handle_processes_data(NEW.user_id, 'UPDATE', 'CUSTOMER', OLD.id, @name);
end;
|
DELIMITER ;

-- customer account
DROP TRIGGER IF EXISTS after_custom_account_insert;
DROP TRIGGER IF EXISTS after_custom_account_delete;
DROP TRIGGER IF EXISTS after_custom_account_update;
DROP TRIGGER IF EXISTS before_custom_account_insert;

DELIMITER |
create trigger before_custom_account_insert
    before insert
    on customers_accounts
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

/*----------------------------------------------- delete -----------------------------------------------*/
DELIMITER |
create trigger after_custom_account_delete
    after delete
    on customers_accounts
    for each row

begin
    SET @name = JSON_ARRAY(OLD.account_num, OLD.account_code, OLD.account_date, OLD.paid, OLD.notes, OLD.treasury_id,
                           OLD.purchase,
                           OLD.numberInv, OLD.created_at, OLD.user_id);
#     call delete_processes_data('CUSTOMER_ACC', OLD.account_num, @name);
    CALL handle_processes_data(OLD.user_id, upper('DELETE'), upper('CUSTOMER_ACC'), OLD.account_num, @name);

end;
|
DELIMITER ;

/*----------------------------------------------- insert -----------------------------------------------*/

DELIMITER |
create trigger after_custom_account_insert
    after insert
    on customers_accounts
    for each row
begin
    SET @name = JSON_ARRAY(NEW.account_num, NEW.account_code, NEW.account_date, NEW.paid, NEW.notes, NEW.treasury_id,
                           NEW.purchase,
                           NEW.numberInv, NEW.created_at, NEW.user_id);
#     call insert_processes_data('CUSTOMER_ACC', NEW.account_num, @name);
    CALL handle_processes_data(NEW.user_id, upper('INSERT'), upper('CUSTOMER_ACC'), NEW.account_num, @name);

end;
|
DELIMITER ;

/*----------------------------------------------- update -----------------------------------------------*/
DELIMITER |
create trigger after_custom_account_update
    after update
    on customers_accounts
    for each row
begin
    SET @name = JSON_ARRAY(OLD.account_num, OLD.account_code, OLD.account_date, OLD.paid, OLD.notes, OLD.treasury_id,
                           OLD.purchase,
                           OLD.numberInv, NEW.updated_at, NEW.user_id);
#     call update_processes_data('CUSTOMER_ACC', NEW.account_num, @name);
    CALL handle_processes_data(NEW.user_id, upper('UPDATE'), upper('CUSTOMER_ACC'), OLD.account_num, @name);
end;
|
DELIMITER ;

