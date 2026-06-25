-- =====================================================================
-- 009_stock_treasury_management_triggers.sql
-- Triggers تلقائية لتسجيل حركات المخزون والخزينة
-- =====================================================================

USE account_system_db;

-- =====================================================================
-- 0) حذف كل Triggers هذا الملف مسبقاً لضمان إعادة الإنشاء بشكل صحيح
-- =====================================================================
DROP TRIGGER IF EXISTS after_purchase_insert;
DROP TRIGGER IF EXISTS after_sales_insert;
DROP TRIGGER IF EXISTS after_purchase_return_insert;
DROP TRIGGER IF EXISTS after_sales_return_insert;
DROP TRIGGER IF EXISTS after_stock_movements_insert;
DROP TRIGGER IF EXISTS before_sales_insert_check_stock;
DROP TRIGGER IF EXISTS after_total_sales_insert;
DROP TRIGGER IF EXISTS after_total_buy_insert;
DROP TRIGGER IF EXISTS after_total_sales_re_insert;
DROP TRIGGER IF EXISTS after_total_buy_re_insert;
DROP TRIGGER IF EXISTS after_expenses_details_insert;
DROP TRIGGER IF EXISTS after_treasury_deposit_expenses_insert;
DROP TRIGGER IF EXISTS after_customers_accounts_insert;
DROP TRIGGER IF EXISTS after_suppliers_accounts_insert;
DROP TRIGGER IF EXISTS before_treasury_movements_insert;
DROP TRIGGER IF EXISTS after_treasury_movements_insert;
DROP TRIGGER IF EXISTS before_total_sales_insert_shift;
DROP TRIGGER IF EXISTS before_total_sales_re_insert_shift;
DROP TRIGGER IF EXISTS before_expenses_details_insert_shift;
DROP TRIGGER IF EXISTS before_treasury_deposit_expenses_insert_shift;
DROP TRIGGER IF EXISTS before_customers_accounts_insert_shift;
DROP TRIGGER IF EXISTS before_stock_movements_delete;
DROP TRIGGER IF EXISTS before_treasury_movements_delete;
DROP TRIGGER IF EXISTS after_user_shifts_update;
-- =====================================================================
-- 1) Triggers لتسجيل حركات المخزون تلقائياً
-- =====================================================================

-- ---------------------------------------------------------------------
-- A) Trigger عند إدخال فاتورة مشتريات
-- ---------------------------------------------------------------------

DROP TRIGGER IF EXISTS after_purchase_insert;

DELIMITER $$

CREATE TRIGGER after_purchase_insert
    AFTER INSERT ON purchase
    FOR EACH ROW
BEGIN
    DECLARE v_stock_id INT;
    DECLARE v_user_id INT;
    DECLARE v_invoice_date DATE;

    SET v_stock_id = (
        SELECT stock_id
        FROM total_buy
        WHERE invoice_number = NEW.invoice_number
        LIMIT 1
    );

    SET v_user_id = (
        SELECT user_id
        FROM total_buy
        WHERE invoice_number = NEW.invoice_number
        LIMIT 1
    );

    SET v_invoice_date = (
        SELECT invoice_date
        FROM total_buy
        WHERE invoice_number = NEW.invoice_number
        LIMIT 1
    );

    IF v_stock_id IS NOT NULL THEN
        INSERT INTO stock_movements (
            item_id,
            stock_id,
            movement_date,
            movement_type,
            quantity_in,
            quantity_out,
            unit_id,
            unit_value,
            reference_type,
            reference_id,
            reference_line_id,
            notes,
            user_id
        ) VALUES (
                     NEW.num,
                     v_stock_id,
                     v_invoice_date,
                     'PURCHASE',
                     NEW.quantity * NEW.type_value,
                     0,
                     NEW.type,
                     NEW.type_value,
                     'PURCHASE',
                     NEW.invoice_number,
                     NEW.id,
                     CONCAT('مشتريات - فاتورة رقم ', NEW.invoice_number),
                     COALESCE(v_user_id, 1)
                 );
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- B) Trigger عند إدخال فاتورة مبيعات
-- ---------------------------------------------------------------------

DROP TRIGGER IF EXISTS after_sales_insert;

DELIMITER $$

CREATE TRIGGER after_sales_insert
    AFTER INSERT ON sales
    FOR EACH ROW
BEGIN
    DECLARE v_stock_id INT;
    DECLARE v_user_id INT;
    DECLARE v_invoice_date DATE;

    SET v_stock_id = (
        SELECT stock_id
        FROM total_sales
        WHERE invoice_number = NEW.invoice_number
        LIMIT 1
    );

    SET v_user_id = (
        SELECT user_id
        FROM total_sales
        WHERE invoice_number = NEW.invoice_number
        LIMIT 1
    );

    SET v_invoice_date = (
        SELECT invoice_date
        FROM total_sales
        WHERE invoice_number = NEW.invoice_number
        LIMIT 1
    );

    IF v_stock_id IS NOT NULL THEN
        INSERT INTO stock_movements (
            item_id,
            stock_id,
            movement_date,
            movement_type,
            quantity_in,
            quantity_out,
            unit_id,
            unit_value,
            reference_type,
            reference_id,
            reference_line_id,
            notes,
            user_id
        ) VALUES (
                     NEW.num,
                     v_stock_id,
                     v_invoice_date,
                     'SALE',
                     0,
                     NEW.quantity * NEW.type_value,
                     NEW.type,
                     NEW.type_value,
                     'SALE',
                     NEW.invoice_number,
                     NEW.id,
                     CONCAT('مبيعات - فاتورة رقم ', NEW.invoice_number),
                     COALESCE(v_user_id, 1)
                 );
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- C) Trigger عند مرتجع مشتريات
-- ---------------------------------------------------------------------

DROP TRIGGER IF EXISTS after_purchase_return_insert;

DELIMITER $$

CREATE TRIGGER after_purchase_return_insert
    AFTER INSERT ON purchase_re
    FOR EACH ROW
BEGIN
    DECLARE v_stock_id INT;
    DECLARE v_user_id INT;
    DECLARE v_invoice_date DATE;

    SET v_stock_id = (
        SELECT stock_id
        FROM total_buy_re
        WHERE id = NEW.invoice_number
        LIMIT 1
    );

    SET v_user_id = (
        SELECT user_id
        FROM total_buy_re
        WHERE id = NEW.invoice_number
        LIMIT 1
    );

    SET v_invoice_date = (
        SELECT invoice_date
        FROM total_buy_re
        WHERE id = NEW.invoice_number
        LIMIT 1
    );

    IF v_stock_id IS NOT NULL THEN
        INSERT INTO stock_movements (
            item_id,
            stock_id,
            movement_date,
            movement_type,
            quantity_in,
            quantity_out,
            unit_id,
            unit_value,
            reference_type,
            reference_id,
            reference_line_id,
            notes,
            user_id
        ) VALUES (
                     NEW.item_id,
                     v_stock_id,
                     v_invoice_date,
                     'PURCHASE_RETURN',
                     0,
                     NEW.quantity * NEW.type_value,
                     NEW.type,
                     NEW.type_value,
                     'PURCHASE_RETURN',
                     NEW.invoice_number,
                     NEW.id,
                     CONCAT('مرتجع مشتريات - فاتورة رقم ', NEW.invoice_number),
                     COALESCE(v_user_id, 1)
                 );
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- D) Trigger عند مرتجع مبيعات
-- ---------------------------------------------------------------------

DROP TRIGGER IF EXISTS after_sales_return_insert;

DELIMITER $$

CREATE TRIGGER after_sales_return_insert
    AFTER INSERT ON sales_re
    FOR EACH ROW
BEGIN
    DECLARE v_stock_id INT;
    DECLARE v_user_id INT;
    DECLARE v_invoice_date DATE;

    SET v_stock_id = (
        SELECT stock_id
        FROM total_sales_re
        WHERE id = NEW.invoice_number
        LIMIT 1
    );

    SET v_user_id = (
        SELECT user_id
        FROM total_sales_re
        WHERE id = NEW.invoice_number
        LIMIT 1
    );

    SET v_invoice_date = (
        SELECT invoice_date
        FROM total_sales_re
        WHERE id = NEW.invoice_number
        LIMIT 1
    );

    IF v_stock_id IS NOT NULL THEN
        INSERT INTO stock_movements (
            item_id,
            stock_id,
            movement_date,
            movement_type,
            quantity_in,
            quantity_out,
            unit_id,
            unit_value,
            reference_type,
            reference_id,
            reference_line_id,
            notes,
            user_id
        ) VALUES (
                     NEW.item_id,
                     v_stock_id,
                     v_invoice_date,
                     'SALE_RETURN',
                     NEW.quantity * NEW.type_value,
                     0,
                     NEW.type,
                     NEW.type_value,
                     'SALE_RETURN',
                     NEW.invoice_number,
                     NEW.id,
                     CONCAT('مرتجع مبيعات - فاتورة رقم ', NEW.invoice_number),
                     COALESCE(v_user_id, 1)
                 );
    END IF;
END$$

DELIMITER ;

-- =====================================================================
-- 2) Trigger لتحديث رصيد المخزون تلقائياً
-- =====================================================================

DROP TRIGGER IF EXISTS after_stock_movements_insert;

DELIMITER $$

CREATE TRIGGER after_stock_movements_insert
    AFTER INSERT ON stock_movements
    FOR EACH ROW
BEGIN
    DECLARE v_current DECIMAL(14,3) DEFAULT 0;
    DECLARE v_new_qty DECIMAL(14,3) DEFAULT 0;
    DECLARE v_item_name VARCHAR(200);

    SELECT COALESCE(current_quantity, 0)
    INTO v_current
    FROM items_stock
    WHERE item_id = NEW.item_id AND stock_id = NEW.stock_id
    LIMIT 1;

    SET v_new_qty = v_current + NEW.quantity_in - NEW.quantity_out;

    IF v_new_qty < 0 THEN
        SELECT nameItem INTO v_item_name FROM items WHERE id = NEW.item_id;
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = CONCAT(
                    'لا يوجد رصيد كافٍ للصنف: ', COALESCE(v_item_name, CONCAT('ID=', NEW.item_id)),
                    ' | المتاح: ', v_current,
                    ' | المطلوب: ', NEW.quantity_out
                               );
    END IF;

    INSERT INTO items_stock (item_id, stock_id, first_balance, current_quantity)
    VALUES (NEW.item_id, NEW.stock_id, 0, v_new_qty)
    ON DUPLICATE KEY UPDATE
        current_quantity = current_quantity + NEW.quantity_in - NEW.quantity_out;
END$$

DELIMITER ;

-- =====================================================================
-- 3) Triggers ضف trigger BEFORE INSERT على جدول sales يتحقق من توفر الكمية قبل أن تصل إلى stock_movements:
-- =====================================================================
DROP TRIGGER IF EXISTS before_sales_insert_check_stock;

DELIMITER $$

CREATE TRIGGER before_sales_insert_check_stock
    BEFORE INSERT ON sales
    FOR EACH ROW
BEGIN
    DECLARE v_stock_id INT;
    DECLARE v_available DECIMAL(14,3) DEFAULT 0;
    DECLARE v_required DECIMAL(14,3);
    DECLARE v_item_name VARCHAR(200);

    SET v_required = NEW.quantity * NEW.type_value;

    SELECT stock_id INTO v_stock_id
    FROM total_sales
    WHERE invoice_number = NEW.invoice_number
    LIMIT 1;

    IF v_stock_id IS NOT NULL THEN
        SELECT COALESCE(current_quantity, 0)
        INTO v_available
        FROM items_stock
        WHERE item_id = NEW.num AND stock_id = v_stock_id
        LIMIT 1;

        IF v_required > v_available THEN
            SELECT nameItem INTO v_item_name FROM items WHERE id = NEW.num;
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = CONCAT(
                        'الكمية المطلوبة (', v_required, ') أكبر من المتاح (', v_available,
                        ') للصنف: ', COALESCE(v_item_name, '')
                                   );
        END IF;
    END IF;
END$$

DELIMITER ;
-- ---------------------------------------------------------------------
-- A) Trigger عند فاتورة مبيعات نقدية
-- ---------------------------------------------------------------------

DROP TRIGGER IF EXISTS after_total_sales_insert;

DELIMITER $$

CREATE TRIGGER after_total_sales_insert
    AFTER INSERT ON total_sales
    FOR EACH ROW
BEGIN
    IF COALESCE(NEW.paid_up, 0) > 0 THEN
        INSERT INTO treasury_movements (
            treasury_id,
            movement_date,
            movement_type,
            amount_in,
            amount_out,
            reference_type,
            reference_id,
            notes,
            user_id
        ) VALUES (
                     NEW.treasury_id,
                     NEW.invoice_date,
                     'SALE',
                     NEW.paid_up,
                     0,
                     'SALE',
                     NEW.invoice_number,
                     CONCAT('مبيعات نقدي - فاتورة رقم ', NEW.invoice_number),
                     NEW.user_id
                 );
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- B) Trigger عند فاتورة مشتريات نقدية
-- ---------------------------------------------------------------------

DROP TRIGGER IF EXISTS after_total_buy_insert;

DELIMITER $$

CREATE TRIGGER after_total_buy_insert
    AFTER INSERT ON total_buy
    FOR EACH ROW
BEGIN
    IF COALESCE(NEW.paid_up, 0) > 0 THEN
        INSERT INTO treasury_movements (
            treasury_id,
            movement_date,
            movement_type,
            amount_in,
            amount_out,
            reference_type,
            reference_id,
            notes,
            user_id
        ) VALUES (
                     NEW.treasury_id,
                     NEW.invoice_date,
                     'PURCHASE',
                     0,
                     NEW.paid_up,
                     'PURCHASE',
                     NEW.invoice_number,
                     CONCAT('مشتريات نقدي - فاتورة رقم ', NEW.invoice_number),
                     NEW.user_id
                 );
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- C) Trigger عند مرتجع مبيعات نقدي
-- ---------------------------------------------------------------------

DROP TRIGGER IF EXISTS after_total_sales_re_insert;

DELIMITER $$

CREATE TRIGGER after_total_sales_re_insert
    AFTER INSERT ON total_sales_re
    FOR EACH ROW
BEGIN
    IF COALESCE(NEW.paid_from_treasury, 0) > 0 THEN
        INSERT INTO treasury_movements (
            treasury_id,
            movement_date,
            movement_type,
            amount_in,
            amount_out,
            reference_type,
            reference_id,
            notes,
            user_id
        ) VALUES (
                     NEW.treasury_id,
                     NEW.invoice_date,
                     'SALE_RETURN',
                     0,
                     NEW.paid_from_treasury,
                     'SALE_RETURN',
                     NEW.id,
                     CONCAT('مرتجع مبيعات نقدي - فاتورة رقم ', NEW.id),
                     NEW.user_id
                 );
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- D) Trigger عند مرتجع مشتريات نقدي
-- ---------------------------------------------------------------------

DROP TRIGGER IF EXISTS after_total_buy_re_insert;

DELIMITER $$

CREATE TRIGGER after_total_buy_re_insert
    AFTER INSERT ON total_buy_re
    FOR EACH ROW
BEGIN
    IF COALESCE(NEW.paid_to_treasury, 0) > 0 THEN
        INSERT INTO treasury_movements (
            treasury_id,
            movement_date,
            movement_type,
            amount_in,
            amount_out,
            reference_type,
            reference_id,
            notes,
            user_id
        ) VALUES (
                     NEW.treasury_id,
                     NEW.invoice_date,
                     'PURCHASE_RETURN',
                     NEW.paid_to_treasury,
                     0,
                     'PURCHASE_RETURN',
                     NEW.id,
                     CONCAT('مرتجع مشتريات نقدي - فاتورة رقم ', NEW.id),
                     NEW.user_id
                 );
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- E) Trigger عند المصروفات
-- ---------------------------------------------------------------------

DROP TRIGGER IF EXISTS after_expenses_details_insert;

DELIMITER $$

CREATE TRIGGER after_expenses_details_insert
    AFTER INSERT ON expenses_details
    FOR EACH ROW
BEGIN
    IF COALESCE(NEW.amount, 0) > 0 THEN
        INSERT INTO treasury_movements (
            treasury_id,
            movement_date,
            movement_type,
            amount_in,
            amount_out,
            reference_type,
            reference_id,
            notes,
            user_id
        ) VALUES (
                     NEW.treasury_id,
                     NEW.date,
                     'EXPENSE',
                     0,
                     NEW.amount,
                     'EXPENSE',
                     NEW.id,
                     CONCAT('مصروفات - ', COALESCE(NEW.notes, '')),
                     NEW.user_id
                 );
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- F) Trigger عند الإيداعات/المسحوبات
-- ---------------------------------------------------------------------

DROP TRIGGER IF EXISTS after_treasury_deposit_expenses_insert;

DELIMITER $$

CREATE TRIGGER after_treasury_deposit_expenses_insert
    AFTER INSERT ON treasury_deposit_expenses
    FOR EACH ROW
BEGIN
    IF COALESCE(NEW.amount, 0) > 0 THEN
        INSERT INTO treasury_movements (
            treasury_id,
            movement_date,
            movement_type,
            amount_in,
            amount_out,
            reference_type,
            reference_id,
            notes,
            user_id
        ) VALUES (
                     NEW.treasury_id,
                     NEW.date_inter,
                     IF(NEW.deposit_or_expenses = 1, 'DEPOSIT', 'WITHDRAWAL'),
                     IF(NEW.deposit_or_expenses = 1, NEW.amount, 0),
                     IF(NEW.deposit_or_expenses = 2, NEW.amount, 0),
                     'TREASURY_DEPOSIT_EXPENSES',
                     NEW.id,
                     NEW.statement,
                     NEW.user_id
                 );
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- G) Trigger عند دفعات العملاء
-- ---------------------------------------------------------------------

DROP TRIGGER IF EXISTS after_customers_accounts_insert;

DELIMITER $$

CREATE TRIGGER after_customers_accounts_insert
    AFTER INSERT ON customers_accounts
    FOR EACH ROW
BEGIN
    IF COALESCE(NEW.paid, 0) > 0 THEN
        INSERT INTO treasury_movements (
            treasury_id,
            movement_date,
            movement_type,
            amount_in,
            amount_out,
            reference_type,
            reference_id,
            notes,
            user_id
        ) VALUES (
                     NEW.treasury_id,
                     NEW.account_date,
                     'SALE',
                     NEW.paid,
                     0,
                     'SALE',
                     NEW.numberInv,
                     CONCAT('تحصيل من عميل - حساب رقم ', NEW.account_num),
                     NEW.user_id
                 );
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- H) Trigger عند دفعات الموردين
-- ---------------------------------------------------------------------

DROP TRIGGER IF EXISTS after_suppliers_accounts_insert;

DELIMITER $$

CREATE TRIGGER after_suppliers_accounts_insert
    AFTER INSERT ON suppliers_accounts
    FOR EACH ROW
BEGIN
    IF COALESCE(NEW.paid, 0) > 0 THEN
        INSERT INTO treasury_movements (
            treasury_id,
            movement_date,
            movement_type,
            amount_in,
            amount_out,
            reference_type,
            reference_id,
            notes,
            user_id
        ) VALUES (
                     NEW.treasury_id,
                     NEW.account_date,
                     'PURCHASE',
                     0,
                     NEW.paid,
                     'PURCHASE',
                     NEW.numberInv,
                     CONCAT('دفع لمورد - حساب رقم ', NEW.account_num),
                     NEW.user_id
                 );
    END IF;
END$$

DELIMITER ;

-- =====================================================================
-- 4) Triggers لتحديث رصيد الخزينة تلقائياً
-- =====================================================================

DROP TRIGGER IF EXISTS before_treasury_movements_insert;
DROP TRIGGER IF EXISTS after_treasury_movements_insert;

DELIMITER $$

CREATE TRIGGER before_treasury_movements_insert
    BEFORE INSERT ON treasury_movements
    FOR EACH ROW
BEGIN
    DECLARE v_current_balance DECIMAL(16,2) DEFAULT 0;

    SELECT COALESCE(
                   (
                       SELECT amount
                       FROM treasury
                       WHERE id = NEW.treasury_id
                       LIMIT 1
                   ),
                   0
           )
    INTO v_current_balance;

    SET NEW.amount_in = COALESCE(NEW.amount_in, 0);
    SET NEW.amount_out = COALESCE(NEW.amount_out, 0);
    SET NEW.balance_after = v_current_balance + NEW.amount_in - NEW.amount_out;
END$$

CREATE TRIGGER after_treasury_movements_insert
    AFTER INSERT ON treasury_movements
    FOR EACH ROW
BEGIN
    UPDATE treasury
    SET amount = NEW.balance_after
    WHERE id = NEW.treasury_id;
END$$

DELIMITER ;

-- =====================================================================
-- 5) Triggers لربط الفواتير بالورديات تلقائياً
-- =====================================================================

DROP TRIGGER IF EXISTS before_total_sales_insert_shift;
DROP TRIGGER IF EXISTS before_total_sales_re_insert_shift;
DROP TRIGGER IF EXISTS before_expenses_details_insert_shift;
DROP TRIGGER IF EXISTS before_treasury_deposit_expenses_insert_shift;
DROP TRIGGER IF EXISTS before_customers_accounts_insert_shift;

DELIMITER $$

CREATE TRIGGER before_total_sales_insert_shift
    BEFORE INSERT ON total_sales
    FOR EACH ROW
BEGIN
    DECLARE v_open_shift_id INT DEFAULT NULL;

    SET v_open_shift_id = (
        SELECT id
        FROM user_shifts
        WHERE user_id = NEW.user_id
          AND treasury_id = NEW.treasury_id
          AND is_open = TRUE
        LIMIT 1
    );

    IF v_open_shift_id IS NOT NULL THEN
        SET NEW.shift_id = v_open_shift_id;
    END IF;
END$$

CREATE TRIGGER before_total_sales_re_insert_shift
    BEFORE INSERT ON total_sales_re
    FOR EACH ROW
BEGIN
    DECLARE v_open_shift_id INT DEFAULT NULL;

    SET v_open_shift_id = (
        SELECT id
        FROM user_shifts
        WHERE user_id = NEW.user_id
          AND treasury_id = NEW.treasury_id
          AND is_open = TRUE
        LIMIT 1
    );

    IF v_open_shift_id IS NOT NULL THEN
        SET NEW.shift_id = v_open_shift_id;
    END IF;
END$$

CREATE TRIGGER before_expenses_details_insert_shift
    BEFORE INSERT ON expenses_details
    FOR EACH ROW
BEGIN
    DECLARE v_open_shift_id INT DEFAULT NULL;

    SET v_open_shift_id = (
        SELECT id
        FROM user_shifts
        WHERE user_id = NEW.user_id
          AND treasury_id = NEW.treasury_id
          AND is_open = TRUE
        LIMIT 1
    );

    IF v_open_shift_id IS NOT NULL THEN
        SET NEW.shift_id = v_open_shift_id;
    END IF;
END$$

CREATE TRIGGER before_treasury_deposit_expenses_insert_shift
    BEFORE INSERT ON treasury_deposit_expenses
    FOR EACH ROW
BEGIN
    DECLARE v_open_shift_id INT DEFAULT NULL;

    SET v_open_shift_id = (
        SELECT id
        FROM user_shifts
        WHERE user_id = NEW.user_id
          AND treasury_id = NEW.treasury_id
          AND is_open = TRUE
        LIMIT 1
    );

    IF v_open_shift_id IS NOT NULL THEN
        SET NEW.shift_id = v_open_shift_id;
    END IF;
END$$

CREATE TRIGGER before_customers_accounts_insert_shift
    BEFORE INSERT ON customers_accounts
    FOR EACH ROW
BEGIN
    DECLARE v_open_shift_id INT DEFAULT NULL;

    SET v_open_shift_id = (
        SELECT id
        FROM user_shifts
        WHERE user_id = NEW.user_id
          AND treasury_id = NEW.treasury_id
          AND is_open = TRUE
        LIMIT 1
    );

    IF v_open_shift_id IS NOT NULL THEN
        SET NEW.shift_id = v_open_shift_id;
    END IF;
END$$

DELIMITER ;

-- =====================================================================
-- 6) Triggers لمنع حذف الحركات بعد الترحيل
-- =====================================================================

DROP TRIGGER IF EXISTS before_stock_movements_delete;
DROP TRIGGER IF EXISTS before_treasury_movements_delete;

DELIMITER $$

CREATE TRIGGER before_stock_movements_delete
    BEFORE DELETE ON stock_movements
    FOR EACH ROW
BEGIN
    IF @allow_delete IS NULL OR @allow_delete = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'لا يمكن حذف حركات المخزون المرحلة. استخدم التسويات بدلاً من ذلك.';
    END IF;
END$$

CREATE TRIGGER before_treasury_movements_delete
    BEFORE DELETE ON treasury_movements
    FOR EACH ROW
BEGIN
    IF @allow_delete IS NULL OR @allow_delete = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'لا يمكن حذف حركات الخزينة المرحلة. استخدم التسويات بدلاً من ذلك.';
    END IF;
END$$

DELIMITER ;

-- =====================================================================
-- 7) Triggers للتدقيق Audit
-- =====================================================================

DROP TRIGGER IF EXISTS after_user_shifts_update;

DELIMITER $$

CREATE TRIGGER after_user_shifts_update
    AFTER UPDATE ON user_shifts
    FOR EACH ROW
BEGIN
    INSERT INTO audit_log (
        table_name,
        record_id,
        action_type,
        user_id,
        old_data,
        new_data,
        source,
        notes
    ) VALUES (
                 'user_shifts',
                 NEW.id,
                 'UPDATE',
                 NEW.user_id,
                 JSON_OBJECT(
                         'open_balance', OLD.open_balance,
                         'close_balance', OLD.close_balance,
                         'is_open', OLD.is_open,
                         'status', OLD.shift_status
                 ),
                 JSON_OBJECT(
                         'open_balance', NEW.open_balance,
                         'close_balance', NEW.close_balance,
                         'is_open', NEW.is_open,
                         'status', NEW.shift_status
                 ),
                 'TRIGGER',
                 IF(OLD.is_open = TRUE AND NEW.is_open = FALSE, 'إغلاق وردية', 'تحديث وردية')
             );
END$$

DELIMITER ;

-- =====================================================================
-- 8) تقرير تحقق بعد إنشاء Triggers
-- =====================================================================

SELECT 'تم إنشاء جميع Triggers التلقائية بنجاح!' AS status;

SELECT COUNT(*) AS triggers_count
FROM information_schema.triggers
WHERE trigger_schema = DATABASE();

-- التحقق من وجود الـ triggers الأساسية لإدارة المخزون والخزينة
SELECT expected.trigger_name,
       CASE WHEN t.trigger_name IS NULL THEN 'مفقود ❌' ELSE 'موجود ✅' END AS status
FROM (
         SELECT 'after_purchase_insert' AS trigger_name
         UNION ALL SELECT 'after_sales_insert'
         UNION ALL SELECT 'after_purchase_return_insert'
         UNION ALL SELECT 'after_sales_return_insert'
         UNION ALL SELECT 'after_stock_movements_insert'
         UNION ALL SELECT 'before_sales_insert_check_stock'
         UNION ALL SELECT 'after_total_sales_insert'
         UNION ALL SELECT 'after_total_buy_insert'
         UNION ALL SELECT 'after_total_sales_re_insert'
         UNION ALL SELECT 'after_total_buy_re_insert'
         UNION ALL SELECT 'after_expenses_details_insert'
         UNION ALL SELECT 'after_treasury_deposit_expenses_insert'
         UNION ALL SELECT 'after_customers_accounts_insert'
         UNION ALL SELECT 'after_suppliers_accounts_insert'
         UNION ALL SELECT 'before_treasury_movements_insert'
         UNION ALL SELECT 'after_treasury_movements_insert'
         UNION ALL SELECT 'before_stock_movements_delete'
         UNION ALL SELECT 'before_treasury_movements_delete'
     ) AS expected
         LEFT JOIN information_schema.triggers t
                   ON t.trigger_name = expected.trigger_name
                       AND t.trigger_schema = DATABASE()
ORDER BY status, expected.trigger_name;