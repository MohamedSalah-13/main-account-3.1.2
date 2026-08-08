-- =====================================================================
-- V3 - Multiple barcodes per item.
--
-- `items.barcode` holds one code; this table carries the extra ones, so a
-- product sold under several manufacturer/retailer barcodes scans correctly
-- either way. Deleting the item takes its barcodes with it.
-- =====================================================================

CREATE TABLE IF NOT EXISTS item_barcodes
(
    id         INT AUTO_INCREMENT PRIMARY KEY,
    item_id    INT                                 NOT NULL,
    barcode    VARCHAR(200)                        NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT item_barcodes_barcode_uindex UNIQUE (barcode),
    CONSTRAINT item_barcodes_items_id_fk FOREIGN KEY (item_id) REFERENCES items (id) ON UPDATE CASCADE ON DELETE CASCADE
);

-- CREATE INDEX has no IF NOT EXISTS in MySQL, so guard it against installs
-- that already ran this by hand.
SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE()
               AND table_name = 'item_barcodes'
               AND index_name = 'item_barcodes_item_id_idx');
SET @sql := IF(@idx = 0, 'CREATE INDEX item_barcodes_item_id_idx ON item_barcodes (item_id)', 'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
