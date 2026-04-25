-- treasury
DROP TRIGGER IF EXISTS before_treasury_insert;
DROP TRIGGER IF EXISTS before_treasury_delete;


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
