-- items
DROP TRIGGER IF EXISTS after_items_update;
DROP PROCEDURE IF EXISTS max_item_id;


/*----------------------------------------------- update -----------------------------------------------*/
DELIMITER |
create trigger after_items_update
    after update
    on items
    for each row
begin
    update items_stock
    set first_balance = NEW.first_balance
    where items_stock.item_id = NEW.id
      and items_stock.stock_id = 1;
end;
|
DELIMITER ;
/*----------------------------------------------- max_id -----------------------------------------------*/
DELIMITER |
create
    definer = root@localhost procedure max_item_id(OUT itemId int)
begin
    SET itemId = (SELECT id
                  from items
                  ORDER BY id DESC
                  LIMIT 1);
end;
|
DELIMITER ;

-- items_stock
DROP TRIGGER IF EXISTS before_items_stock_insert;

DELIMITER |
create trigger before_items_stock_insert
    before insert
    on items_stock
    for each row
begin
    -- Define a constant for the error message
    DECLARE err_msg VARCHAR(255) DEFAULT 'Cannot insert: Duplicate entry stock and item combination';

    -- Check if a matching stock and item combination already exists
    IF EXISTS (SELECT 1
               FROM items_stock
               WHERE items_stock.stock_id = NEW.stock_id
                 AND items_stock.item_id = NEW.item_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = err_msg;
    END IF;
end;
|
DELIMITER ;

-- items_units
DROP TRIGGER IF EXISTS before_items_units_insert;

DELIMITER |
create trigger before_items_units_insert
    before insert
    on items_units
    for each row
begin
    -- Define a constant for the error message
    DECLARE err_msg VARCHAR(255) DEFAULT 'Cannot insert : Duplicate entry combination';

    -- Get the latest item id
#     set NEW.items_id = (SELECT id FROM items ORDER BY id DESC LIMIT 1);

    -- Check if a matching stock and item combination already exists
    IF EXISTS (SELECT 1
               FROM items_units
               WHERE items_units.unit = NEW.unit
                 AND items_units.items_id = NEW.items_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = err_msg;
    END IF;
end;
|
DELIMITER ;


