-- stock
DROP TRIGGER IF EXISTS before_stocks_insert;
DROP TRIGGER IF EXISTS before_stocks_delete;

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