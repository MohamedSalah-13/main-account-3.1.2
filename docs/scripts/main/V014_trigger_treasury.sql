-- treasury
DROP TRIGGER IF EXISTS before_treasury_insert;
DROP TRIGGER IF EXISTS before_treasury_delete;

DELIMITER |
create trigger before_treasury_insert
    before insert
    on treasury
    for each row
begin
    SET @DEFAULT_USER_ID = 1;
    IF (NEW.user_id IS NULL OR NOT EXISTS (SELECT 1 FROM users WHERE users.id = NEW.user_id)) THEN
        SET NEW.user_id = @DEFAULT_USER_ID;
    END IF;
end;
|
DELIMITER ;

-- deletion is not allowed
DELIMITER |
create trigger before_treasury_delete
    before delete
    on treasury
    for each row
begin
    if (OLD.id = 1) Then
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot delete or update a parent row';
    end if;

end;
|
DELIMITER ;

-- treasury_detail
DROP TRIGGER IF EXISTS after_treasury_deposit_expenses_insert;
DROP TRIGGER IF EXISTS after_treasury_deposit_expenses_delete;
DROP TRIGGER IF EXISTS after_treasury_deposit_expenses_update;
DROP TRIGGER IF EXISTS before_treasury_deposit_expenses_insert;


DELIMITER |
create trigger before_treasury_deposit_expenses_insert
    before insert
    on treasury_deposit_expenses
    for each row
begin
    SET @DEFAULT_USER_ID = 1;
    IF (NEW.user_id IS NULL OR NOT EXISTS (SELECT 1 FROM users WHERE users.id = NEW.user_id)) THEN
        SET NEW.user_id = @DEFAULT_USER_ID;
    END IF;
end;
|
DELIMITER ;

/*----------------------------------------------- delete -----------------------------------------------*/
DELIMITER |
create trigger after_treasury_deposit_expenses_delete
    after delete
    on treasury_deposit_expenses
    for each row
begin
    SET @name =
            JSON_ARRAY(OLD.id, OLD.statement, OLD.date_inter, OLD.amount, OLD.description_data, OLD.deposit_or_expenses,
                       OLD.treasury_id, OLD.date_insert, OLD.user_id);
#     call delete_processes_data('TREASURY', OLD.id, @name);
    CALL handle_processes_data(OLD.user_id, upper('DELETE'), upper('TREASURY'), OLD.id, @name);
end;
|
DELIMITER ;

/*----------------------------------------------- insert -----------------------------------------------*/
DELIMITER |
create trigger after_treasury_deposit_expenses_insert
    after insert
    on treasury_deposit_expenses
    for each row
begin
    SET @name =
            JSON_ARRAY(NEW.id, NEW.statement, NEW.date_inter, NEW.amount, NEW.description_data, NEW.deposit_or_expenses,
                       NEW.treasury_id, NEW.date_insert, NEW.user_id);
#     call insert_processes_data('TREASURY', NEW.id, @name);
    CALL handle_processes_data(NEW.user_id, upper('INSERT'), upper('TREASURY'), NEW.id, @name);
end;
|
DELIMITER ;

/*----------------------------------------------- update -----------------------------------------------*/
DELIMITER |
create trigger after_treasury_deposit_expenses_update
    after update
    on treasury_deposit_expenses
    for each row
begin
    SET @name =
            JSON_ARRAY(OLD.id, OLD.statement, OLD.date_inter, OLD.amount, OLD.description_data, OLD.deposit_or_expenses
                , OLD.treasury_id, OLD.date_insert, NEW.user_id);
#     call update_processes_data('TREASURY', NEW.id, @name);
    CALL handle_processes_data(NEW.user_id, upper('UPDATE'), upper('TREASURY'), OLD.id, @name);
end;
|
DELIMITER ;
