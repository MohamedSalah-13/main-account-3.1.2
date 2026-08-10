-- =====================================================================
-- Repeatable - triggers.
--
-- Every trigger is preceded by DROP TRIGGER IF EXISTS. MySQL has no
-- CREATE OR REPLACE TRIGGER, so drop-then-create is the only safe form.
-- 
-- These fire after the V1 seed data is inserted rather than before it, as
-- in the old single-file baseline. That is harmless: after_users_insert is
-- guarded by IF (NEW.id > 1) and the seed only inserts the admin (id = 1),
-- and after_permission_insert copies rows for users where id != 1, of
-- which the seed creates none.
-- 
-- The audit_* triggers on users/custom/suppliers/total_sales/total_buy/
-- treasury stay in V2__audit_triggers.sql - moving an already-applied
-- versioned migration would fail Flyway validation on live clients.
--
-- One consequence: `users` carries two AFTER INSERT triggers, and because
-- this file runs after V2, after_users_insert is now created second and so
-- fires second, where it used to fire first. The two are independent -
-- after_users_insert writes user_permission, audit_users_insert writes
-- audit_log, and neither reads the other's table - so the swap has no
-- effect. FOLLOWS/PRECEDES was deliberately not used here: it would make
-- this file fail outright if run by hand against a schema that has not yet
-- had V2 applied.
-- =====================================================================

DROP TRIGGER IF EXISTS audit_items_insert;
DROP TRIGGER IF EXISTS audit_items_update;
DROP TRIGGER IF EXISTS audit_items_delete;

###############################################
DELIMITER |
CREATE TRIGGER audit_items_insert
    AFTER INSERT ON items
    FOR EACH ROW
BEGIN
    CALL write_audit_log(
            'items',
            NEW.id,
            'INSERT',
            COALESCE(@app_user_id, NEW.user_id, 1),
            NULL,
            JSON_OBJECT(
                    'id', NEW.id,
                    'barcode', NEW.barcode,
                    'nameItem', NEW.nameItem,
                    'buy_price', NEW.buy_price,
                    'first_balance', NEW.first_balance
            ),
            NULL
         );
END;
|
DELIMITER ;

DELIMITER |
CREATE TRIGGER audit_items_update
    AFTER UPDATE ON items
    FOR EACH ROW
BEGIN
    CALL write_audit_log(
            'items',
            NEW.id,
            'UPDATE',
            COALESCE(@app_user_id, NEW.user_id, OLD.user_id, 1),
            JSON_OBJECT(
                    'id', OLD.id,
                    'barcode', OLD.barcode,
                    'nameItem', OLD.nameItem,
                    'buy_price', OLD.buy_price,
                    'first_balance', OLD.first_balance
            ),
            JSON_OBJECT(
                    'id', NEW.id,
                    'barcode', NEW.barcode,
                    'nameItem', NEW.nameItem,
                    'buy_price', NEW.buy_price,
                    'first_balance', NEW.first_balance
            ),
            NULL
         );
END;
|
DELIMITER ;

DELIMITER |
CREATE TRIGGER audit_items_delete
    AFTER DELETE ON items
    FOR EACH ROW
BEGIN
    CALL write_audit_log(
            'items',
            OLD.id,
            'DELETE',
            COALESCE(@app_user_id, OLD.user_id, 1),
            JSON_OBJECT(
                    'id', OLD.id,
                    'barcode', OLD.barcode,
                    'nameItem', OLD.nameItem,
                    'buy_price', OLD.buy_price,
                    'first_balance', OLD.first_balance
            ),
            NULL,
            NULL
         );
END;
|
DELIMITER ;

-- user permission
DROP TRIGGER IF EXISTS after_users_insert;

-- insert permission for new user
DELIMITER |
create trigger after_users_insert
    after insert
    on users
    for each row
begin
    declare maxPermissions int unsigned default (SELECT count(*) FROM permission);
    declare currentPermissionId int unsigned default 1;
    IF (NEW.id > 1) THEN
        while currentPermissionId <= maxPermissions
            do
                set @permissionId = (SELECT p.id FROM permission p WHERE p.id = currentPermissionId);
                insert into user_permission (permission_id, user_id)
                VALUES (@permissionId, NEW.id);
                set currentPermissionId = currentPermissionId + 1;
            end while;
    end if;

end;
|
DELIMITER ;


-- permission
DROP TRIGGER IF EXISTS after_permission_insert;

DELIMITER |
create trigger after_permission_insert
    after insert
    on permission
    for each row
begin
    INSERT INTO user_permission (permission_id, user_id, check_status)
    SELECT NEW.id, users.id, 0
    FROM users
    WHERE users.id != 1;
end;
|
DELIMITER ;


DROP TRIGGER IF EXISTS after_items_update;

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
--
-- `before_items_units_insert` used to reject a second row for the same
-- (item, unit). V5 replaced it with the `items_units_item_unit_uk` unique key,
-- which also covers UPDATE - the trigger never did. Dropping it here as well
-- keeps a database that reruns this file from getting it back.
DROP TRIGGER IF EXISTS before_items_units_insert;
