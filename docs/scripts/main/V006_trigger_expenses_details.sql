DROP TRIGGER IF EXISTS after_expenses_details_insert;
DROP TRIGGER IF EXISTS after_expenses_details_delete;
DROP TRIGGER IF EXISTS after_expenses_details_update;
DROP TRIGGER IF EXISTS before_expenses_details_insert;

DELIMITER |
create trigger before_expenses_details_insert
    before insert
    on expenses_details
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
create trigger after_expenses_details_delete
    after delete
    on expenses_details
    for each row
begin
    SET @name = JSON_ARRAY(OLD.id, OLD.type_code, OLD.date, OLD.amount, OLD.notes, OLD.emp_id, OLD.treasury_id,
                           OLD.date_insert, OLD.user_id);
#     call delete_processes_data('EXPENSES', OLD.id, @name);
    CALL handle_processes_data(OLD.user_id, upper('delete'), upper('EXPENSES'), OLD.id, @name);
end;
|
DELIMITER ;
/*----------------------------------------------- insert -----------------------------------------------*/
DELIMITER |
create trigger after_expenses_details_insert
    after insert
    on expenses_details
    for each row
begin
    SET @name = JSON_ARRAY(NEW.id, NEW.type_code, NEW.date, NEW.amount, NEW.notes, NEW.emp_id, NEW.treasury_id,
                           NEW.date_insert, NEW.user_id);
#     call insert_processes_data('EXPENSES', NEW.id, @name);
    CALL handle_processes_data(NEW.user_id, upper('INSERT'), upper('EXPENSES'), NEW.id, @name);
    if (NEW.emp_id > 0) then
        insert into expense_salary(employee_id, expenses_details_id) VALUES (NEW.emp_id, NEW.id);
    end if;
end;
|
DELIMITER ;
/*----------------------------------------------- update -----------------------------------------------*/
DELIMITER |
create trigger after_expenses_details_update
    after update
    on expenses_details
    for each row
begin
    SET @name = JSON_ARRAY(OLD.id, OLD.type_code, OLD.date, OLD.amount, OLD.notes, OLD.emp_id, OLD.treasury_id,
                           OLD.date_insert, NEW.user_id);
#     call update_processes_data('EXPENSES', NEW.id, @name);
    CALL handle_processes_data(NEW.user_id, upper('UPDATE'), upper('EXPENSES'), OLD.id, @name);
    if (NEW.emp_id > 0) then
        update expense_salary set employee_id=NEW.emp_id where expenses_details_id = NEW.id;
    end if;
end;
|
DELIMITER ;