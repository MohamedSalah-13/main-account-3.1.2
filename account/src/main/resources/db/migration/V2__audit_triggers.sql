-- =====================================================================
-- V2 - Audit triggers for the remaining business tables.
--
-- V1 only wires the audit log up to `items`. This adds the same
-- INSERT/UPDATE/DELETE triple for users, custom, suppliers, total_sales,
-- total_buy and treasury - 18 triggers, all routed through the
-- write_audit_log procedure that V1 already created.
--
-- Each is dropped first: some installs had this bundle applied by hand via
-- RunAllSqlScripts.bat before the migration engine existed, and CREATE TRIGGER
-- is not idempotent.
-- =====================================================================

DROP TRIGGER IF EXISTS audit_users_insert;
DROP TRIGGER IF EXISTS audit_users_update;
DROP TRIGGER IF EXISTS audit_users_delete;
DROP TRIGGER IF EXISTS audit_custom_insert;
DROP TRIGGER IF EXISTS audit_custom_update;
DROP TRIGGER IF EXISTS audit_custom_delete;
DROP TRIGGER IF EXISTS audit_suppliers_insert;
DROP TRIGGER IF EXISTS audit_suppliers_update;
DROP TRIGGER IF EXISTS audit_suppliers_delete;
DROP TRIGGER IF EXISTS audit_total_sales_insert;
DROP TRIGGER IF EXISTS audit_total_sales_update;
DROP TRIGGER IF EXISTS audit_total_sales_delete;
DROP TRIGGER IF EXISTS audit_total_buy_insert;
DROP TRIGGER IF EXISTS audit_total_buy_update;
DROP TRIGGER IF EXISTS audit_total_buy_delete;
DROP TRIGGER IF EXISTS audit_treasury_insert;
DROP TRIGGER IF EXISTS audit_treasury_update;
DROP TRIGGER IF EXISTS audit_treasury_delete;

-- =====================================================================
-- 1. جدول المستخدمين (Users)
-- =====================================================================
DELIMITER |
CREATE TRIGGER audit_users_insert AFTER INSERT ON users FOR EACH ROW
BEGIN
    CALL write_audit_log('users', NEW.id, 'INSERT', COALESCE(@app_user_id, 1), NULL,
                         JSON_OBJECT('id', NEW.id, 'user_name', NEW.user_name, 'user_activity', NEW.user_activity, 'user_available', NEW.user_available), NULL);
END;
|
CREATE TRIGGER audit_users_update AFTER UPDATE ON users FOR EACH ROW
BEGIN
    CALL write_audit_log('users', NEW.id, 'UPDATE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'user_name', OLD.user_name, 'user_activity', OLD.user_activity, 'user_available', OLD.user_available),
                         JSON_OBJECT('id', NEW.id, 'user_name', NEW.user_name, 'user_activity', NEW.user_activity, 'user_available', NEW.user_available), NULL);
END;
|
CREATE TRIGGER audit_users_delete AFTER DELETE ON users FOR EACH ROW
BEGIN
    CALL write_audit_log('users', OLD.id, 'DELETE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'user_name', OLD.user_name, 'user_activity', OLD.user_activity), NULL, NULL);
END;
|
DELIMITER ;

-- =====================================================================
-- 2. جدول العملاء (Custom)
-- =====================================================================
DELIMITER |
CREATE TRIGGER audit_custom_insert AFTER INSERT ON custom FOR EACH ROW
BEGIN
    CALL write_audit_log('custom', NEW.id, 'INSERT', COALESCE(@app_user_id, NEW.user_id, 1), NULL,
                         JSON_OBJECT('id', NEW.id, 'name', NEW.name, 'limit_num', NEW.limit_num, 'first_balance', NEW.first_balance), NULL);
END;
|
CREATE TRIGGER audit_custom_update AFTER UPDATE ON custom FOR EACH ROW
BEGIN
    CALL write_audit_log('custom', NEW.id, 'UPDATE', COALESCE(@app_user_id, NEW.user_id, OLD.user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'name', OLD.name, 'limit_num', OLD.limit_num, 'first_balance', OLD.first_balance),
                         JSON_OBJECT('id', NEW.id, 'name', NEW.name, 'limit_num', NEW.limit_num, 'first_balance', NEW.first_balance), NULL);
END;
|
CREATE TRIGGER audit_custom_delete AFTER DELETE ON custom FOR EACH ROW
BEGIN
    CALL write_audit_log('custom', OLD.id, 'DELETE', COALESCE(@app_user_id, OLD.user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'name', OLD.name, 'first_balance', OLD.first_balance), NULL, NULL);
END;
|
DELIMITER ;

-- =====================================================================
-- 3. جدول الموردين (Suppliers)
-- =====================================================================
DELIMITER |
CREATE TRIGGER audit_suppliers_insert AFTER INSERT ON suppliers FOR EACH ROW
BEGIN
    CALL write_audit_log('suppliers', NEW.id, 'INSERT', COALESCE(@app_user_id, NEW.user_id, 1), NULL,
                         JSON_OBJECT('id', NEW.id, 'name', NEW.name, 'first_balance', NEW.first_balance), NULL);
END;
|
CREATE TRIGGER audit_suppliers_update AFTER UPDATE ON suppliers FOR EACH ROW
BEGIN
    CALL write_audit_log('suppliers', NEW.id, 'UPDATE', COALESCE(@app_user_id, NEW.user_id, OLD.user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'name', OLD.name, 'first_balance', OLD.first_balance),
                         JSON_OBJECT('id', NEW.id, 'name', NEW.name, 'first_balance', NEW.first_balance), NULL);
END;
|
CREATE TRIGGER audit_suppliers_delete AFTER DELETE ON suppliers FOR EACH ROW
BEGIN
    CALL write_audit_log('suppliers', OLD.id, 'DELETE', COALESCE(@app_user_id, OLD.user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'name', OLD.name, 'first_balance', OLD.first_balance), NULL, NULL);
END;
|
DELIMITER ;

-- =====================================================================
-- 4. جدول إجمالي المبيعات (Total Sales)
-- =====================================================================
DELIMITER |
CREATE TRIGGER audit_total_sales_insert AFTER INSERT ON total_sales FOR EACH ROW
BEGIN
    CALL write_audit_log('total_sales', NEW.invoice_number, 'INSERT', COALESCE(@app_user_id, NEW.user_id, 1), NULL,
                         JSON_OBJECT('invoice_number', NEW.invoice_number, 'sup_code', NEW.sup_code, 'total', NEW.total, 'paid_up', NEW.paid_up, 'invoice_type', NEW.invoice_type), NULL);
END;
|
CREATE TRIGGER audit_total_sales_update AFTER UPDATE ON total_sales FOR EACH ROW
BEGIN
    CALL write_audit_log('total_sales', NEW.invoice_number, 'UPDATE', COALESCE(@app_user_id, NEW.user_id, OLD.user_id, 1),
                         JSON_OBJECT('invoice_number', OLD.invoice_number, 'sup_code', OLD.sup_code, 'total', OLD.total, 'paid_up', OLD.paid_up),
                         JSON_OBJECT('invoice_number', NEW.invoice_number, 'sup_code', NEW.sup_code, 'total', NEW.total, 'paid_up', NEW.paid_up), NULL);
END;
|
CREATE TRIGGER audit_total_sales_delete AFTER DELETE ON total_sales FOR EACH ROW
BEGIN
    CALL write_audit_log('total_sales', OLD.invoice_number, 'DELETE', COALESCE(@app_user_id, OLD.user_id, 1),
                         JSON_OBJECT('invoice_number', OLD.invoice_number, 'sup_code', OLD.sup_code, 'total', OLD.total), NULL, NULL);
END;
|
DELIMITER ;

-- =====================================================================
-- 5. جدول إجمالي المشتريات (Total Buy)
-- =====================================================================
DELIMITER |
CREATE TRIGGER audit_total_buy_insert AFTER INSERT ON total_buy FOR EACH ROW
BEGIN
    CALL write_audit_log('total_buy', NEW.invoice_number, 'INSERT', COALESCE(@app_user_id, NEW.user_id, 1), NULL,
                         JSON_OBJECT('invoice_number', NEW.invoice_number, 'sup_code', NEW.sup_code, 'total', NEW.total, 'paid_up', NEW.paid_up, 'invoice_type', NEW.invoice_type), NULL);
END;
|
CREATE TRIGGER audit_total_buy_update AFTER UPDATE ON total_buy FOR EACH ROW
BEGIN
    CALL write_audit_log('total_buy', NEW.invoice_number, 'UPDATE', COALESCE(@app_user_id, NEW.user_id, OLD.user_id, 1),
                         JSON_OBJECT('invoice_number', OLD.invoice_number, 'sup_code', OLD.sup_code, 'total', OLD.total, 'paid_up', OLD.paid_up),
                         JSON_OBJECT('invoice_number', NEW.invoice_number, 'sup_code', NEW.sup_code, 'total', NEW.total, 'paid_up', NEW.paid_up), NULL);
END;
|
CREATE TRIGGER audit_total_buy_delete AFTER DELETE ON total_buy FOR EACH ROW
BEGIN
    CALL write_audit_log('total_buy', OLD.invoice_number, 'DELETE', COALESCE(@app_user_id, OLD.user_id, 1),
                         JSON_OBJECT('invoice_number', OLD.invoice_number, 'sup_code', OLD.sup_code, 'total', OLD.total), NULL, NULL);
END;
|
DELIMITER ;

-- =====================================================================
-- 6. جدول الخزينة (Treasury)
-- =====================================================================
DELIMITER |
CREATE TRIGGER audit_treasury_insert AFTER INSERT ON treasury FOR EACH ROW
BEGIN
    CALL write_audit_log('treasury', NEW.id, 'INSERT', COALESCE(@app_user_id, NEW.user_id, 1), NULL,
                         JSON_OBJECT('id', NEW.id, 't_name', NEW.t_name, 'amount', NEW.amount), NULL);
END;
|
CREATE TRIGGER audit_treasury_update AFTER UPDATE ON treasury FOR EACH ROW
BEGIN
    CALL write_audit_log('treasury', NEW.id, 'UPDATE', COALESCE(@app_user_id, NEW.user_id, OLD.user_id, 1),
                         JSON_OBJECT('id', OLD.id, 't_name', OLD.t_name, 'amount', OLD.amount),
                         JSON_OBJECT('id', NEW.id, 't_name', NEW.t_name, 'amount', NEW.amount), NULL);
END;
|
CREATE TRIGGER audit_treasury_delete AFTER DELETE ON treasury FOR EACH ROW
BEGIN
    CALL write_audit_log('treasury', OLD.id, 'DELETE', COALESCE(@app_user_id, OLD.user_id, 1),
                         JSON_OBJECT('id', OLD.id, 't_name', OLD.t_name, 'amount', OLD.amount), NULL, NULL);
END;
|

DELIMITER ;
