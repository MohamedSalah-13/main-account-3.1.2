-- =====================================================================
-- Repeatable - triggers.
--
-- Every trigger is preceded by DROP TRIGGER IF EXISTS. MySQL has no
-- CREATE OR REPLACE TRIGGER, so drop-then-create is the only safe form.
-- 
-- V11 replaced the old per-user permission-maintenance triggers with RBAC.
-- The obsolete after_users_insert trigger is dropped below; a new permission
-- is copied only to the protected SYSTEM_ADMIN role.
-- 
-- The audit_* triggers on users/custom/suppliers/total_sales/total_buy/
-- treasury stay in V2__audit_triggers.sql - moving an already-applied
-- versioned migration would fail Flyway validation on live clients.
--
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

-- Authorization is catalogue-driven from V12. New users start with no role
-- (deny by default), and catalogue synchronization grants new permissions only
-- to the protected system-administrator role.
DROP TRIGGER IF EXISTS after_users_insert;
DROP TRIGGER IF EXISTS after_permission_insert;


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

-- Shift cash handovers are two immutable facts: cashier declaration, then
-- receipt by a different authenticated user.
DROP TRIGGER IF EXISTS validate_shift_cash_handover_receiver;
DROP TRIGGER IF EXISTS prevent_shift_cash_handover_update;
DROP TRIGGER IF EXISTS prevent_shift_cash_handover_delete;
DROP TRIGGER IF EXISTS prevent_shift_cash_handover_receipt_update;
DROP TRIGGER IF EXISTS prevent_shift_cash_handover_receipt_delete;

DELIMITER |
CREATE TRIGGER validate_shift_cash_handover_receiver
BEFORE INSERT ON shift_cash_handover_receipts FOR EACH ROW
BEGIN
    IF NEW.received_by_user_id =
       (SELECT handed_by_user_id FROM shift_cash_handovers WHERE id=NEW.handover_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A second user must receive the cash handover';
    END IF;
END|

CREATE TRIGGER prevent_shift_cash_handover_update
BEFORE UPDATE ON shift_cash_handovers FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift cash handover is immutable';
END|

CREATE TRIGGER prevent_shift_cash_handover_delete
BEFORE DELETE ON shift_cash_handovers FOR EACH ROW
BEGIN
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift cash handover is immutable';
    END IF;
END|

CREATE TRIGGER prevent_shift_cash_handover_receipt_update
BEFORE UPDATE ON shift_cash_handover_receipts FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift cash handover receipt is immutable';
END|

CREATE TRIGGER prevent_shift_cash_handover_receipt_delete
BEFORE DELETE ON shift_cash_handover_receipts FOR EACH ROW
BEGIN
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift cash handover receipt is immutable';
    END IF;
END|
DELIMITER ;
