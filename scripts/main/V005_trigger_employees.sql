DROP TRIGGER IF EXISTS after_employees_insert;
DROP TRIGGER IF EXISTS after_employees_delete;
DROP TRIGGER IF EXISTS after_employees_update;
DROP TRIGGER IF EXISTS before_employees_insert;

DELIMITER |
create trigger before_employees_insert
    before insert
    on employees
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

DELIMITER |
create trigger after_employees_delete
    after delete
    on employees
    for each row
begin
    SET @name = JSON_ARRAY(OLD.id, OLD.column_name, OLD.birth_date, OLD.hire_date, OLD.salary, OLD.email, OLD.tel,
                           OLD.address, OLD.job, OLD.date_insert, OLD.user_id);
#     call delete_processes_data('EMPLOYEES', OLD.id, @name);
    CALL handle_processes_data(OLD.user_id, upper('delete'), upper('EMPLOYEES'), OLD.id, @name);
end;
|
DELIMITER ;

DELIMITER |
create trigger after_employees_insert
    after insert
    on employees
    for each row
begin
    SET @name = JSON_ARRAY(NEW.id, NEW.column_name, NEW.birth_date, NEW.hire_date, NEW.salary, NEW.email, NEW.tel,
                           NEW.address, NEW.job, NEW.date_insert, NEW.user_id);
#     call insert_processes_data('EMPLOYEES', NEW.id, @name);
    CALL handle_processes_data(NEW.user_id, upper('insert'), upper('EMPLOYEES'), NEW.id, @name);
end;
|
DELIMITER ;

DELIMITER |
create trigger after_employees_update
    after update
    on employees
    for each row
begin
    SET @name = JSON_ARRAY(OLD.id, OLD.column_name, OLD.birth_date, OLD.hire_date, OLD.salary, OLD.email, OLD.tel,
                           OLD.address, OLD.job, OLD.date_insert, NEW.user_id);
#     call update_processes_data('EMPLOYEES', NEW.id, @name);
    CALL handle_processes_data(NEW.user_id, upper('update'), upper('EMPLOYEES'), OLD.id, @name);
end;
|
DELIMITER ;