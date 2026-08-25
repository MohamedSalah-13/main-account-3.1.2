-- =====================================================================
-- V17 - The record that two items were made one.
--
-- Merging an item repoints every line it ever appeared on and then deletes
-- the row. Nothing else in the schema would remember it happened: audit_log
-- is written by triggers on `items`, `custom`, `suppliers`, `total_sales`,
-- `total_buy` and `treasury` - not on `sales`, `purchase` or their returns -
-- so the thousands of lines that changed hands leave no trace at all, and the
-- DELETE on `items` records only that a row went away, never where its
-- history went.
--
-- Two tables:
--
--   item_merge        one row per merge. The source's name, barcode and
--                     opening balance are copied in as values, because the
--                     row they came from is gone by the time the log is read.
--   item_merge_lines  how many rows moved, per table. A child table rather
--                     than a column per table so that declaring a new
--                     reference in ItemReferenceRegistry needs no migration.
--
-- No foreign key to `items` on either end, deliberately. The source is
-- deleted by definition - a key on it could never hold - and a key on the
-- target would refuse to let that item be deleted later on the strength of a
-- log entry, which turns a record of the past into a constraint on the
-- future. The same reasoning covers user_id: the name is copied beside it.
--
-- Nothing here is dated by a business date, so the accounting lock does not
-- reach it: merging changes no figure, only which item a figure is filed
-- under. locked_period_lines records how many of the moved lines were inside
-- a closed period, so the fact is on the record even though it was allowed.
-- =====================================================================

CREATE TABLE IF NOT EXISTS item_merge
(
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    target_item_id       INT                                 NOT NULL,
    target_item_name     VARCHAR(200)                        NOT NULL,
    source_item_id       INT                                 NOT NULL,
    source_item_name     VARCHAR(200)                        NOT NULL,
    source_barcode       VARCHAR(200)                        NULL,
    source_first_balance DECIMAL(14, 3) DEFAULT 0            NOT NULL,
    locked_period_lines  INT            DEFAULT 0            NOT NULL,
    merged_at            DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    user_id              INT            DEFAULT 1            NOT NULL,
    user_name            VARCHAR(50)                         NULL,
    INDEX item_merge_target_idx (target_item_id),
    INDEX item_merge_merged_at_idx (merged_at)
);

CREATE TABLE IF NOT EXISTS item_merge_lines
(
    id         INT AUTO_INCREMENT PRIMARY KEY,
    merge_id   INT           NOT NULL,
    table_name VARCHAR(64)   NOT NULL,
    rows_moved INT DEFAULT 0 NOT NULL,
    CONSTRAINT item_merge_lines_merge_fk FOREIGN KEY (merge_id) REFERENCES item_merge (id)
        ON UPDATE CASCADE ON DELETE CASCADE
);
