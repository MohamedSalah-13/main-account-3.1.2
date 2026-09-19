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
                    'sel_price1', OLD.sel_price1,
                    'sel_price2', OLD.sel_price2,
                    'sel_price3', OLD.sel_price3,
                    'first_balance', OLD.first_balance
            ),
            JSON_OBJECT(
                    'id', NEW.id,
                    'barcode', NEW.barcode,
                    'nameItem', NEW.nameItem,
                    'sub_num', NEW.sub_num,
                    'buy_price', NEW.buy_price,
                    'sel_price1', NEW.sel_price1,
                    'sel_price2', NEW.sel_price2,
                    'sel_price3', NEW.sel_price3,
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

-- A unit's own prices had no history at all: the audit triggers covered `items`
-- and not this table, so a carton repriced - or a hundred of them made automatic
-- from the unit prices screen in one save - left no trace of who did it or what
-- the price had been.
--
-- UPDATE only, deliberately. `ItemsDao.saveUnits` replaces an item's unit rows
-- wholesale on every save of the item screen, so INSERT and DELETE triggers would
-- log every unit of every item each time anybody saved its name - rows that record
-- nothing having changed. The in-place writes are the price edits (and a merge
-- repointing a unit), and the IF skips an UPDATE that moved none of the figures.
DROP TRIGGER IF EXISTS audit_items_units_update;

DELIMITER |
CREATE TRIGGER audit_items_units_update
    AFTER UPDATE ON items_units
    FOR EACH ROW
BEGIN
    IF NOT (OLD.items_id <=> NEW.items_id AND OLD.unit <=> NEW.unit
            AND OLD.quantity <=> NEW.quantity AND OLD.buy_price <=> NEW.buy_price
            AND OLD.sel_price <=> NEW.sel_price AND OLD.sel_price2 <=> NEW.sel_price2
            AND OLD.sel_price3 <=> NEW.sel_price3) THEN
        CALL write_audit_log(
                'items_units',
                NEW.id,
                'UPDATE',
                COALESCE(@app_user_id, NEW.user_id, OLD.user_id, 1),
                JSON_OBJECT(
                        'items_id', OLD.items_id,
                        'unit', OLD.unit,
                        'quantity', OLD.quantity,
                        'buy_price', OLD.buy_price,
                        'sel_price', OLD.sel_price,
                        'sel_price2', OLD.sel_price2,
                        'sel_price3', OLD.sel_price3
                ),
                JSON_OBJECT(
                        'items_id', NEW.items_id,
                        'unit', NEW.unit,
                        'quantity', NEW.quantity,
                        'buy_price', NEW.buy_price,
                        'sel_price', NEW.sel_price,
                        'sel_price2', NEW.sel_price2,
                        'sel_price3', NEW.sel_price3
                ),
                NULL
             );
    END IF;
END;
|
DELIMITER ;

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
DROP TRIGGER IF EXISTS prevent_shift_variance_settlement_request_update;
DROP TRIGGER IF EXISTS prevent_shift_variance_settlement_request_delete;
DROP TRIGGER IF EXISTS prevent_shift_employee_shortage_charge_update;
DROP TRIGGER IF EXISTS prevent_shift_employee_shortage_charge_delete;

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

-- A payroll line is editable only while its run is a draft. The guard is on the run's
-- status rather than absolute, because a draft is meant to be corrected - what must not
-- move is a run somebody has approved, since approval is what wrote the entitlements into
-- the employees' ledgers.
--
-- The DELETE guard takes the @app_bulk_wipe escape and **the UPDATE guard does not**, which
-- is the distinction V43 missed when it assumed the two were one and failed on a database
-- holding a closed shift (see CLAUDE.md, "Shifts"). A wipe may take these rows; nothing may
-- change one.
DROP TRIGGER IF EXISTS prevent_payroll_line_update_after_approval;
DROP TRIGGER IF EXISTS prevent_payroll_line_delete_after_approval;

DELIMITER |
CREATE TRIGGER prevent_payroll_line_update_after_approval
BEFORE UPDATE ON payroll_line FOR EACH ROW
BEGIN
    DECLARE run_status VARCHAR(20);
    SELECT status INTO run_status FROM payroll_run WHERE id = OLD.payroll_run_id;
    IF run_status IS NOT NULL AND run_status <> 'DRAFT' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A payroll line is frozen once its run leaves DRAFT';
    END IF;
END|

CREATE TRIGGER prevent_shift_variance_settlement_request_update
BEFORE UPDATE ON shift_variance_settlement_requests FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift variance settlement request is immutable';
END|

CREATE TRIGGER prevent_shift_variance_settlement_request_delete
BEFORE DELETE ON shift_variance_settlement_requests FOR EACH ROW
BEGIN
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift variance settlement request is immutable';
    END IF;
END|

CREATE TRIGGER prevent_shift_employee_shortage_charge_update
BEFORE UPDATE ON shift_employee_shortage_charges FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift employee shortage charge is immutable';
END|

CREATE TRIGGER prevent_shift_employee_shortage_charge_delete
BEFORE DELETE ON shift_employee_shortage_charges FOR EACH ROW
BEGIN
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift employee shortage charge is immutable';
    END IF;
END|

CREATE TRIGGER prevent_payroll_line_delete_after_approval
BEFORE DELETE ON payroll_line FOR EACH ROW
BEGIN
    DECLARE run_status VARCHAR(20);
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SELECT status INTO run_status FROM payroll_run WHERE id = OLD.payroll_run_id;
        IF run_status IS NOT NULL AND run_status <> 'DRAFT' THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'A payroll line is frozen once its run leaves DRAFT';
        END IF;
    END IF;
END|
DELIMITER ;

-- expenses_details and expenses (V64)
--
-- An expense had a DELETE trigger (V7) and nothing else, so changing one from 500 to 50 left
-- no trace outside the shift cash journal - and that journal writes nothing while shifts are
-- DISABLED, which is the default. The headings had no trigger at all. docs/expenses-plan.md ع-٨.
--
-- The V7 delete trigger is replaced here rather than beside it: it recorded neither the date nor
-- the employee, and a deleted expense whose date is gone cannot be placed in the month it moved.
DROP TRIGGER IF EXISTS audit_expenses_details_insert;
DROP TRIGGER IF EXISTS audit_expenses_details_update;
DROP TRIGGER IF EXISTS audit_expenses_details_delete;
DROP TRIGGER IF EXISTS audit_expenses_insert;
DROP TRIGGER IF EXISTS audit_expenses_update;
DROP TRIGGER IF EXISTS audit_expenses_delete;

DELIMITER |
CREATE TRIGGER audit_expenses_details_insert
    AFTER INSERT ON expenses_details
    FOR EACH ROW
BEGIN
    CALL write_audit_log('expenses_details', NEW.id, 'INSERT', COALESCE(@app_user_id, NEW.user_id, 1),
                         NULL,
                         JSON_OBJECT('type_code', NEW.type_code, 'date', NEW.date, 'amount', NEW.amount,
                                     'treasury_id', NEW.treasury_id, 'emp_id', NEW.emp_id,
                                     'payee', NEW.payee, 'reference_no', NEW.reference_no,
                                     'notes', NEW.notes),
                         NULL);
END|

CREATE TRIGGER audit_expenses_details_update
    AFTER UPDATE ON expenses_details
    FOR EACH ROW
BEGIN
    IF NOT (OLD.type_code <=> NEW.type_code AND OLD.date <=> NEW.date AND OLD.amount <=> NEW.amount
            AND OLD.treasury_id <=> NEW.treasury_id AND OLD.emp_id <=> NEW.emp_id
            AND OLD.payee <=> NEW.payee AND OLD.reference_no <=> NEW.reference_no
            AND OLD.notes <=> NEW.notes) THEN
        CALL write_audit_log('expenses_details', NEW.id, 'UPDATE',
                             COALESCE(@app_user_id, NEW.user_id, OLD.user_id, 1),
                             JSON_OBJECT('type_code', OLD.type_code, 'date', OLD.date, 'amount', OLD.amount,
                                         'treasury_id', OLD.treasury_id, 'emp_id', OLD.emp_id,
                                         'payee', OLD.payee, 'reference_no', OLD.reference_no,
                                         'notes', OLD.notes),
                             JSON_OBJECT('type_code', NEW.type_code, 'date', NEW.date, 'amount', NEW.amount,
                                         'treasury_id', NEW.treasury_id, 'emp_id', NEW.emp_id,
                                         'payee', NEW.payee, 'reference_no', NEW.reference_no,
                                         'notes', NEW.notes),
                             NULL);
    END IF;
END|

CREATE TRIGGER audit_expenses_details_delete
    AFTER DELETE ON expenses_details
    FOR EACH ROW
BEGIN
    CALL write_audit_log('expenses_details', OLD.id, 'DELETE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'type_code', OLD.type_code, 'date', OLD.date,
                                     'amount', OLD.amount, 'treasury_id', OLD.treasury_id,
                                     'emp_id', OLD.emp_id, 'payee', OLD.payee,
                                     'reference_no', OLD.reference_no, 'notes', OLD.notes),
                         NULL, NULL);
END|

CREATE TRIGGER audit_expenses_insert
    AFTER INSERT ON expenses
    FOR EACH ROW
BEGIN
    CALL write_audit_log('expenses', NEW.id, 'INSERT', COALESCE(@app_user_id, NEW.user_id, 1),
                         NULL,
                         JSON_OBJECT('expenses_name', NEW.expenses_name, 'parent_id', NEW.parent_id,
                                     'is_active', NEW.is_active, 'employee_payment', NEW.employee_payment,
                                     'system_key', NEW.system_key),
                         NULL);
END|

CREATE TRIGGER audit_expenses_update
    AFTER UPDATE ON expenses
    FOR EACH ROW
BEGIN
    IF NOT (OLD.expenses_name <=> NEW.expenses_name AND OLD.parent_id <=> NEW.parent_id
            AND OLD.is_active <=> NEW.is_active AND OLD.employee_payment <=> NEW.employee_payment
            AND OLD.system_key <=> NEW.system_key) THEN
        CALL write_audit_log('expenses', NEW.id, 'UPDATE', COALESCE(@app_user_id, NEW.user_id, 1),
                             JSON_OBJECT('expenses_name', OLD.expenses_name, 'parent_id', OLD.parent_id,
                                         'is_active', OLD.is_active,
                                         'employee_payment', OLD.employee_payment,
                                         'system_key', OLD.system_key),
                             JSON_OBJECT('expenses_name', NEW.expenses_name, 'parent_id', NEW.parent_id,
                                         'is_active', NEW.is_active,
                                         'employee_payment', NEW.employee_payment,
                                         'system_key', NEW.system_key),
                             NULL);
    END IF;
END|

CREATE TRIGGER audit_expenses_delete
    AFTER DELETE ON expenses
    FOR EACH ROW
BEGIN
    CALL write_audit_log('expenses', OLD.id, 'DELETE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('expenses_name', OLD.expenses_name, 'parent_id', OLD.parent_id,
                                     'is_active', OLD.is_active, 'employee_payment', OLD.employee_payment,
                                     'system_key', OLD.system_key),
                         NULL, NULL);
END|
DELIMITER ;

-- commission run (V72)
--
-- KEEP THIS SECTION LAST. `DelegateActivityDatabaseAcceptanceTest` builds a V70 schema to upgrade, and
-- cuts this file at the line above: a trigger cannot be created on a table that does not exist yet, so
-- everything below it must belong to V72 or later.
--
-- A commission line and a posting are facts from the moment they are written - the run is born
-- approved, there is no draft - so neither is ever updated, and neither is deleted outside a wipe.
-- The DELETE guards take the @app_bulk_wipe escape and **the UPDATE guards do not** (see "Shifts" in
-- CLAUDE.md, and V43, which assumed they were one).
--
-- The run itself may change in exactly one way: APPROVED -> CANCELLED, and not even that once
-- anything of it has been posted - a posted commission is an entitlement in somebody's account, and
-- cancelling its run would orphan it. The period and who approved it never move.
DROP TRIGGER IF EXISTS prevent_commission_line_update;
DROP TRIGGER IF EXISTS prevent_commission_line_delete;
DROP TRIGGER IF EXISTS prevent_commission_posting_update;
DROP TRIGGER IF EXISTS prevent_commission_posting_delete;
DROP TRIGGER IF EXISTS guard_commission_run_update;
DROP TRIGGER IF EXISTS prevent_commission_run_delete;

DELIMITER |
CREATE TRIGGER prevent_commission_line_update
BEFORE UPDATE ON commission_line FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A commission line is immutable';
END|

CREATE TRIGGER prevent_commission_line_delete
BEFORE DELETE ON commission_line FOR EACH ROW
BEGIN
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A commission line is immutable';
    END IF;
END|

CREATE TRIGGER prevent_commission_posting_update
BEFORE UPDATE ON commission_posting FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A commission posting is immutable';
END|

CREATE TRIGGER prevent_commission_posting_delete
BEFORE DELETE ON commission_posting FOR EACH ROW
BEGIN
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A commission posting is immutable';
    END IF;
END|

CREATE TRIGGER guard_commission_run_update
BEFORE UPDATE ON commission_run FOR EACH ROW
BEGIN
    DECLARE posted INT;
    IF NOT (OLD.status = 'APPROVED' AND NEW.status = 'CANCELLED')
        OR NEW.period_year <> OLD.period_year OR NEW.period_month <> OLD.period_month
        OR NEW.user_id <> OLD.user_id OR NEW.approved_at <> OLD.approved_at THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A commission run may only move from APPROVED to CANCELLED';
    END IF;
    SELECT COUNT(*) INTO posted
    FROM commission_posting p
             JOIN commission_line l ON l.id = p.line_id
    WHERE l.run_id = OLD.id;
    IF posted > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A commission run with a posted line cannot be cancelled';
    END IF;
END|

CREATE TRIGGER prevent_commission_run_delete
BEFORE DELETE ON commission_run FOR EACH ROW
BEGIN
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A commission run is immutable';
    END IF;
END|
DELIMITER ;
