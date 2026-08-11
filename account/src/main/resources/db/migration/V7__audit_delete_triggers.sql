-- =====================================================================
-- V7 - DELETE audit triggers for the rest of the deletable tables.
--
-- V1 audits items; V2 added the INSERT/UPDATE/DELETE triple for users,
-- custom, suppliers, total_sales, total_buy and treasury. Everything else
-- a user can delete - a unit, a group, an employee, an expense, a payment
-- on an account, a return invoice - left no trace at all: the row was gone
-- and nothing recorded what it had been.
--
-- Only DELETE is added here, on purpose. `old_data` on a delete is the
-- whole row as it stood, which is what makes the log answer "what was in
-- that record" after the fact; INSERT and UPDATE on these tables would
-- treble the write cost of every screen to record what the tables
-- themselves already hold.
--
-- The invoice line tables (sales, sales_re, purchase, purchase_re) are
-- deliberately left out. Saving an invoice deletes and re-inserts its
-- lines - see TotalsSalesDao.update - so a trigger there would write one
-- audit row per line per save, and bury the deletes that matter.
--
-- All of these route through write_audit_log, which skips its insert while
-- @app_bulk_wipe is set, so emptying these tables from the delete-data
-- screen does not copy the database into the log on its way out.
--
-- Dropped first: CREATE TRIGGER is not idempotent, and some installs had
-- an earlier hand-applied version of this bundle.
-- =====================================================================

DROP TRIGGER IF EXISTS audit_units_delete;
DROP TRIGGER IF EXISTS audit_main_group_delete;
DROP TRIGGER IF EXISTS audit_sub_group_delete;
DROP TRIGGER IF EXISTS audit_employees_delete;
DROP TRIGGER IF EXISTS audit_expenses_details_delete;
DROP TRIGGER IF EXISTS audit_customers_accounts_delete;
DROP TRIGGER IF EXISTS audit_suppliers_accounts_delete;
DROP TRIGGER IF EXISTS audit_total_sales_re_delete;
DROP TRIGGER IF EXISTS audit_total_buy_re_delete;

DELIMITER |

-- الوحدات
CREATE TRIGGER audit_units_delete AFTER DELETE ON units FOR EACH ROW
BEGIN
    CALL write_audit_log('units', OLD.unit_id, 'DELETE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('unit_id', OLD.unit_id, 'unit_name', OLD.unit_name, 'value_d', OLD.value_d),
                         NULL, NULL);
END;
|

-- المجموعات الرئيسية
CREATE TRIGGER audit_main_group_delete AFTER DELETE ON main_group FOR EACH ROW
BEGIN
    CALL write_audit_log('main_group', OLD.id, 'DELETE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'name_g', OLD.name_g),
                         NULL, NULL);
END;
|

-- المجموعات الفرعية
CREATE TRIGGER audit_sub_group_delete AFTER DELETE ON sub_group FOR EACH ROW
BEGIN
    CALL write_audit_log('sub_group', OLD.id, 'DELETE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'name', OLD.name, 'main_id', OLD.main_id),
                         NULL, NULL);
END;
|

-- الموظفين
CREATE TRIGGER audit_employees_delete AFTER DELETE ON employees FOR EACH ROW
BEGIN
    CALL write_audit_log('employees', OLD.id, 'DELETE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'column_name', OLD.column_name, 'salary', OLD.salary,
                                     'job', OLD.job, 'hire_date', OLD.hire_date),
                         NULL, NULL);
END;
|

-- المصروفات
CREATE TRIGGER audit_expenses_details_delete AFTER DELETE ON expenses_details FOR EACH ROW
BEGIN
    CALL write_audit_log('expenses_details', OLD.id, 'DELETE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'type_code', OLD.type_code, 'amount', OLD.amount,
                                     'treasury_id', OLD.treasury_id, 'notes', OLD.notes),
                         NULL, NULL);
END;
|

-- حركات حسابات العملاء
CREATE TRIGGER audit_customers_accounts_delete AFTER DELETE ON customers_accounts FOR EACH ROW
BEGIN
    CALL write_audit_log('customers_accounts', OLD.account_num, 'DELETE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('account_num', OLD.account_num, 'account_code', OLD.account_code,
                                     'account_date', OLD.account_date, 'paid', OLD.paid,
                                     'purchase', OLD.purchase, 'treasury_id', OLD.treasury_id,
                                     'numberInv', OLD.numberInv, 'notes', OLD.notes),
                         NULL, NULL);
END;
|

-- حركات حسابات الموردين
CREATE TRIGGER audit_suppliers_accounts_delete AFTER DELETE ON suppliers_accounts FOR EACH ROW
BEGIN
    CALL write_audit_log('suppliers_accounts', OLD.account_num, 'DELETE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('account_num', OLD.account_num, 'account_code', OLD.account_code,
                                     'account_date', OLD.account_date, 'paid', OLD.paid,
                                     'purchase', OLD.purchase, 'treasury_id', OLD.treasury_id,
                                     'notes', OLD.notes),
                         NULL, NULL);
END;
|

-- مرتجعات البيع
CREATE TRIGGER audit_total_sales_re_delete AFTER DELETE ON total_sales_re FOR EACH ROW
BEGIN
    CALL write_audit_log('total_sales_re', OLD.id, 'DELETE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'sup_id', OLD.sup_id, 'invoice_date', OLD.invoice_date,
                                     'total', OLD.total, 'discount', OLD.discount,
                                     'paid_from_treasury', OLD.paid_from_treasury, 'notes', OLD.notes),
                         NULL, NULL);
END;
|

-- مرتجعات الشراء
CREATE TRIGGER audit_total_buy_re_delete AFTER DELETE ON total_buy_re FOR EACH ROW
BEGIN
    CALL write_audit_log('total_buy_re', OLD.id, 'DELETE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'sup_id', OLD.sup_id, 'invoice_date', OLD.invoice_date,
                                     'total', OLD.total, 'discount', OLD.discount,
                                     'paid_to_treasury', OLD.paid_to_treasury, 'notes', OLD.notes),
                         NULL, NULL);
END;
|

DELIMITER ;
