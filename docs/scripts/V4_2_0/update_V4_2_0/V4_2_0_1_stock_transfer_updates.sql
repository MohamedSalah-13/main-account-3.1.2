-- =====================================================================
-- V019_stock_transfer_updates.sql
-- تحديثات نظام تحويلات المخازن
-- =====================================================================
-- الهدف:
-- 1) إضافة حالة للتحويلات stock_transfer.status
-- 2) دعم إلغاء التحويل بتحويل عكسي بدل الحذف
-- 3) منع الكميات السالبة في items_stock
-- 4) منع تكرار نفس الصنف داخل نفس التحويل
-- 5) تعديل علاقة stock_transfer_list لمنع حذف التحويلات المرحّلة
-- 6) تحديث View التحويلات ليعرض التحويلات المرحلة فقط
-- 7) إضافة Views مساعدة لأرصدة المخازن
-- =====================================================================

USE account_system_db;

-- =====================================================================
-- 1) إضافة أعمدة حالة التحويل والإلغاء إلى stock_transfer
-- =====================================================================

SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_transfer'
      AND COLUMN_NAME = 'status'
);

SET @sql := IF(
        @column_exists = 0,
        'ALTER TABLE stock_transfer ADD COLUMN status VARCHAR(20) DEFAULT ''POSTED'' NOT NULL AFTER stock_to',
        'SELECT ''Column stock_transfer.status already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_transfer'
      AND COLUMN_NAME = 'cancelled_at'
);

SET @sql := IF(
        @column_exists = 0,
        'ALTER TABLE stock_transfer ADD COLUMN cancelled_at DATETIME NULL AFTER status',
        'SELECT ''Column stock_transfer.cancelled_at already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_transfer'
      AND COLUMN_NAME = 'cancelled_by'
);

SET @sql := IF(
        @column_exists = 0,
        'ALTER TABLE stock_transfer ADD COLUMN cancelled_by INT NULL AFTER cancelled_at',
        'SELECT ''Column stock_transfer.cancelled_by already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_transfer'
      AND COLUMN_NAME = 'cancel_reason'
);

SET @sql := IF(
        @column_exists = 0,
        'ALTER TABLE stock_transfer ADD COLUMN cancel_reason TEXT NULL AFTER cancelled_by',
        'SELECT ''Column stock_transfer.cancel_reason already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


SET @column_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_transfer'
      AND COLUMN_NAME = 'reversal_transfer_id'
);

SET @sql := IF(
        @column_exists = 0,
        'ALTER TABLE stock_transfer ADD COLUMN reversal_transfer_id INT NULL AFTER cancel_reason',
        'SELECT ''Column stock_transfer.reversal_transfer_id already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 2) ضبط البيانات القديمة
-- =====================================================================

UPDATE stock_transfer
SET status = 'POSTED'
WHERE status IS NULL OR status = '';


-- =====================================================================
-- 3) إضافة قيود CHECK على stock_transfer
-- =====================================================================

SET @constraint_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_transfer'
      AND CONSTRAINT_NAME = 'stock_transfer_status_chk'
);

SET @sql := IF(
        @constraint_exists = 0,
        'ALTER TABLE stock_transfer ADD CONSTRAINT stock_transfer_status_chk CHECK (status IN (''POSTED'', ''CANCELLED''))',
        'SELECT ''Constraint stock_transfer_status_chk already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 4) إضافة Foreign Keys الخاصة بالإلغاء
-- =====================================================================

SET @fk_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_transfer'
      AND CONSTRAINT_NAME = 'stock_transfer_cancelled_by_fk'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

SET @sql := IF(
        @fk_exists = 0,
        'ALTER TABLE stock_transfer ADD CONSTRAINT stock_transfer_cancelled_by_fk FOREIGN KEY (cancelled_by) REFERENCES users(id)',
        'SELECT ''Foreign key stock_transfer_cancelled_by_fk already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


SET @fk_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_transfer'
      AND CONSTRAINT_NAME = 'stock_transfer_reversal_fk'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

SET @sql := IF(
        @fk_exists = 0,
        'ALTER TABLE stock_transfer ADD CONSTRAINT stock_transfer_reversal_fk FOREIGN KEY (reversal_transfer_id) REFERENCES stock_transfer(id)',
        'SELECT ''Foreign key stock_transfer_reversal_fk already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 5) إضافة قيود منع الكمية السالبة في items_stock
-- =====================================================================

SET @constraint_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'items_stock'
      AND CONSTRAINT_NAME = 'items_stock_first_balance_chk'
);

SET @sql := IF(
        @constraint_exists = 0,
        'ALTER TABLE items_stock ADD CONSTRAINT items_stock_first_balance_chk CHECK (first_balance >= 0)',
        'SELECT ''Constraint items_stock_first_balance_chk already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


SET @constraint_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'items_stock'
      AND CONSTRAINT_NAME = 'items_stock_current_quantity_chk'
);

SET @sql := IF(
        @constraint_exists = 0,
        'ALTER TABLE items_stock ADD CONSTRAINT items_stock_current_quantity_chk CHECK (current_quantity >= 0)',
        'SELECT ''Constraint items_stock_current_quantity_chk already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 6) منع تكرار نفس الصنف داخل نفس التحويل
-- =====================================================================
-- قبل إضافة UNIQUE، يجب التأكد من عدم وجود بيانات مكررة.
-- لو وجدت بيانات مكررة، سيعرضها الاستعلام التالي.

SELECT
    stock_transfer_id,
    item_id,
    COUNT(*) AS duplicate_count
FROM stock_transfer_list
GROUP BY stock_transfer_id, item_id
HAVING COUNT(*) > 1;


-- إضافة القيد UNIQUE إذا لم يكن موجودًا.
-- ملاحظة: إذا كان الاستعلام السابق أظهر تكرارات، يجب تنظيفها قبل تشغيل هذا الجزء.

SET @constraint_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_transfer_list'
      AND CONSTRAINT_NAME = 'stock_transfer_list_uk'
);

SET @sql := IF(
        @constraint_exists = 0,
        'ALTER TABLE stock_transfer_list ADD CONSTRAINT stock_transfer_list_uk UNIQUE (stock_transfer_id, item_id)',
        'SELECT ''Constraint stock_transfer_list_uk already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 7) تعديل علاقة stock_transfer_list مع stock_transfer
-- =====================================================================
-- الهدف:
-- منع حذف رأس التحويل إذا كانت له تفاصيل.
-- بدلاً من ON DELETE CASCADE نستخدم ON DELETE RESTRICT.
--
-- ملاحظة:
-- اسم القيد الحالي في قاعدة البيانات هو غالبًا:
-- stock_transfer_list_stock_transfer_id_fk
-- لكن للتأكد سيتم البحث عنه من INFORMATION_SCHEMA ثم حذفه ديناميكيًا.
-- =====================================================================

SET @old_fk_name := (
    SELECT CONSTRAINT_NAME
    FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_transfer_list'
      AND COLUMN_NAME = 'stock_transfer_id'
      AND REFERENCED_TABLE_NAME = 'stock_transfer'
      AND REFERENCED_COLUMN_NAME = 'id'
    LIMIT 1
);

SET @sql := IF(
        @old_fk_name IS NOT NULL,
        CONCAT('ALTER TABLE stock_transfer_list DROP FOREIGN KEY ', @old_fk_name),
        'SELECT ''No foreign key found on stock_transfer_list.stock_transfer_id'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


SET @fk_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_transfer_list'
      AND CONSTRAINT_NAME = 'stock_transfer_list_stock_transfer_id_fk'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

SET @sql := IF(
        @fk_exists = 0,
        'ALTER TABLE stock_transfer_list
            ADD CONSTRAINT stock_transfer_list_stock_transfer_id_fk
            FOREIGN KEY (stock_transfer_id) REFERENCES stock_transfer(id)
            ON UPDATE CASCADE
            ON DELETE RESTRICT',
        'SELECT ''Foreign key stock_transfer_list_stock_transfer_id_fk already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 8) فهارس إضافية لتحسين أداء التحويلات والحركات
-- =====================================================================

SET @index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_transfer'
      AND INDEX_NAME = 'stock_transfer_status_idx'
);

SET @sql := IF(
        @index_exists = 0,
        'CREATE INDEX stock_transfer_status_idx ON stock_transfer(status)',
        'SELECT ''Index stock_transfer_status_idx already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


SET @index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_transfer'
      AND INDEX_NAME = 'stock_transfer_reversal_idx'
);

SET @sql := IF(
        @index_exists = 0,
        'CREATE INDEX stock_transfer_reversal_idx ON stock_transfer(reversal_transfer_id)',
        'SELECT ''Index stock_transfer_reversal_idx already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


SET @index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'items_stock'
      AND INDEX_NAME = 'items_stock_stock_item_idx'
);

SET @sql := IF(
        @index_exists = 0,
        'CREATE INDEX items_stock_stock_item_idx ON items_stock(stock_id, item_id)',
        'SELECT ''Index items_stock_stock_item_idx already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


SET @index_exists := (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'items'
      AND INDEX_NAME = 'items_name_barcode_idx'
);

SET @sql := IF(
        @index_exists = 0,
        'CREATE INDEX items_name_barcode_idx ON items(nameItem, barcode)',
        'SELECT ''Index items_name_barcode_idx already exists'' AS message'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================
-- 9) تحديث View stock_transfer_view
-- =====================================================================
-- يتم عرض التحويلات المرحلة POSTED فقط.
-- التحويلات الملغية لا تدخل في حسابات الرصيد.
-- =====================================================================

CREATE OR REPLACE VIEW stock_transfer_view AS
SELECT st.id,
       st.transfer_date,
       st.stock_from,
       st.stock_to,
       st.status,
       st.cancelled_at,
       st.cancelled_by,
       st.cancel_reason,
       st.reversal_transfer_id,
       stf.stock_name AS name_from,
       stt.stock_name AS name_to,
       stl.id         AS transfer_line_id,
       stl.item_id,
       stl.quantity,
       i.nameItem,
       i.barcode
FROM stock_transfer st
         JOIN stock_transfer_list stl ON st.id = stl.stock_transfer_id
         JOIN items i ON i.id = stl.item_id
         JOIN stocks stf ON stf.stock_id = st.stock_from
         JOIN stocks stt ON stt.stock_id = st.stock_to
WHERE st.status = 'POSTED';


-- =====================================================================
-- 10) View رصيد كل صنف في كل مخزن
-- =====================================================================

CREATE OR REPLACE VIEW v_items_stock_balance AS
SELECT
    i.id AS item_id,
    i.barcode,
    i.nameItem,
    s.stock_id,
    s.stock_name,
    ist.first_balance,
    ist.current_quantity,
    u.unit_id,
    u.unit_name
FROM items_stock ist
         JOIN items i ON i.id = ist.item_id
         JOIN stocks s ON s.stock_id = ist.stock_id
         JOIN units u ON u.unit_id = i.unit_id;


-- =====================================================================
-- 11) View إجمالي رصيد كل صنف في كل المخازن
-- =====================================================================

CREATE OR REPLACE VIEW v_items_total_balance AS
SELECT
    i.id AS item_id,
    i.barcode,
    i.nameItem,
    COALESCE(SUM(ist.current_quantity), 0) AS total_quantity,
    u.unit_id,
    u.unit_name
FROM items i
         LEFT JOIN items_stock ist ON ist.item_id = i.id
         JOIN units u ON u.unit_id = i.unit_id
GROUP BY
    i.id,
    i.barcode,
    i.nameItem,
    u.unit_id,
    u.unit_name;


-- =====================================================================
-- 12) View للتحويلات مع أسماء المستخدمين وحالة الإلغاء
-- =====================================================================

CREATE OR REPLACE VIEW v_stock_transfer_header AS
SELECT
    st.id,
    st.transfer_date,
    st.stock_from,
    sf.stock_name AS stock_from_name,
    st.stock_to,
    stt.stock_name AS stock_to_name,
    st.status,
    st.cancelled_at,
    st.cancelled_by,
    cu.user_name AS cancelled_by_name,
    st.cancel_reason,
    st.reversal_transfer_id,
    st.date_insert,
    st.date_insert,
    st.user_id,
    u.user_name
FROM stock_transfer st
         JOIN stocks sf ON sf.stock_id = st.stock_from
         JOIN stocks stt ON stt.stock_id = st.stock_to
         JOIN users u ON u.id = st.user_id
         LEFT JOIN users cu ON cu.id = st.cancelled_by;


-- =====================================================================
-- 13) View تفاصيل التحويلات
-- =====================================================================

CREATE OR REPLACE VIEW v_stock_transfer_details AS
SELECT
    st.id AS transfer_id,
    st.transfer_date,
    st.stock_from,
    sf.stock_name AS stock_from_name,
    st.stock_to,
    stt.stock_name AS stock_to_name,
    st.status,
    stl.id AS line_id,
    stl.item_id,
    i.barcode,
    i.nameItem,
    stl.quantity,
    u.unit_id,
    u.unit_name,
    st.user_id,
    usr.user_name
FROM stock_transfer st
         JOIN stock_transfer_list stl ON stl.stock_transfer_id = st.id
         JOIN stocks sf ON sf.stock_id = st.stock_from
         JOIN stocks stt ON stt.stock_id = st.stock_to
         JOIN items i ON i.id = stl.item_id
         JOIN units u ON u.unit_id = i.unit_id
         JOIN users usr ON usr.id = st.user_id;


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
WHERE first_balance < 0
   OR current_quantity < 0;

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