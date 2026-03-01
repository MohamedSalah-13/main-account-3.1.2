-- stock
DROP TRIGGER IF EXISTS before_stocks_insert;
DROP TRIGGER IF EXISTS before_stocks_delete;

DELIMITER |
create trigger before_stocks_insert
    before insert
    on stocks
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

-- stocks delete
DELIMITER |
create trigger before_stocks_delete
    before delete
    on stocks
    for each row
begin
    if (OLD.stock_id = 1) Then
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot delete or update a parent row';
    end if;

end;
|
DELIMITER ;

-- stock transfer
DROP TRIGGER IF EXISTS after_stock_transfer_insert;
DROP TRIGGER IF EXISTS after_stock_transfer_delete;
DROP TRIGGER IF EXISTS after_stock_transfer_update;
DROP TRIGGER IF EXISTS before_stock_transfer_insert;
DROP PROCEDURE IF EXISTS max_stock_transfer_id;


DELIMITER |
create trigger before_stock_transfer_insert
    before insert
    on stock_transfer
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
create trigger after_stock_transfer_delete
    after delete
    on stock_transfer
    for each row
begin
    SET @name = JSON_ARRAY(OLD.id, OLD.transfer_date, OLD.stock_from, OLD.stock_to, OLD.date_insert, OLD.user_id);
#     call delete_processes_data('STOCK_TRANSFER', OLD.id, @name);
    CALL handle_processes_data(OLD.user_id, upper('DELETE'), upper('STOCK_TRANSFER'), OLD.id, @name);
end;
|
DELIMITER ;

/*----------------------------------------------- insert -----------------------------------------------*/
DELIMITER |
create trigger after_stock_transfer_insert
    after insert
    on stock_transfer
    for each row
begin
    SET @name = JSON_ARRAY(NEW.id, NEW.transfer_date, NEW.stock_from, NEW.stock_to, NEW.date_insert, NEW.user_id);
#     call insert_processes_data('STOCK_TRANSFER', NEW.id, @name);
    CALL handle_processes_data(NEW.user_id, upper('INSERT'), upper('STOCK_TRANSFER'), NEW.id, @name);
end;
|
DELIMITER ;

/*----------------------------------------------- update -----------------------------------------------*/
DELIMITER |
create trigger after_stock_transfer_update
    after update
    on stock_transfer
    for each row
begin
    SET @name = JSON_ARRAY(OLD.id, OLD.transfer_date, OLD.stock_from, OLD.stock_to, OLD.date_insert, NEW.user_id);
#     call update_processes_data('STOCK_TRANSFER', NEW.id, @name);
    CALL handle_processes_data(NEW.user_id, upper('UPDATE'), upper('STOCK_TRANSFER'), OLD.id, @name);
end;
|
DELIMITER ;
/*----------------------------------------------- max_id -----------------------------------------------*/
DELIMITER |
create
    definer = root@localhost procedure max_stock_transfer_id(OUT itemId int)
begin
    SET itemId = (SELECT id
                  from stock_transfer
                  ORDER BY id DESC
                  LIMIT 1);
end;
|
DELIMITER ;