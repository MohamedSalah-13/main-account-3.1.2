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
                    'buy_price', NEW.buy_price
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
                    'sel_price3', OLD.sel_price3
            ),
            JSON_OBJECT(
                    'id', NEW.id,
                    'barcode', NEW.barcode,
                    'nameItem', NEW.nameItem,
                    'sub_num', NEW.sub_num,
                    'buy_price', NEW.buy_price,
                    'sel_price1', NEW.sel_price1,
                    'sel_price2', NEW.sel_price2,
                    'sel_price3', NEW.sel_price3
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
                    'buy_price', OLD.buy_price
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


-- `after_items_update` copied items.first_balance over warehouse 1's items_stock row on every
-- update of an item - its name, its price, its picture - so the two copies agreed only by it
-- firing, and a write that reached items_stock directly was undone by the next save of the item.
-- V78 dropped the column and the trigger. The DROP is kept so that this file, which is where a
-- trigger is looked for, still says it must not exist.
DROP TRIGGER IF EXISTS after_items_update;

-- items_stock
--
-- `before_items_stock_insert` is dropped and not recreated, the same way V5 replaced
-- `before_items_units_insert` below. It signalled SQLSTATE 45000 for a duplicate
-- (item, stock) - which `items_stock_uk` already refuses, so it enforced nothing the
-- schema did not - and **an error a trigger signals is not suppressed by INSERT
-- IGNORE**, which a duplicate-key error is.
--
-- That difference broke warehouse transfers outright. Giving the destination a row for
-- each item is how the arriving quantity gets something to add itself onto
-- (`StockTransferDao.ensureDestination`), it is written as INSERT IGNORE because most
-- items already have one, and it runs over every item of every transfer. So a transfer
-- into a warehouse that already held the item - the ordinary case, since a new warehouse
-- backfills a row per item and a new item a row per warehouse - failed with "هذه
-- البيانات موجودة بالفعل" before it moved anything. Found by
-- `StockTransferEndToEndAcceptanceTest` on the first run of a transfer against a real
-- database, which is what §11 of docs/warehouse-plan.md said had never happened.
--
-- The DROP stays: an install that ran an older copy of this file still carries it.
DROP TRIGGER IF EXISTS before_items_stock_insert;

-- warehouses, transfers and counts (warehouse plan phase C)
--
-- Who created a warehouse, who moved stock between two of them, and who turned a count
-- sheet into a posted correction. None of it was recorded anywhere: the audit triggers
-- reached items, parties, documents and treasuries, and stopped at the shelf. A transfer
-- and a posted count each move a balance exactly as an invoice does.
--
-- **The line tables are deliberately not audited**, the same decision `items_units` carries
-- above. `StockCountDao.save` deletes and re-inserts every line of a sheet on each save, so
-- a trigger there would write a row per line per keystroke of a count that may run to
-- hundreds of items; `stock_transfer_list` is written once and taken by its header's
-- cascade, which is the header's entry to explain. What a transfer moved is its lines, and
-- they are in `stock_transfer_list` until the transfer is reversed - the log says who and
-- when, and the rows themselves say what.
--
-- A stock count's UPDATE is the one that matters: `status` moving from DRAFT to POSTED is
-- the moment the sheet becomes a correction to every balance on it.

DROP TRIGGER IF EXISTS audit_stocks_insert;
DROP TRIGGER IF EXISTS audit_stocks_update;
DROP TRIGGER IF EXISTS audit_stocks_delete;
DROP TRIGGER IF EXISTS audit_stock_transfer_insert;
DROP TRIGGER IF EXISTS audit_stock_transfer_delete;
DROP TRIGGER IF EXISTS audit_stock_count_insert;
DROP TRIGGER IF EXISTS audit_stock_count_update;
DROP TRIGGER IF EXISTS audit_stock_count_delete;

DELIMITER |
CREATE TRIGGER audit_stock_transfer_insert AFTER INSERT ON stock_transfer FOR EACH ROW
BEGIN
    CALL write_audit_log('stock_transfer', NEW.id, 'INSERT', @app_user_id, NULL,
        JSON_OBJECT('id', NEW.id, 'transfer_date', NEW.transfer_date,
                    'stock_from', NEW.stock_from, 'stock_to', NEW.stock_to,
                    'user_id', NEW.user_id),
        'Stock transfer posted');
END|

CREATE TRIGGER audit_stock_transfer_delete AFTER DELETE ON stock_transfer FOR EACH ROW
BEGIN
    CALL write_audit_log('stock_transfer', OLD.id, 'DELETE', @app_user_id,
        JSON_OBJECT('id', OLD.id, 'transfer_date', OLD.transfer_date,
                    'stock_from', OLD.stock_from, 'stock_to', OLD.stock_to,
                    'user_id', OLD.user_id), NULL,
        'Stock transfer reversed');
END|

CREATE TRIGGER audit_stock_count_insert AFTER INSERT ON stock_count FOR EACH ROW
BEGIN
    CALL write_audit_log('stock_count', NEW.id, 'INSERT', @app_user_id, NULL,
        JSON_OBJECT('id', NEW.id, 'stock_id', NEW.stock_id, 'count_date', NEW.count_date,
                    'status', NEW.status, 'notes', NEW.notes, 'user_id', NEW.user_id),
        'Stock count sheet opened');
END|

CREATE TRIGGER audit_stock_count_update AFTER UPDATE ON stock_count FOR EACH ROW
BEGIN
    CALL write_audit_log('stock_count', NEW.id, 'UPDATE', @app_user_id,
        JSON_OBJECT('id', OLD.id, 'stock_id', OLD.stock_id, 'count_date', OLD.count_date,
                    'status', OLD.status, 'notes', OLD.notes, 'posted_at', OLD.posted_at),
        JSON_OBJECT('id', NEW.id, 'stock_id', NEW.stock_id, 'count_date', NEW.count_date,
                    'status', NEW.status, 'notes', NEW.notes, 'posted_at', NEW.posted_at),
        'Stock count changed');
END|

CREATE TRIGGER audit_stock_count_delete AFTER DELETE ON stock_count FOR EACH ROW
BEGIN
    CALL write_audit_log('stock_count', OLD.id, 'DELETE', @app_user_id,
        JSON_OBJECT('id', OLD.id, 'stock_id', OLD.stock_id, 'count_date', OLD.count_date,
                    'status', OLD.status, 'notes', OLD.notes), NULL,
        'Stock count draft removed');
END|
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
-- KEEP THIS SECTION BELOW EVERYTHING OLDER THAN V72. `DelegateActivityDatabaseAcceptanceTest` builds a
-- V70 schema to upgrade, and cuts this file at the line above: a trigger cannot be created on a table,
-- nor name a column, that does not exist yet, so everything below it must belong to V72 or later - the
-- warehouse section after this one is V77's.
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

-- --------------------------------------------------------------------------------------------
-- warehouses switched off (V77)
--
-- The three `stocks` triggers moved here from the warehouse section above when V77 gave the table
-- `is_active`: they name the column, and the cut above runs everything before the commission
-- section over a V70 schema, which has no such column. Their DROPs stay where they were.
--
-- Switching a warehouse off is the one change to it that matters, and it is the UPDATE's
-- `is_active` pair: who took it out of every picker, and when.

DELIMITER |
CREATE TRIGGER audit_stocks_insert AFTER INSERT ON stocks FOR EACH ROW
BEGIN
    CALL write_audit_log('stocks', NEW.stock_id, 'INSERT', @app_user_id, NULL,
        JSON_OBJECT('stock_id', NEW.stock_id, 'stock_name', NEW.stock_name,
                    'stock_address', NEW.stock_address, 'is_active', NEW.is_active,
                    'user_id', NEW.user_id),
        'Warehouse created');
END|

CREATE TRIGGER audit_stocks_update AFTER UPDATE ON stocks FOR EACH ROW
BEGIN
    CALL write_audit_log('stocks', NEW.stock_id, 'UPDATE', @app_user_id,
        JSON_OBJECT('stock_id', OLD.stock_id, 'stock_name', OLD.stock_name,
                    'stock_address', OLD.stock_address, 'is_active', OLD.is_active,
                    'user_id', OLD.user_id),
        JSON_OBJECT('stock_id', NEW.stock_id, 'stock_name', NEW.stock_name,
                    'stock_address', NEW.stock_address, 'is_active', NEW.is_active,
                    'user_id', NEW.user_id),
        IF(OLD.is_active <> NEW.is_active,
           IF(NEW.is_active = 1, 'Warehouse switched on', 'Warehouse switched off'),
           'Warehouse updated'));
END|

CREATE TRIGGER audit_stocks_delete AFTER DELETE ON stocks FOR EACH ROW
BEGIN
    CALL write_audit_log('stocks', OLD.stock_id, 'DELETE', @app_user_id,
        JSON_OBJECT('stock_id', OLD.stock_id, 'stock_name', OLD.stock_name,
                    'stock_address', OLD.stock_address, 'is_active', OLD.is_active,
                    'user_id', OLD.user_id), NULL,
        'Warehouse deleted');
END|
DELIMITER ;


-- ---------------------------------------------------------------------------------------------
-- a warehouse's opening balance (V78)
--
-- The item audit triggers above recorded items.first_balance, which was a copy; V78 dropped it
-- and the opening lives on the item's row in each warehouse alone. So that is where a change to
-- it is recorded: who entered what a shelf held before anything moved, and who changed it while
-- nothing had. Only an UPDATE, and only when the figure moves. A row is inserted per item per
-- warehouse whenever either is created, and an INSERT trigger would log one row per item for
-- every warehouse made - the decision items_units already carries.

DROP TRIGGER IF EXISTS audit_items_stock_opening;

DELIMITER |
CREATE TRIGGER audit_items_stock_opening AFTER UPDATE ON items_stock FOR EACH ROW
BEGIN
    IF NOT (OLD.first_balance <=> NEW.first_balance) THEN
        CALL write_audit_log('items_stock', NEW.id, 'UPDATE', @app_user_id,
            JSON_OBJECT('item_id', OLD.item_id, 'stock_id', OLD.stock_id,
                        'first_balance', OLD.first_balance),
            JSON_OBJECT('item_id', NEW.item_id, 'stock_id', NEW.stock_id,
                        'first_balance', NEW.first_balance),
            'Opening balance changed');
    END IF;
END|
DELIMITER ;


-- ---------------------------------------------------------------------------------------------
-- currencies and their rates (V80)
--
-- A rate is the one figure here that decides what a foreign amount is worth in the books, and it
-- may be corrected: so who typed it, who changed it from what, and who deleted it are recorded.
-- The currency's own UPDATE names the two changes that matter - which currency the books are in,
-- and a currency taken out of every picker. Last in the file, being the newest: every acceptance
-- test that builds an older schema cuts this file at an earlier marker, and a trigger cannot be
-- created on a table that does not exist yet.

DROP TRIGGER IF EXISTS audit_currency_insert;
DROP TRIGGER IF EXISTS audit_currency_update;
DROP TRIGGER IF EXISTS audit_currency_delete;
DROP TRIGGER IF EXISTS audit_currency_rate_insert;
DROP TRIGGER IF EXISTS audit_currency_rate_update;
DROP TRIGGER IF EXISTS audit_currency_rate_delete;

DELIMITER |
CREATE TRIGGER audit_currency_insert AFTER INSERT ON currency FOR EACH ROW
BEGIN
    CALL write_audit_log('currency', NEW.id, 'INSERT', @app_user_id, NULL,
        JSON_OBJECT('code', NEW.code, 'name', NEW.name, 'symbol', NEW.symbol, 'symbol_latin', NEW.symbol_latin,
                    'decimal_places', NEW.decimal_places, 'is_base', NEW.is_base,
                    'is_active', NEW.is_active, 'user_id', NEW.user_id),
        'Currency created');
END|

CREATE TRIGGER audit_currency_update AFTER UPDATE ON currency FOR EACH ROW
BEGIN
    CALL write_audit_log('currency', NEW.id, 'UPDATE', @app_user_id,
        JSON_OBJECT('code', OLD.code, 'name', OLD.name, 'symbol', OLD.symbol, 'symbol_latin', OLD.symbol_latin,
                    'decimal_places', OLD.decimal_places, 'is_base', OLD.is_base,
                    'is_active', OLD.is_active, 'sort_order', OLD.sort_order),
        JSON_OBJECT('code', NEW.code, 'name', NEW.name, 'symbol', NEW.symbol, 'symbol_latin', NEW.symbol_latin,
                    'decimal_places', NEW.decimal_places, 'is_base', NEW.is_base,
                    'is_active', NEW.is_active, 'sort_order', NEW.sort_order),
        IF(OLD.is_base <> NEW.is_base,
           IF(NEW.is_base = 1, 'Base currency set', 'Base currency cleared'),
           IF(OLD.is_active <> NEW.is_active,
              IF(NEW.is_active = 1, 'Currency switched on', 'Currency switched off'),
              'Currency updated')));
END|

CREATE TRIGGER audit_currency_delete AFTER DELETE ON currency FOR EACH ROW
BEGIN
    CALL write_audit_log('currency', OLD.id, 'DELETE', @app_user_id,
        JSON_OBJECT('code', OLD.code, 'name', OLD.name, 'symbol', OLD.symbol, 'symbol_latin', OLD.symbol_latin,
                    'decimal_places', OLD.decimal_places, 'is_base', OLD.is_base,
                    'is_active', OLD.is_active), NULL,
        'Currency deleted');
END|

CREATE TRIGGER audit_currency_rate_insert AFTER INSERT ON currency_rate FOR EACH ROW
BEGIN
    CALL write_audit_log('currency_rate', NEW.id, 'INSERT', @app_user_id, NULL,
        JSON_OBJECT('currency_id', NEW.currency_id, 'effective_date', NEW.effective_date,
                    'rate', NEW.rate, 'notes', NEW.notes, 'user_id', NEW.user_id),
        'Exchange rate recorded');
END|

CREATE TRIGGER audit_currency_rate_update AFTER UPDATE ON currency_rate FOR EACH ROW
BEGIN
    CALL write_audit_log('currency_rate', NEW.id, 'UPDATE', @app_user_id,
        JSON_OBJECT('currency_id', OLD.currency_id, 'effective_date', OLD.effective_date,
                    'rate', OLD.rate, 'notes', OLD.notes),
        JSON_OBJECT('currency_id', NEW.currency_id, 'effective_date', NEW.effective_date,
                    'rate', NEW.rate, 'notes', NEW.notes),
        'Exchange rate changed');
END|

CREATE TRIGGER audit_currency_rate_delete AFTER DELETE ON currency_rate FOR EACH ROW
BEGIN
    CALL write_audit_log('currency_rate', OLD.id, 'DELETE', @app_user_id,
        JSON_OBJECT('currency_id', OLD.currency_id, 'effective_date', OLD.effective_date,
                    'rate', OLD.rate, 'notes', OLD.notes), NULL,
        'Exchange rate deleted');
END|
DELIMITER ;


-- ---------------------------------------------------------------------------------------------
-- price tiers (V84)
--
-- Tier 1 is the price every item carries, and the one an item missing a price on the customer's
-- tier falls back to (docs/pricing-and-offers-plan.md ق-س٣) - so it can never be switched off. The
-- plan asked for a CHECK on id = 1, which MySQL refuses on an AUTO_INCREMENT column (error 3818), so
-- the rule is here and in PriceTierService. A fill rule may not copy a tier from itself either.
-- Who renamed a tier, switched one off, or changed how one is filled is recorded, since a tier's
-- name is what every price on screen is labelled with. After the currencies, being newer.

DROP TRIGGER IF EXISTS type_price_rules_update;
DROP TRIGGER IF EXISTS audit_type_price_update;

DELIMITER |
CREATE TRIGGER type_price_rules_update BEFORE UPDATE ON type_price FOR EACH ROW
BEGIN
    IF NEW.id = 1 AND NEW.is_active = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'type_price 1 cannot be switched off';
    END IF;
    IF NEW.rule_tier_id IS NOT NULL AND NEW.rule_tier_id = NEW.id THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a price tier cannot be filled from itself';
    END IF;
END|

CREATE TRIGGER audit_type_price_update AFTER UPDATE ON type_price FOR EACH ROW
BEGIN
    CALL write_audit_log('type_price', NEW.id, 'UPDATE', @app_user_id,
        JSON_OBJECT('name', OLD.name, 'is_active', OLD.is_active, 'rule_source', OLD.rule_source,
                    'rule_tier_id', OLD.rule_tier_id, 'rule_percent', OLD.rule_percent,
                    'rule_rounding', OLD.rule_rounding),
        JSON_OBJECT('name', NEW.name, 'is_active', NEW.is_active, 'rule_source', NEW.rule_source,
                    'rule_tier_id', NEW.rule_tier_id, 'rule_percent', NEW.rule_percent,
                    'rule_rounding', NEW.rule_rounding),
        IF(OLD.is_active <> NEW.is_active,
           IF(NEW.is_active = 1, 'Price tier switched on', 'Price tier switched off'),
           'Price tier updated'));
END|
DELIMITER ;


-- ---------------------------------------------------------------------------------------------
-- offers (V85)
--
-- An offer a sale or a return line names is history (docs/pricing-and-offers-plan.md ق-ع٧): its terms
-- - what it gives and to whom - are refused an UPDATE here as they are in OfferService, so a statement
-- from outside the program cannot rewrite what a saved invoice was given. Its name, its notes, its end
-- and its status may still move. Who wrote an offer, switched it on or stopped it, and who deleted a
-- draft, is recorded. After the price tiers, being newer.

DROP TRIGGER IF EXISTS offer_terms_update;
DROP TRIGGER IF EXISTS audit_offer_insert;
DROP TRIGGER IF EXISTS audit_offer_update;
DROP TRIGGER IF EXISTS audit_offer_delete;

DELIMITER |
CREATE TRIGGER offer_terms_update BEFORE UPDATE ON offer FOR EACH ROW
BEGIN
    IF (NOT (OLD.kind <=> NEW.kind) OR NOT (OLD.percent <=> NEW.percent) OR NOT (OLD.amount <=> NEW.amount)
            OR NOT (OLD.offer_price <=> NEW.offer_price) OR NOT (OLD.unit_id <=> NEW.unit_id)
            OR NOT (OLD.starts_on <=> NEW.starts_on) OR NOT (OLD.weekdays <=> NEW.weekdays)
            OR NOT (OLD.priority <=> NEW.priority))
        AND (EXISTS (SELECT 1 FROM sales WHERE offer_id = OLD.id)
            OR EXISTS (SELECT 1 FROM sales_re WHERE offer_id = OLD.id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a used offer keeps its terms: stop it and write another';
    END IF;
END|

CREATE TRIGGER audit_offer_insert AFTER INSERT ON offer FOR EACH ROW
BEGIN
    CALL write_audit_log('offer', NEW.id, 'INSERT', @app_user_id, NULL,
        JSON_OBJECT('name', NEW.name, 'kind', NEW.kind, 'status', NEW.status, 'starts_on', NEW.starts_on,
                    'ends_on', NEW.ends_on, 'weekdays', NEW.weekdays, 'priority', NEW.priority,
                    'percent', NEW.percent, 'amount', NEW.amount, 'offer_price', NEW.offer_price,
                    'unit_id', NEW.unit_id),
        'Offer created');
END|

CREATE TRIGGER audit_offer_update AFTER UPDATE ON offer FOR EACH ROW
BEGIN
    CALL write_audit_log('offer', NEW.id, 'UPDATE', @app_user_id,
        JSON_OBJECT('name', OLD.name, 'status', OLD.status, 'starts_on', OLD.starts_on, 'ends_on', OLD.ends_on,
                    'weekdays', OLD.weekdays, 'priority', OLD.priority, 'percent', OLD.percent,
                    'amount', OLD.amount, 'offer_price', OLD.offer_price, 'unit_id', OLD.unit_id,
                    'notes', OLD.notes),
        JSON_OBJECT('name', NEW.name, 'status', NEW.status, 'starts_on', NEW.starts_on, 'ends_on', NEW.ends_on,
                    'weekdays', NEW.weekdays, 'priority', NEW.priority, 'percent', NEW.percent,
                    'amount', NEW.amount, 'offer_price', NEW.offer_price, 'unit_id', NEW.unit_id,
                    'notes', NEW.notes),
        IF(OLD.status <> NEW.status,
           CASE NEW.status WHEN 'ACTIVE' THEN 'Offer switched on' WHEN 'STOPPED' THEN 'Offer stopped'
                           ELSE 'Offer returned to draft' END,
           'Offer updated'));
END|

CREATE TRIGGER audit_offer_delete AFTER DELETE ON offer FOR EACH ROW
BEGIN
    CALL write_audit_log('offer', OLD.id, 'DELETE', @app_user_id,
        JSON_OBJECT('name', OLD.name, 'kind', OLD.kind, 'status', OLD.status, 'starts_on', OLD.starts_on,
                    'ends_on', OLD.ends_on, 'percent', OLD.percent, 'amount', OLD.amount,
                    'offer_price', OLD.offer_price, 'unit_id', OLD.unit_id),
        NULL,
        'Offer deleted');
END|
DELIMITER ;

-- offers, quantity and gifts (V86)
--
-- The same four triggers as the V85 section, again, with the columns V86 added: a quantity offer's
-- quantities, what a "buy and get" gives, and the two limits are terms too, and a saved invoice's edit is
-- judged by them (docs/pricing-and-offers-plan.md ق-ع٧). Redefined here rather than edited above because a
-- schema at V85 has none of these columns and MySQL refuses a trigger naming one - a test that builds a
-- V85 schema cuts this file at the marker above.

DROP TRIGGER IF EXISTS offer_terms_update;
DROP TRIGGER IF EXISTS audit_offer_insert;
DROP TRIGGER IF EXISTS audit_offer_update;
DROP TRIGGER IF EXISTS audit_offer_delete;

DELIMITER |
CREATE TRIGGER offer_terms_update BEFORE UPDATE ON offer FOR EACH ROW
BEGIN
    IF (NOT (OLD.kind <=> NEW.kind) OR NOT (OLD.percent <=> NEW.percent) OR NOT (OLD.amount <=> NEW.amount)
            OR NOT (OLD.offer_price <=> NEW.offer_price) OR NOT (OLD.unit_id <=> NEW.unit_id)
            OR NOT (OLD.starts_on <=> NEW.starts_on) OR NOT (OLD.weekdays <=> NEW.weekdays)
            OR NOT (OLD.priority <=> NEW.priority) OR NOT (OLD.buy_quantity <=> NEW.buy_quantity)
            OR NOT (OLD.get_quantity <=> NEW.get_quantity) OR NOT (OLD.get_percent <=> NEW.get_percent)
            OR NOT (OLD.max_per_invoice <=> NEW.max_per_invoice)
            OR NOT (OLD.quantity_limit <=> NEW.quantity_limit))
        AND (EXISTS (SELECT 1 FROM sales WHERE offer_id = OLD.id)
            OR EXISTS (SELECT 1 FROM sales_re WHERE offer_id = OLD.id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'a used offer keeps its terms: stop it and write another';
    END IF;
END|

CREATE TRIGGER audit_offer_insert AFTER INSERT ON offer FOR EACH ROW
BEGIN
    CALL write_audit_log('offer', NEW.id, 'INSERT', @app_user_id, NULL,
        JSON_OBJECT('name', NEW.name, 'kind', NEW.kind, 'status', NEW.status, 'starts_on', NEW.starts_on,
                    'ends_on', NEW.ends_on, 'weekdays', NEW.weekdays, 'priority', NEW.priority,
                    'percent', NEW.percent, 'amount', NEW.amount, 'offer_price', NEW.offer_price,
                    'unit_id', NEW.unit_id, 'buy_quantity', NEW.buy_quantity, 'get_quantity', NEW.get_quantity,
                    'get_percent', NEW.get_percent, 'max_per_invoice', NEW.max_per_invoice,
                    'quantity_limit', NEW.quantity_limit),
        'Offer created');
END|

CREATE TRIGGER audit_offer_update AFTER UPDATE ON offer FOR EACH ROW
BEGIN
    CALL write_audit_log('offer', NEW.id, 'UPDATE', @app_user_id,
        JSON_OBJECT('name', OLD.name, 'status', OLD.status, 'starts_on', OLD.starts_on, 'ends_on', OLD.ends_on,
                    'weekdays', OLD.weekdays, 'priority', OLD.priority, 'percent', OLD.percent,
                    'amount', OLD.amount, 'offer_price', OLD.offer_price, 'unit_id', OLD.unit_id,
                    'buy_quantity', OLD.buy_quantity, 'get_quantity', OLD.get_quantity,
                    'get_percent', OLD.get_percent, 'max_per_invoice', OLD.max_per_invoice,
                    'quantity_limit', OLD.quantity_limit, 'notes', OLD.notes),
        JSON_OBJECT('name', NEW.name, 'status', NEW.status, 'starts_on', NEW.starts_on, 'ends_on', NEW.ends_on,
                    'weekdays', NEW.weekdays, 'priority', NEW.priority, 'percent', NEW.percent,
                    'amount', NEW.amount, 'offer_price', NEW.offer_price, 'unit_id', NEW.unit_id,
                    'buy_quantity', NEW.buy_quantity, 'get_quantity', NEW.get_quantity,
                    'get_percent', NEW.get_percent, 'max_per_invoice', NEW.max_per_invoice,
                    'quantity_limit', NEW.quantity_limit, 'notes', NEW.notes),
        IF(OLD.status <> NEW.status,
           CASE NEW.status WHEN 'ACTIVE' THEN 'Offer switched on' WHEN 'STOPPED' THEN 'Offer stopped'
                           ELSE 'Offer returned to draft' END,
           'Offer updated'));
END|

CREATE TRIGGER audit_offer_delete AFTER DELETE ON offer FOR EACH ROW
BEGIN
    CALL write_audit_log('offer', OLD.id, 'DELETE', @app_user_id,
        JSON_OBJECT('name', OLD.name, 'kind', OLD.kind, 'status', OLD.status, 'starts_on', OLD.starts_on,
                    'ends_on', OLD.ends_on, 'percent', OLD.percent, 'amount', OLD.amount,
                    'offer_price', OLD.offer_price, 'unit_id', OLD.unit_id, 'buy_quantity', OLD.buy_quantity,
                    'get_quantity', OLD.get_quantity, 'get_percent', OLD.get_percent),
        NULL,
        'Offer deleted');
END|
DELIMITER ;
