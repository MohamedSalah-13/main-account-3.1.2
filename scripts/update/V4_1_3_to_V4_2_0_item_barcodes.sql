-- ======================================================
-- Update from V4.1.3 to V4.2.0
-- ======================================================

CREATE TABLE IF NOT EXISTS item_barcodes
(
    id         INT AUTO_INCREMENT PRIMARY KEY,
    item_id    INT                                NOT NULL,
    barcode    VARCHAR(200)                       NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT item_barcodes_barcode_uindex UNIQUE (barcode),
    CONSTRAINT item_barcodes_items_id_fk FOREIGN KEY (item_id) REFERENCES items (id) ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE INDEX item_barcodes_item_id_idx ON item_barcodes (item_id);
