-- suppliers
DROP TRIGGER IF EXISTS after_suppliers_insert;
DROP TRIGGER IF EXISTS after_suppliers_delete;
DROP TRIGGER IF EXISTS after_suppliers_update;
DROP TRIGGER IF EXISTS before_suppliers_insert;

DELIMITER |
create trigger before_suppliers_insert
    before insert
    on suppliers
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
create trigger after_suppliers_delete
    after delete
    on suppliers
    for each row
begin
    SET @name = JSON_ARRAY(OLD.id, OLD.name, OLD.tel, OLD.notes, OLD.first_balance,
                           OLD.date_insert, OLD.user_id);
#     call delete_processes_data('SUPPLIERS', OLD.id, @name);
    CALL handle_processes_data(OLD.user_id, upper('DELETE'), upper('SUPPLIERS'), OLD.id, @name);
end;
|
DELIMITER ;
/*----------------------------------------------- insert -----------------------------------------------*/
DELIMITER |
create trigger after_suppliers_insert
    after insert
    on suppliers
    for each row
begin
    SET @name = JSON_ARRAY(NEW.id, NEW.name, NEW.tel, NEW.notes, NEW.first_balance,
                           NEW.date_insert, NEW.user_id);
#     call insert_processes_data('SUPPLIERS', NEW.id, @name);
    CALL handle_processes_data(NEW.user_id, upper('INSERT'), upper('SUPPLIERS'), NEW.id, @name);
end;
|
DELIMITER ;
/*----------------------------------------------- update -----------------------------------------------*/
DELIMITER |
create trigger after_suppliers_update
    after update
    on suppliers
    for each row
begin
    SET @name = JSON_ARRAY(OLD.id, OLD.name, OLD.tel, OLD.notes, OLD.first_balance,
                           OLD.date_insert, NEW.user_id);
#     call update_processes_data('SUPPLIERS', NEW.id, @name);
    CALL handle_processes_data(NEW.user_id, upper('UPDATE'), upper('SUPPLIERS'), OLD.id, @name);
end;
|
DELIMITER ;

-- supplier account
DROP TRIGGER IF EXISTS after_supplier_account_insert;
DROP TRIGGER IF EXISTS after_supplier_account_delete;
DROP TRIGGER IF EXISTS after_supplier_account_update;
DROP TRIGGER IF EXISTS before_suppliers_accounts_insert;


DELIMITER |
create trigger before_suppliers_accounts_insert
    before insert
    on suppliers_accounts
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
create trigger after_supplier_account_delete
    after delete
    on suppliers_accounts
    for each row
begin
    SET @name = JSON_ARRAY(OLD.account_num, OLD.account_code, OLD.account_date, OLD.paid, OLD.notes, OLD.treasury_id,
                           OLD.purchase,
                           OLD.numberInv, OLD.date_insert, OLD.user_id);
#     call delete_processes_data('SUPPLIERS_ACCOUNT', OLD.account_num, @name);
    CALL handle_processes_data(OLD.user_id, upper('DELETE'), upper('SUPPLIERS_ACCOUNT'), OLD.account_num, @name);
end;
|
DELIMITER ;
/*----------------------------------------------- insert -----------------------------------------------*/
DELIMITER |
create trigger after_supplier_account_insert
    after insert
    on suppliers_accounts
    for each row
begin
    SET @name = JSON_ARRAY(NEW.account_num, NEW.account_code, NEW.account_date, NEW.paid, NEW.notes, NEW.treasury_id,
                           NEW.purchase,
                           NEW.numberInv, NEW.date_insert, NEW.user_id);
#     call insert_processes_data('SUPPLIERS_ACCOUNT', NEW.account_num, @name);
    CALL handle_processes_data(NEW.user_id, upper('INSERT'), upper('SUPPLIERS_ACCOUNT'), NEW.account_num, @name);
end;
|
DELIMITER ;
/*----------------------------------------------- update -----------------------------------------------*/
DELIMITER |
create trigger after_supplier_account_update
    after update
    on suppliers_accounts
    for each row
begin
    SET @name = JSON_ARRAY(OLD.account_num, OLD.account_code, OLD.account_date, OLD.paid, OLD.notes, OLD.treasury_id,
                           OLD.purchase,
                           OLD.numberInv, OLD.date_insert, NEW.user_id);
#     call update_processes_data('SUPPLIERS_ACCOUNT', NEW.account_num, @name);
    CALL handle_processes_data(NEW.user_id, upper('UPDATE'), upper('SUPPLIERS_ACCOUNT'), OLD.account_num, @name);
end;
|
DELIMITER ;
