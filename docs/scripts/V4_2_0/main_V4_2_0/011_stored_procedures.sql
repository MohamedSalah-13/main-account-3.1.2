-- =====================================================================
-- 14) Stored Procedure: فحص إمكانية إلغاء تحويل
-- =====================================================================
-- ترجع الأصناف التي لا تكفي كميتها في المخزن المستلم.
-- إذا لم يرجع الاستعلام أي صفوف، يمكن إلغاء التحويل.
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_check_stock_transfer_cancel;

DELIMITER $$

CREATE PROCEDURE sp_check_stock_transfer_cancel(
    IN p_transfer_id INT
)
BEGIN
    SELECT
        stl.item_id,
        i.nameItem,
        stl.quantity AS required_quantity,
        COALESCE(ist.current_quantity, 0) AS available_quantity,
        st.stock_to AS stock_id,
        s.stock_name
    FROM stock_transfer st
             JOIN stock_transfer_list stl ON stl.stock_transfer_id = st.id
             JOIN items i ON i.id = stl.item_id
             JOIN stocks s ON s.stock_id = st.stock_to
             LEFT JOIN items_stock ist
                       ON ist.item_id = stl.item_id
                           AND ist.stock_id = st.stock_to
    WHERE st.id = p_transfer_id
      AND st.status = 'POSTED'
      AND COALESCE(ist.current_quantity, 0) < stl.quantity;
END$$

DELIMITER ;


-- =====================================================================
-- 15) Stored Procedure: إلغاء تحويل بتحويل عكسي
-- =====================================================================
-- الشروط:
-- 1) التحويل الأصلي يجب أن يكون POSTED
-- 2) المخزن المستلم يجب أن يحتوي على الكميات المطلوبة للإلغاء
-- 3) لا يتم حذف أي بيانات
-- 4) يتم إنشاء تحويل عكسي
-- 5) يتم تحديث items_stock
-- 6) يتم تسجيل stock_movements
-- 7) يتم تعليم التحويل الأصلي بأنه CANCELLED
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_cancel_stock_transfer;

DELIMITER $$

CREATE PROCEDURE sp_cancel_stock_transfer(
    IN p_transfer_id INT,
    IN p_user_id INT,
    IN p_reason TEXT
)
BEGIN
    DECLARE v_stock_from INT;
    DECLARE v_stock_to INT;
    DECLARE v_reversal_transfer_id INT;
    DECLARE v_not_available_count INT DEFAULT 0;
    DECLARE v_original_status VARCHAR(20);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
        BEGIN
            ROLLBACK;
            RESIGNAL;
        END;

    START TRANSACTION;

    SELECT
        stock_from,
        stock_to,
        status
    INTO
        v_stock_from,
        v_stock_to,
        v_original_status
    FROM stock_transfer
    WHERE id = p_transfer_id
        FOR UPDATE;

    IF v_original_status IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'التحويل غير موجود';
    END IF;

    IF v_original_status <> 'POSTED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'لا يمكن إلغاء تحويل غير مرحل أو ملغي مسبقًا';
    END IF;

    SELECT
        COUNT(*)
    INTO
        v_not_available_count
    FROM stock_transfer st
             JOIN stock_transfer_list stl ON stl.stock_transfer_id = st.id
             LEFT JOIN items_stock ist
                       ON ist.item_id = stl.item_id
                           AND ist.stock_id = st.stock_to
    WHERE st.id = p_transfer_id
      AND COALESCE(ist.current_quantity, 0) < stl.quantity;

    IF v_not_available_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'لا يمكن إلغاء التحويل لأن الكمية غير متاحة في المخزن المستلم';
    END IF;

    INSERT INTO stock_transfer
    (
        transfer_date,
        stock_from,
        stock_to,
        status,
        cancel_reason,
        user_id
    )
    VALUES
        (
            CURDATE(),
            v_stock_to,
            v_stock_from,
            'POSTED',
            CONCAT('تحويل عكسي لإلغاء التحويل رقم ', p_transfer_id, ' - ', COALESCE(p_reason, '')),
            p_user_id
        );

    SET v_reversal_transfer_id = LAST_INSERT_ID();

    INSERT INTO stock_transfer_list
    (
        stock_transfer_id,
        item_id,
        quantity
    )
    SELECT
        v_reversal_transfer_id,
        item_id,
        quantity
    FROM stock_transfer_list
    WHERE stock_transfer_id = p_transfer_id;

    UPDATE items_stock ist
        JOIN stock_transfer_list stl
        ON stl.item_id = ist.item_id
        JOIN stock_transfer st
        ON st.id = stl.stock_transfer_id
    SET ist.current_quantity = ist.current_quantity - stl.quantity
    WHERE st.id = p_transfer_id
      AND ist.stock_id = st.stock_to
      AND ist.current_quantity >= stl.quantity;

    INSERT INTO items_stock
    (
        item_id,
        stock_id,
        first_balance,
        current_quantity
    )
    SELECT
        stl.item_id,
        st.stock_from,
        0,
        stl.quantity
    FROM stock_transfer st
             JOIN stock_transfer_list stl ON stl.stock_transfer_id = st.id
    WHERE st.id = p_transfer_id
    ON DUPLICATE KEY UPDATE
        current_quantity = current_quantity + VALUES(current_quantity);

    INSERT INTO stock_movements
    (
        item_id,
        stock_id,
        movement_type,
        quantity_in,
        quantity_out,
        reference_type,
        reference_id,
        reference_line_id,
        notes,
        user_id
    )
    SELECT
        stl.item_id,
        st.stock_to,
        'TRANSFER_OUT',
        0,
        stl.quantity,
        'STOCK_TRANSFER',
        v_reversal_transfer_id,
        stl.id,
        CONCAT('تحويل عكسي لإلغاء التحويل رقم ', st.id),
        p_user_id
    FROM stock_transfer st
             JOIN stock_transfer_list stl ON stl.stock_transfer_id = st.id
    WHERE st.id = p_transfer_id;

    INSERT INTO stock_movements
    (
        item_id,
        stock_id,
        movement_type,
        quantity_in,
        quantity_out,
        reference_type,
        reference_id,
        reference_line_id,
        notes,
        user_id
    )
    SELECT
        stl.item_id,
        st.stock_from,
        'TRANSFER_IN',
        stl.quantity,
        0,
        'STOCK_TRANSFER',
        v_reversal_transfer_id,
        stl.id,
        CONCAT('تحويل عكسي لإلغاء التحويل رقم ', st.id),
        p_user_id
    FROM stock_transfer st
             JOIN stock_transfer_list stl ON stl.stock_transfer_id = st.id
    WHERE st.id = p_transfer_id;

    UPDATE stock_transfer
    SET status = 'CANCELLED',
        cancelled_at = NOW(),
        cancelled_by = p_user_id,
        cancel_reason = p_reason,
        reversal_transfer_id = v_reversal_transfer_id
    WHERE id = p_transfer_id
      AND status = 'POSTED';

    COMMIT;

    SELECT
        p_transfer_id AS cancelled_transfer_id,
        v_reversal_transfer_id AS reversal_transfer_id,
        'تم إلغاء التحويل بنجاح عن طريق تحويل عكسي' AS message;
END$$

DELIMITER ;


-- =====================================================================
-- 16) Stored Procedure: تنفيذ تحويل مخزني
-- =====================================================================
-- هذه الإجراء اختياري، لكنه مفيد لو أردت تنفيذ التحويل من الداتا بيز مباشرة.
-- البرنامج يمكنه تنفيذ نفس المنطق من Java.
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_post_stock_transfer;

DELIMITER $$

CREATE PROCEDURE sp_post_stock_transfer(
    IN p_transfer_date DATE,
    IN p_stock_from INT,
    IN p_stock_to INT,
    IN p_user_id INT
)
BEGIN
    IF p_stock_from = p_stock_to THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'لا يمكن التحويل إلى نفس المخزن';
    END IF;

    INSERT INTO stock_transfer
    (
        transfer_date,
        stock_from,
        stock_to,
        status,
        user_id
    )
    VALUES
        (
            p_transfer_date,
            p_stock_from,
            p_stock_to,
            'POSTED',
            p_user_id
        );

    SELECT LAST_INSERT_ID() AS transfer_id;
END$$

DELIMITER ;


-- =====================================================================
-- 17) تقرير فحص سريع بعد التحديث
-- =====================================================================

SELECT
    'stock_transfer columns' AS check_name,
    COUNT(*) AS columns_count
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'stock_transfer'
  AND COLUMN_NAME IN (
                      'status',
                      'cancelled_at',
                      'cancelled_by',
                      'cancel_reason',
                      'reversal_transfer_id'
    );

SELECT
    'items_stock negative quantities' AS check_name,
    COUNT(*) AS invalid_rows
FROM items_stock
WHERE current_quantity < 0;

SELECT
    'stock_transfer_list duplicated items' AS check_name,
    COUNT(*) AS duplicated_groups
FROM (
         SELECT
             stock_transfer_id,
             item_id,
             COUNT(*) AS cnt
         FROM stock_transfer_list
         GROUP BY stock_transfer_id, item_id
         HAVING COUNT(*) > 1
     ) x;

SELECT
    'V019_stock_transfer_updates.sql executed successfully' AS message;

-- =====================================================================
-- 6) Stored Procedures للورديات
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_get_shift_summary;

DELIMITER $$

CREATE PROCEDURE sp_get_shift_summary(
    IN p_shift_id INT
)
BEGIN
    SELECT
        us.*,
        u.user_name,
        t.t_name AS treasury_name,
        (us.total_sales - us.total_sales_returns) AS net_sales,
        (us.close_balance - us.open_balance) AS net_change,
        -- عدد الفواتير حسب النوع
        (SELECT COUNT(*) FROM total_sales WHERE shift_id = p_shift_id) AS sales_count,
        (SELECT COUNT(*) FROM total_sales_re WHERE shift_id = p_shift_id) AS sales_return_count,
        (SELECT COUNT(*) FROM expenses_details WHERE shift_id = p_shift_id) AS expenses_count
    FROM user_shifts us
             JOIN users u ON u.id = us.user_id
             JOIN treasury t ON t.id = us.treasury_id
    WHERE us.id = p_shift_id;
END$$

DELIMITER ;

-- =====================================================================
-- 7) Stored Procedures لرأس المال
-- =====================================================================

DROP PROCEDURE IF EXISTS sp_calculate_partner_profit;

DELIMITER $$

-- حساب حصة شريك من الأرباح/الخسائر
CREATE PROCEDURE sp_calculate_partner_profit(
    IN p_partner_id INT,
    IN p_capital_id INT,
    IN p_net_profit_loss DECIMAL(16,2),
    IN p_is_profit TINYINT,
    OUT p_partner_amount DECIMAL(16,2),
    OUT p_partner_percentage DECIMAL(6,3)
)
BEGIN
    DECLARE v_profit_percentage DECIMAL(6,3);
    DECLARE v_loss_percentage DECIMAL(6,3);

    -- الحصول على نسب الشريك
    SELECT profit_percentage, loss_percentage
    INTO v_profit_percentage, v_loss_percentage
    FROM partner_shares
    WHERE partner_id = p_partner_id
      AND capital_id = p_capital_id;

    -- حساب المبلغ حسب نوع التوزيع
    IF p_is_profit = 1 THEN
        SET p_partner_percentage = v_profit_percentage;
        SET p_partner_amount = (p_net_profit_loss * v_profit_percentage) / 100;
    ELSE
        SET p_partner_percentage = v_loss_percentage;
        SET p_partner_amount = (p_net_profit_loss * v_loss_percentage) / 100;
    END IF;
END$$

DELIMITER ;

-- =====================================================================

DROP PROCEDURE IF EXISTS sp_distribute_profit_loss;

DELIMITER $$

-- توزيع الأرباح/الخسائر على الشركاء
CREATE PROCEDURE sp_distribute_profit_loss(
    IN p_distribution_id INT,
    IN p_user_id INT
)
BEGIN
    DECLARE v_capital_id INT;
    DECLARE v_net_profit_loss DECIMAL(16,2);
    DECLARE v_is_profit TINYINT;
    DECLARE v_partner_id INT;
    DECLARE v_partner_amount DECIMAL(16,2);
    DECLARE v_partner_percentage DECIMAL(6,3);
    DECLARE v_done INT DEFAULT FALSE;

    DECLARE partner_cursor CURSOR FOR
        SELECT ps.partner_id
        FROM partner_shares ps
                 JOIN profit_loss_distribution pld ON pld.capital_id = ps.capital_id
        WHERE pld.id = p_distribution_id;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
        BEGIN
            ROLLBACK;
            RESIGNAL;
        END;

    START TRANSACTION;

    -- الحصول على بيانات التوزيع
    SELECT capital_id, net_profit_loss, is_profit
    INTO v_capital_id, v_net_profit_loss, v_is_profit
    FROM profit_loss_distribution
    WHERE id = p_distribution_id;

    -- فتح المؤشر
    OPEN partner_cursor;

    read_loop: LOOP
        FETCH partner_cursor INTO v_partner_id;
        IF v_done THEN
            LEAVE read_loop;
        END IF;

        -- حساب حصة الشريك
        CALL sp_calculate_partner_profit(
                v_partner_id,
                v_capital_id,
                v_net_profit_loss,
                v_is_profit,
                v_partner_amount,
                v_partner_percentage
             );

        -- إدخال التفاصيل
        INSERT INTO profit_loss_distribution_details (
            distribution_id,
            partner_id,
            partner_share_percent,
            partner_profit_percent,
            partner_amount,
            paid_amount,
            payment_status
        ) VALUES (
                     p_distribution_id,
                     v_partner_id,
                     (SELECT share_percentage FROM partner_shares
                      WHERE partner_id = v_partner_id AND capital_id = v_capital_id),
                     v_partner_percentage,
                     v_partner_amount,
                     0,
                     'UNPAID'
                 );

        -- تسجيل الحركة في رأس المال
        INSERT INTO capital_movements (
            capital_id,
            partner_id,
            movement_date,
            movement_type,
            amount_in,
            amount_out,
            balance_after,
            reference_type,
            reference_id,
            notes,
            user_id
        ) VALUES (
                     v_capital_id,
                     v_partner_id,
                     CURDATE(),
                     IF(v_is_profit = 1, 'PROFIT_DISTRIBUTION', 'LOSS_DISTRIBUTION'),
                     IF(v_is_profit = 1, v_partner_amount, 0),
                     IF(v_is_profit = 0, v_partner_amount, 0),
                     0, -- سيتم حسابه في trigger
                     'PROFIT_LOSS_DISTRIBUTION',
                     p_distribution_id,
                     CONCAT('توزيع ', IF(v_is_profit = 1, 'أرباح', 'خسائر'), ' فترة ',
                            (SELECT CONCAT(period_from, ' إلى ', period_to)
                             FROM profit_loss_distribution WHERE id = p_distribution_id)),
                     p_user_id
                 );

    END LOOP;

    CLOSE partner_cursor;

    -- تحديث حالة التوزيع
    UPDATE profit_loss_distribution
    SET distribution_status = 'DISTRIBUTED',
        distributed_at = NOW()
    WHERE id = p_distribution_id;

    COMMIT;

    SELECT 'تم توزيع الأرباح/الخسائر بنجاح' AS message;
END$$

DELIMITER ;


-- =====================================================================
-- تسجيل نسخة التحديث بدون تكرار
-- =====================================================================

INSERT INTO database_migrations (version, description, executed_at)
SELECT '4.2.0', 'Main database schema and procedures', NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM database_migrations
    WHERE version = '4.2.0'
);

SELECT '011_stored_procedures.sql executed successfully' AS status;