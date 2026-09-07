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

DROP TRIGGER IF EXISTS prevent_audit_admin_event_update;
DROP TRIGGER IF EXISTS prevent_audit_admin_event_delete;

DELIMITER |
CREATE TRIGGER prevent_audit_admin_event_update
BEFORE UPDATE ON audit_admin_event FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit administration events are immutable';
END|

CREATE TRIGGER prevent_audit_admin_event_delete
BEFORE DELETE ON audit_admin_event FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Audit administration events are immutable';
END|
DELIMITER ;

-- Authorization assignments are security-sensitive data. Application mutations
-- also write auth_audit_log, but these database triggers close the gap for direct
-- SQL and cascading changes, while preserving the same actor/workstation snapshots
-- as the rest of audit_log.
DROP TRIGGER IF EXISTS audit_auth_role_insert;
DROP TRIGGER IF EXISTS audit_auth_role_update;
DROP TRIGGER IF EXISTS audit_auth_role_delete;
DROP TRIGGER IF EXISTS audit_auth_role_permission_insert;
DROP TRIGGER IF EXISTS audit_auth_role_permission_delete;
DROP TRIGGER IF EXISTS audit_auth_user_role_insert;
DROP TRIGGER IF EXISTS audit_auth_user_role_delete;
DROP TRIGGER IF EXISTS audit_auth_role_inheritance_insert;
DROP TRIGGER IF EXISTS audit_auth_role_inheritance_delete;
DROP TRIGGER IF EXISTS audit_auth_override_insert;
DROP TRIGGER IF EXISTS audit_auth_override_update;
DROP TRIGGER IF EXISTS audit_auth_override_delete;

DELIMITER |
CREATE TRIGGER audit_auth_role_insert AFTER INSERT ON auth_role FOR EACH ROW
BEGIN
    CALL write_audit_log('auth_role', NEW.id, 'INSERT', @app_user_id, NULL,
        JSON_OBJECT('id', NEW.id, 'role_code', NEW.role_code, 'role_name', NEW.role_name,
                    'description', NEW.description, 'system_role', NEW.system_role,
                    'active', NEW.active, 'created_by', NEW.created_by),
        'Authorization role created');
END|

CREATE TRIGGER audit_auth_role_update AFTER UPDATE ON auth_role FOR EACH ROW
BEGIN
    CALL write_audit_log('auth_role', NEW.id, 'UPDATE', @app_user_id,
        JSON_OBJECT('id', OLD.id, 'role_code', OLD.role_code, 'role_name', OLD.role_name,
                    'description', OLD.description, 'system_role', OLD.system_role,
                    'active', OLD.active, 'created_by', OLD.created_by),
        JSON_OBJECT('id', NEW.id, 'role_code', NEW.role_code, 'role_name', NEW.role_name,
                    'description', NEW.description, 'system_role', NEW.system_role,
                    'active', NEW.active, 'created_by', NEW.created_by),
        'Authorization role updated');
END|

CREATE TRIGGER audit_auth_role_delete AFTER DELETE ON auth_role FOR EACH ROW
BEGIN
    CALL write_audit_log('auth_role', OLD.id, 'DELETE', @app_user_id,
        JSON_OBJECT('id', OLD.id, 'role_code', OLD.role_code, 'role_name', OLD.role_name,
                    'description', OLD.description, 'system_role', OLD.system_role,
                    'active', OLD.active, 'created_by', OLD.created_by), NULL,
        'Authorization role deleted');
END|

CREATE TRIGGER audit_auth_role_permission_insert AFTER INSERT ON auth_role_permission FOR EACH ROW
BEGIN
    CALL write_audit_log('auth_role_permission', CONCAT(NEW.role_id, ':', NEW.permission_id),
        'INSERT', @app_user_id, NULL,
        JSON_OBJECT('role_id', NEW.role_id, 'permission_id', NEW.permission_id,
                    'granted_by', NEW.granted_by, 'granted_at', NEW.granted_at),
        'Permission granted to role');
END|

CREATE TRIGGER audit_auth_role_permission_delete AFTER DELETE ON auth_role_permission FOR EACH ROW
BEGIN
    CALL write_audit_log('auth_role_permission', CONCAT(OLD.role_id, ':', OLD.permission_id),
        'DELETE', @app_user_id,
        JSON_OBJECT('role_id', OLD.role_id, 'permission_id', OLD.permission_id,
                    'granted_by', OLD.granted_by, 'granted_at', OLD.granted_at), NULL,
        'Permission removed from role');
END|

CREATE TRIGGER audit_auth_user_role_insert AFTER INSERT ON auth_user_role FOR EACH ROW
BEGIN
    CALL write_audit_log('auth_user_role', CONCAT(NEW.user_id, ':', NEW.role_id),
        'INSERT', @app_user_id, NULL,
        JSON_OBJECT('user_id', NEW.user_id, 'role_id', NEW.role_id,
                    'assigned_by', NEW.assigned_by, 'assigned_at', NEW.assigned_at),
        'Role assigned to user');
END|

CREATE TRIGGER audit_auth_user_role_delete AFTER DELETE ON auth_user_role FOR EACH ROW
BEGIN
    CALL write_audit_log('auth_user_role', CONCAT(OLD.user_id, ':', OLD.role_id),
        'DELETE', @app_user_id,
        JSON_OBJECT('user_id', OLD.user_id, 'role_id', OLD.role_id,
                    'assigned_by', OLD.assigned_by, 'assigned_at', OLD.assigned_at), NULL,
        'Role removed from user');
END|

CREATE TRIGGER audit_auth_role_inheritance_insert AFTER INSERT ON auth_role_inheritance FOR EACH ROW
BEGIN
    CALL write_audit_log('auth_role_inheritance', CONCAT(NEW.child_role_id, ':', NEW.parent_role_id),
        'INSERT', @app_user_id, NULL,
        JSON_OBJECT('child_role_id', NEW.child_role_id, 'parent_role_id', NEW.parent_role_id,
                    'assigned_by', NEW.assigned_by, 'assigned_at', NEW.assigned_at),
        'Role inheritance assigned');
END|

CREATE TRIGGER audit_auth_role_inheritance_delete AFTER DELETE ON auth_role_inheritance FOR EACH ROW
BEGIN
    CALL write_audit_log('auth_role_inheritance', CONCAT(OLD.child_role_id, ':', OLD.parent_role_id),
        'DELETE', @app_user_id,
        JSON_OBJECT('child_role_id', OLD.child_role_id, 'parent_role_id', OLD.parent_role_id,
                    'assigned_by', OLD.assigned_by, 'assigned_at', OLD.assigned_at), NULL,
        'Role inheritance removed');
END|

CREATE TRIGGER audit_auth_override_insert AFTER INSERT ON auth_user_permission_override FOR EACH ROW
BEGIN
    CALL write_audit_log('auth_user_permission_override', CONCAT(NEW.user_id, ':', NEW.permission_id),
        'INSERT', @app_user_id, NULL,
        JSON_OBJECT('user_id', NEW.user_id, 'permission_id', NEW.permission_id,
                    'effect', NEW.effect, 'reason', NEW.reason, 'expires_at', NEW.expires_at,
                    'granted_by', NEW.granted_by, 'granted_at', NEW.granted_at),
        'User permission override created');
END|

CREATE TRIGGER audit_auth_override_update AFTER UPDATE ON auth_user_permission_override FOR EACH ROW
BEGIN
    CALL write_audit_log('auth_user_permission_override', CONCAT(NEW.user_id, ':', NEW.permission_id),
        'UPDATE', @app_user_id,
        JSON_OBJECT('user_id', OLD.user_id, 'permission_id', OLD.permission_id,
                    'effect', OLD.effect, 'reason', OLD.reason, 'expires_at', OLD.expires_at,
                    'granted_by', OLD.granted_by, 'granted_at', OLD.granted_at),
        JSON_OBJECT('user_id', NEW.user_id, 'permission_id', NEW.permission_id,
                    'effect', NEW.effect, 'reason', NEW.reason, 'expires_at', NEW.expires_at,
                    'granted_by', NEW.granted_by, 'granted_at', NEW.granted_at),
        'User permission override updated');
END|

CREATE TRIGGER audit_auth_override_delete AFTER DELETE ON auth_user_permission_override FOR EACH ROW
BEGIN
    CALL write_audit_log('auth_user_permission_override', CONCAT(OLD.user_id, ':', OLD.permission_id),
        'DELETE', @app_user_id,
        JSON_OBJECT('user_id', OLD.user_id, 'permission_id', OLD.permission_id,
                    'effect', OLD.effect, 'reason', OLD.reason, 'expires_at', OLD.expires_at,
                    'granted_by', OLD.granted_by, 'granted_at', OLD.granted_at), NULL,
        'User permission override removed');
END|
DELIMITER ;

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
                    'sub_num', OLD.sub_num,
                    'buy_price', OLD.buy_price,
                    'first_balance', OLD.first_balance
            ),
            JSON_OBJECT(
                    'id', NEW.id,
                    'barcode', NEW.barcode,
                    'nameItem', NEW.nameItem,
                    'sub_num', NEW.sub_num,
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
DROP TRIGGER IF EXISTS prevent_shift_cash_variance_adjustment_update;
DROP TRIGGER IF EXISTS prevent_shift_cash_variance_adjustment_delete;
DROP TRIGGER IF EXISTS prevent_shift_handover_open_override_update;
DROP TRIGGER IF EXISTS prevent_shift_handover_open_override_delete;

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

CREATE TRIGGER prevent_shift_cash_variance_adjustment_update
BEFORE UPDATE ON shift_cash_variance_adjustments FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift cash variance adjustment is immutable';
END|

CREATE TRIGGER prevent_shift_cash_variance_adjustment_delete
BEFORE DELETE ON shift_cash_variance_adjustments FOR EACH ROW
BEGIN
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift cash variance adjustment is immutable';
    END IF;
END|

CREATE TRIGGER prevent_shift_handover_open_override_update
BEFORE UPDATE ON shift_cash_handover_open_overrides FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift handover open override is immutable';
END|

CREATE TRIGGER prevent_shift_handover_open_override_delete
BEFORE DELETE ON shift_cash_handover_open_overrides FOR EACH ROW
BEGIN
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift handover open override is immutable';
    END IF;
END|
DELIMITER ;
