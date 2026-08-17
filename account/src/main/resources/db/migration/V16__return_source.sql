-- =====================================================================
-- V16 - Where a return came from.
--
-- A return has never pointed at what it reverses. total_sales_re carried a
-- commented-out total_sales_id (TotalsSalesReturnDao.getData/getUpdateData/map
-- still name it, dead) and total_buy_re never had one at all. Without it a
-- return cannot be checked against what was actually sold: nothing stops
-- returning five units of an item a customer bought three of, nothing offers
-- the batch or the price the sale itself used, and nothing tells the invoice
-- screen a document has since been partly returned.
--
-- Two columns per return family:
--
--   source_invoice_number  the invoice this return reverses - total_sales or
--                          total_buy, by DocumentType.reverses(). Nullable:
--                          every return written before this migration has
--                          none, and a shop that returns goods without asking
--                          for the original invoice needs to keep working.
--   return_reason           free-form for now; features/returns/ReturnReason
--                          turns it into an enum once the entry screen asks
--                          for one. VARCHAR rather than an enum column so a
--                          reason can be added without another migration.
--
-- And one on each return's lines:
--
--   source_line_id           the sales/purchase line (its own `id`, not the
--                          item column those families confusingly call `num`)
--                          this return line reverses. Lets a future
--                          ReturnableRepository read the original price and
--                          buy_price straight off the row instead of the
--                          item's current ones - see CLAUDE.md's note on
--                          SalesInvoiceReturn.object_TableData.
--
-- ON DELETE SET NULL on both header keys, deliberately not CASCADE and not a
-- plain refusal: deleting the original invoice must not take an already-
-- posted return down with it, and must not be blocked by a return that
-- happens to reference it either - DeleteRegistry's rule is that only a
-- non-cascading key belongs there, and this one is not meant to hold a
-- delete back. The return keeps standing; it just stops knowing what it
-- reversed, exactly as an unmigrated historical return already does.
-- =====================================================================

-- add_index_if_missing is not a standing procedure: V1 and V4 each create it, use it
-- for their own file, and drop it again, so every migration that wants it declares
-- its own copy the same way.
DROP PROCEDURE IF EXISTS add_index_if_missing;
DELIMITER $$
CREATE PROCEDURE add_index_if_missing(IN p_table VARCHAR(64),
                                      IN p_index VARCHAR(64),
                                      IN p_cols  VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE table_schema = DATABASE()
                     AND table_name   = p_table
                     AND index_name   = p_index)
    THEN
        SET @s = CONCAT('CREATE INDEX `', p_index, '` ON `', p_table, '` (', p_cols, ')');
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END$$
DELIMITER ;

SET @col := (SELECT COUNT(*)
             FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'total_sales_re'
               AND column_name = 'source_invoice_number');
SET @sql := IF(@col = 0,
               'ALTER TABLE total_sales_re ADD COLUMN source_invoice_number BIGINT NULL AFTER id',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*)
             FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'total_sales_re'
               AND column_name = 'return_reason');
SET @sql := IF(@col = 0,
               'ALTER TABLE total_sales_re ADD COLUMN return_reason VARCHAR(32) NULL',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk := (SELECT COUNT(*)
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = 'total_sales_re'
              AND constraint_name = 'total_sales_re_source_invoice_fk');
SET @sql := IF(@fk = 0,
               'ALTER TABLE total_sales_re ADD CONSTRAINT total_sales_re_source_invoice_fk
                    FOREIGN KEY (source_invoice_number) REFERENCES total_sales (invoice_number)
                        ON DELETE SET NULL',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CALL add_index_if_missing('total_sales_re', 'total_sales_re_source_invoice_idx', 'source_invoice_number');


SET @col := (SELECT COUNT(*)
             FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'total_buy_re'
               AND column_name = 'source_invoice_number');
SET @sql := IF(@col = 0,
               'ALTER TABLE total_buy_re ADD COLUMN source_invoice_number BIGINT NULL AFTER id',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*)
             FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'total_buy_re'
               AND column_name = 'return_reason');
SET @sql := IF(@col = 0,
               'ALTER TABLE total_buy_re ADD COLUMN return_reason VARCHAR(32) NULL',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk := (SELECT COUNT(*)
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = 'total_buy_re'
              AND constraint_name = 'total_buy_re_source_invoice_fk');
SET @sql := IF(@fk = 0,
               'ALTER TABLE total_buy_re ADD CONSTRAINT total_buy_re_source_invoice_fk
                    FOREIGN KEY (source_invoice_number) REFERENCES total_buy (invoice_number)
                        ON DELETE SET NULL',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CALL add_index_if_missing('total_buy_re', 'total_buy_re_source_invoice_idx', 'source_invoice_number');


SET @col := (SELECT COUNT(*)
             FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'sales_re'
               AND column_name = 'source_line_id');
SET @sql := IF(@col = 0,
               'ALTER TABLE sales_re ADD COLUMN source_line_id INT NULL AFTER item_id',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk := (SELECT COUNT(*)
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = 'sales_re'
              AND constraint_name = 'sales_re_source_line_fk');
SET @sql := IF(@fk = 0,
               'ALTER TABLE sales_re ADD CONSTRAINT sales_re_source_line_fk
                    FOREIGN KEY (source_line_id) REFERENCES sales (id)
                        ON DELETE SET NULL',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CALL add_index_if_missing('sales_re', 'sales_re_source_line_idx', 'source_line_id');


SET @col := (SELECT COUNT(*)
             FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'purchase_re'
               AND column_name = 'source_line_id');
SET @sql := IF(@col = 0,
               'ALTER TABLE purchase_re ADD COLUMN source_line_id INT NULL AFTER item_id',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk := (SELECT COUNT(*)
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = 'purchase_re'
              AND constraint_name = 'purchase_re_source_line_fk');
SET @sql := IF(@fk = 0,
               'ALTER TABLE purchase_re ADD CONSTRAINT purchase_re_source_line_fk
                    FOREIGN KEY (source_line_id) REFERENCES purchase (id)
                        ON DELETE SET NULL',
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CALL add_index_if_missing('purchase_re', 'purchase_re_source_line_idx', 'source_line_id');

DROP PROCEDURE IF EXISTS add_index_if_missing;
