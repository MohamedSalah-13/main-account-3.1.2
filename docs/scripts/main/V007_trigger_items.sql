
-- items
DROP TRIGGER IF EXISTS after_items_insert;
DROP TRIGGER IF EXISTS after_items_delete;
DROP TRIGGER IF EXISTS after_items_update;
DROP PROCEDURE IF EXISTS max_item_id;
DROP TRIGGER IF EXISTS before_items_insert;

DELIMITER |
create trigger before_items_insert
    before insert
    on items
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
create trigger after_items_delete
    after delete
    on items
    for each row
begin
    SET @name =
            JSON_ARRAY(OLD.id, OLD.barcode, OLD.nameItem, OLD.sub_num, OLD.buy_price, OLD.unit_id, OLD.mini_quantity,
                       OLD.first_balance, OLD.user_id);
#     call delete_processes_data('ITEMS', OLD.id, @name);
    CALL handle_processes_data(OLD.user_id, upper('DELETE'), upper('ITEMS'), OLD.id, @name);
end;
|
DELIMITER ;
/*----------------------------------------------- insert -----------------------------------------------*/
DELIMITER |
create trigger after_items_insert
    after insert
    on items
    for each row
begin
    SET @name =
            JSON_ARRAY(NEW.id, NEW.barcode, NEW.nameItem, NEW.sub_num, NEW.buy_price, NEW.unit_id, NEW.mini_quantity,
                       NEW.first_balance, NEW.user_id, NEW.created_at, NEW.updated_at);
#     call insert_processes_data('ITEMS', NEW.id, @name);
    CALL handle_processes_data(NEW.user_id, upper('INSERT'), upper('ITEMS'), NEW.id, @name);
    insert into items_stock(item_id, stock_id, first_balance) VALUES (NEW.id, 1, NEW.first_balance);
end;
|
DELIMITER ;
/*----------------------------------------------- update -----------------------------------------------*/
DELIMITER |
create trigger after_items_update
    after update
    on items
    for each row
begin
    #     call get_name(@name, NEW.id, NEW.nameItem, NEW.buy_price);
    SET @name =
            JSON_ARRAY(OLD.id, OLD.barcode, OLD.nameItem, OLD.sub_num, OLD.buy_price, OLD.unit_id, OLD.mini_quantity,
                       OLD.first_balance, NEW.user_id, NEW.updated_at);
#     call update_processes_data('ITEMS', NEW.id, @name);
    CALL handle_processes_data(NEW.user_id, upper('UPDATE'), upper('ITEMS'), OLD.id, @name);
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
    set NEW.items_id=(SELECT id FROM items ORDER BY id DESC LIMIT 1);

    -- Check if a matching stock and item combination already exists
    IF EXISTS (SELECT 1
               FROM items_units
               WHERE items_units.unit = NEW.unit
                 AND items_units.items_id = NEW.items_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = err_msg;
    END IF;

    SET @DEFAULT_USER_ID = 1;
    IF (NEW.user_id IS NULL OR NOT EXISTS (SELECT 1 FROM users WHERE users.id = NEW.user_id)) THEN
        SET NEW.user_id = @DEFAULT_USER_ID;
    END IF;
end;
|
DELIMITER ;

-- units
DROP TRIGGER IF EXISTS before_units_insert;
DROP TRIGGER IF EXISTS before_units_delete;

/*----------------------------------------------- before insert -----------------------------------------------*/

DELIMITER |
create trigger before_units_insert
    before insert
    on units
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

-- ----------------------------------------------- before delete -----------------------------------------------
DELIMITER |
create trigger before_units_delete
    before delete
    on units
    for each row
begin
    if (OLD.unit_id = 1 OR OLD.unit_id = 2) Then
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot delete or update a parent row id 1 , 2 ';
    end if;

end;
|
DELIMITER ;

-- items_package
DROP TRIGGER IF EXISTS before_items_package_insert;
DELIMITER |
create trigger before_items_package_insert
    before insert
    on items_package
    for each row
begin
    -- Get the latest item id
    set NEW.package_id=(SELECT id FROM items ORDER BY id DESC LIMIT 1);
end;
|
DELIMITER ;