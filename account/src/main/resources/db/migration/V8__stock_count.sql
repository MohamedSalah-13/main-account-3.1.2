-- Physical stock counts (الجرد الفعلي).
--
-- Until now the only way to correct a balance was to edit items.first_balance, which
-- rewrites what the opening balance was and leaves no record that anyone corrected
-- anything. A count is instead a dated document with lines, and posting it produces a
-- movement like any other - R__views.sql folds it into quantity_items_table as a
-- seventh aggregate beside purchases, sales, their returns and the transfers.
--
-- A count is a draft until it is posted, and only a posted count moves any stock. That
-- is what lets a shop count over an afternoon, correcting entries as it goes, without
-- the balances shifting under the people still counting.

CREATE TABLE IF NOT EXISTS stock_count
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    stock_id    INT                                 NOT NULL,
    count_date  DATE                                NOT NULL,
    -- DRAFT or POSTED. Only POSTED rows reach the balance; see R__views.sql.
    status      VARCHAR(10) DEFAULT 'DRAFT'         NOT NULL,
    notes       VARCHAR(255)                        NULL,
    posted_at   DATETIME                            NULL,
    date_insert DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id     INT         DEFAULT 1               NOT NULL,
    CONSTRAINT stock_count_stocks_stock_id_fk FOREIGN KEY (stock_id) REFERENCES stocks (stock_id),
    CONSTRAINT stock_count_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT stock_count_status_chk CHECK (status IN ('DRAFT', 'POSTED'))
);

-- One line per item and unit counted.
--
-- system_qty is the balance at the moment the line was added, in base units, and it is
-- stored rather than recomputed on posting: the difference a count posts has to be the
-- difference the person counting saw, not one that moved while they were counting.
--
-- counted_qty is in the unit named by unit_id, and type_value is the factor that unit
-- had at the time - the same arrangement the invoice lines use, and for the same
-- reason: changing an item's factor later must not silently rewrite what a past count
-- meant.
CREATE TABLE IF NOT EXISTS stock_count_lines
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    count_id    INT                        NOT NULL,
    item_id     INT                        NOT NULL,
    unit_id     INT            DEFAULT 1   NOT NULL,
    type_value  DECIMAL(14, 3) DEFAULT 1   NOT NULL,
    system_qty  DECIMAL(14, 3) DEFAULT 0   NOT NULL,
    counted_qty DECIMAL(14, 3) DEFAULT 0   NOT NULL,
    CONSTRAINT stock_count_lines_stock_count_id_fk FOREIGN KEY (count_id) REFERENCES stock_count (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT stock_count_lines_items_id_fk FOREIGN KEY (item_id) REFERENCES items (id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT stock_count_lines_units_unit_id_fk FOREIGN KEY (unit_id) REFERENCES units (unit_id),
    CONSTRAINT stock_count_lines_uk UNIQUE (count_id, item_id, unit_id)
);

CREATE INDEX stock_count_lines_item_id_index ON stock_count_lines (item_id);
CREATE INDEX stock_count_status_index ON stock_count (status);

-- Two permissions rather than one: entering a count is clerical work and posting it
-- changes every balance on the sheet. Ids continue from 87, the last one in
-- UserPermissionType - 25..30 are free, being the removed warehouse permissions, but
-- reusing an id would silently hand the new permission to anyone an old install still
-- has a row for.
INSERT INTO permission (id, name_permission, description)
VALUES (88, 'stock_count_show', 'شاشة الجرد الفعلي'),
       (89, 'stock_count_post', 'ترحيل الجرد')
ON DUPLICATE KEY UPDATE description = VALUES(description);
