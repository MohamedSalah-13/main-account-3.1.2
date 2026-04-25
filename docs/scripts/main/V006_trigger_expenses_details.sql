DROP TRIGGER IF EXISTS after_expenses_details_insert;
DROP TRIGGER IF EXISTS after_expenses_details_delete;
DROP TRIGGER IF EXISTS after_expenses_details_update;
DROP TRIGGER IF EXISTS before_expenses_details_insert;

/*----------------------------------------------- insert -----------------------------------------------*/
DELIMITER |
create trigger after_expenses_details_insert
    after insert
    on expenses_details
    for each row
begin
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
    if (NEW.emp_id > 0) then
        update expense_salary set employee_id=NEW.emp_id where expenses_details_id = NEW.id;
    end if;
end;
|
DELIMITER ;