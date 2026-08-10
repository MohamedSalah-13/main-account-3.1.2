-- =====================================================================
-- #####################################################################
-- ## Tables and indexes                                              ##
-- #####################################################################
-- Repeated from V1 on purpose. A client that Flyway stamped rather than
-- executed never ran V1, so a v3.x database reaching this file has none of the
-- tables added since: stock_movements, items_package, user_shifts, audit_log,
-- treasury_movements, system_info, database_migrations. The column work further
-- down addresses tables that already exist; these statements supply the ones
-- that do not, and every one of them is IF NOT EXISTS, so a database that
-- already has them is untouched.
-- #####################################################################

-- #####################################################################
-- ## Tables and indexes                             (was V001_tables) ##
-- #####################################################################

-- =====================================================================
-- 1) Base / Lookup tables
-- =====================================================================

CREATE TABLE IF NOT EXISTS company
(
    comp_id      INT AUTO_INCREMENT PRIMARY KEY,
    comp_name    VARCHAR(50)                         NOT NULL,
    comp_tel     VARCHAR(50)                         NULL,
    comp_address VARCHAR(100)                        NULL,
    comp_tax     VARCHAR(100)                        NULL,
    comp_comm    VARCHAR(50)                         NULL,
    comp_image   LONGBLOB                            NULL,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS expenses
(
    id            INT         NOT NULL PRIMARY KEY,
    expenses_name VARCHAR(50) NOT NULL,
    CONSTRAINT expenses_pk UNIQUE (expenses_name)
);

CREATE TABLE IF NOT EXISTS jobs
(
    id       INT         NOT NULL PRIMARY KEY,
    job_name VARCHAR(20) NOT NULL,
    CONSTRAINT jobs_pk_2 UNIQUE (job_name)
);

CREATE TABLE IF NOT EXISTS permission
(
    id              INT AUTO_INCREMENT PRIMARY KEY,
    name_permission VARCHAR(50) NOT NULL,
    description     VARCHAR(50) NULL,
    CONSTRAINT users_permission_pk UNIQUE (name_permission)
);

CREATE TABLE IF NOT EXISTS table_area
(
    id        INT AUTO_INCREMENT PRIMARY KEY,
    area_name VARCHAR(100) NOT NULL,
    CONSTRAINT table_area_pk_2 UNIQUE (area_name)
);

CREATE TABLE IF NOT EXISTS users
(
    id             INT AUTO_INCREMENT PRIMARY KEY,
    user_name      VARCHAR(30)                         NULL,
    user_pass      VARCHAR(255)                        NULL,
    user_activity  TINYINT   DEFAULT 1                 NOT NULL,
    user_available TINYINT   DEFAULT 0                 NOT NULL,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT users_pk UNIQUE (user_name),
    CONSTRAINT users_activity_chk CHECK (user_activity IN (0, 1)),
    CONSTRAINT users_available_chk CHECK (user_available IN (0, 1))
);

-- =====================================================================
-- 2) Tables depending mainly on users
-- =====================================================================

# CREATE TABLE IF NOT EXISTS processes_data
# (
#     id             INT AUTO_INCREMENT PRIMARY KEY,
#     user_id        INT                                 NOT NULL,
#     processes_name VARCHAR(50)                         NOT NULL,
#     table_name     VARCHAR(50)                         NOT NULL,
#     table_id       INT                                 NOT NULL,
#     date_insert    DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
#     updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
#     notes          LONGTEXT                            NULL,
#     CONSTRAINT processes_data_users_id_fk
#         FOREIGN KEY (user_id) REFERENCES users (id)
#             ON UPDATE CASCADE ON DELETE CASCADE
# );

# CREATE INDEX processes_data_table_idx ON processes_data (table_name, table_id);
# CREATE INDEX processes_data_date_idx ON processes_data (date_insert);

CREATE TABLE IF NOT EXISTS main_group
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name_g      VARCHAR(50)                         NOT NULL,
    date_insert DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id     INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT main_group_pk UNIQUE (name_g),
    CONSTRAINT main_group_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS stocks
(
    stock_id      INT AUTO_INCREMENT PRIMARY KEY,
    stock_name    VARCHAR(50)                         NOT NULL,
    stock_address VARCHAR(50)                         NULL,
    date_insert   DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id       INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT stocks_pk UNIQUE (stock_name),
    CONSTRAINT stocks_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS treasury
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    t_name      VARCHAR(50)                              NOT NULL,
    amount      DECIMAL(14, 2) DEFAULT 0                 NOT NULL,
    date_insert DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id     INT            DEFAULT 1                 NOT NULL,
    CONSTRAINT treasury_pk UNIQUE (t_name),
    CONSTRAINT treasury_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS type_price
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50)                         NOT NULL,
    date_insert DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id     INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT items_price_pk UNIQUE (name),
    CONSTRAINT type_price_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS units
(
    unit_id     INT AUTO_INCREMENT PRIMARY KEY,
    unit_name   VARCHAR(50)                              NOT NULL,
    value_d     DECIMAL(14, 3) DEFAULT 1                 NOT NULL,
    date_insert DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id     INT            DEFAULT 1                 NOT NULL,
    CONSTRAINT units_pk UNIQUE (unit_name),
    CONSTRAINT units_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

-- =====================================================================
-- 3) Main master data
-- =====================================================================

CREATE TABLE IF NOT EXISTS employees
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    column_name VARCHAR(50)                         NOT NULL,
    birth_date  DATE                                NOT NULL,
    hire_date   DATE                                NOT NULL,
    salary      DECIMAL(14, 2)                      NOT NULL,
    email       VARCHAR(200)                        NULL,
    tel         VARCHAR(200)                        NULL,
    address     VARCHAR(200)                        NULL,
    image       LONGBLOB                            NULL,
    job         INT                                 NOT NULL,
    date_insert DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id     INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT employees_pk2 UNIQUE (column_name),
    CONSTRAINT employees_jobs_id_fk FOREIGN KEY (job) REFERENCES jobs (id),
    CONSTRAINT employees_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS sub_group
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50)                         NOT NULL,
    main_id     INT                                 NOT NULL,
    date_insert DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id     INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT sub_group_pk UNIQUE (name),
    CONSTRAINT sub_group_main_group_id_fk FOREIGN KEY (main_id) REFERENCES main_group (id),
    CONSTRAINT sub_group_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS suppliers
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(50)                              NOT NULL,
    tel           VARCHAR(50)                              NULL,
    address       VARCHAR(255)                             NULL,
    notes         LONGTEXT                                 NULL,
    first_balance DECIMAL(14, 2) DEFAULT 0                 NOT NULL,
    table_id      INT            DEFAULT 1                 NOT NULL,
    date_insert   DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id       INT            DEFAULT 1                 NOT NULL,
    area_id       INT            DEFAULT 1                 NOT NULL,
    CONSTRAINT suppliers_pk UNIQUE (name),
    CONSTRAINT suppliers_table_area_id_fk FOREIGN KEY (area_id) REFERENCES table_area (id),
    CONSTRAINT suppliers_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS custom
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100)                             NOT NULL,
    tel           VARCHAR(50)                              NULL,
    address       VARCHAR(200)                             NULL,
    notes         LONGTEXT                                 NULL,
    limit_num     DECIMAL(14, 2)                           NOT NULL,
    first_balance DECIMAL(14, 2) DEFAULT 0                 NOT NULL,
    price_id      INT            DEFAULT 1                 NOT NULL,
    table_id      INT            DEFAULT 1                 NOT NULL,
    created_at    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id       INT            DEFAULT 1                 NOT NULL,
    area_id       INT            DEFAULT 1                 NOT NULL,
    CONSTRAINT custom_pk UNIQUE (name),
    CONSTRAINT custom_items_price_id_fk FOREIGN KEY (price_id) REFERENCES type_price (id),
    CONSTRAINT custom_table_area_id_fk FOREIGN KEY (area_id) REFERENCES table_area (id),
    CONSTRAINT custom_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

-- =====================================================================
-- 4) Items
-- =====================================================================

CREATE TABLE IF NOT EXISTS items
(
    id                       INT AUTO_INCREMENT PRIMARY KEY,
    barcode                  VARCHAR(200)                             NOT NULL,
    nameItem                 VARCHAR(200)                             NOT NULL,
    sub_num                  INT                                      NOT NULL,
    buy_price                DECIMAL(14, 2) DEFAULT 0                 NOT NULL,
    sel_price1               DECIMAL(14, 2) DEFAULT 0                 NOT NULL,
    sel_price2               DECIMAL(14, 2) DEFAULT 0                 NOT NULL,
    sel_price3               DECIMAL(14, 2) DEFAULT 0                 NOT NULL,
    unit_id                  INT                                      NOT NULL,
    mini_quantity            DECIMAL(14, 3) DEFAULT 1                 NOT NULL,
    first_balance            DECIMAL(14, 3) DEFAULT 0                 NOT NULL,
    item_image               LONGBLOB                                 NULL,
    item_active              TINYINT(1)     DEFAULT 1                 NOT NULL,
    item_has_validity        TINYINT(1)     DEFAULT 0                 NOT NULL,
    number_validity_days     INT            DEFAULT 0                 NOT NULL,
    alert_days_before_expire INT            DEFAULT 0                 NOT NULL,
    item_has_package         TINYINT(1)     DEFAULT 0                 NOT NULL,
    created_at               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at               TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id                  INT            DEFAULT 1                 NOT NULL,
    CONSTRAINT items_barcode_uindex UNIQUE (barcode),
    CONSTRAINT items_pk UNIQUE (nameItem),
    CONSTRAINT items_sub_group_id_fk FOREIGN KEY (sub_num) REFERENCES sub_group (id),
    CONSTRAINT items_units_unit_id_fk FOREIGN KEY (unit_id) REFERENCES units (unit_id),
    CONSTRAINT items_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS items_package
(
    id         INT AUTO_INCREMENT PRIMARY KEY,
    item_id    INT                                NOT NULL,
    package_id INT                                NOT NULL,
    quantity   DECIMAL(14, 3)                     NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT items_package_item_id_fk
        FOREIGN KEY (item_id) REFERENCES items (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT items_package_package_id_fk
        FOREIGN KEY (package_id) REFERENCES items (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT items_package_quantity_chk CHECK (quantity > 0)
);

-- =====================================================================
-- MySQL has no CREATE INDEX IF NOT EXISTS, so every index below goes
-- through this helper. It checks information_schema first, which makes the
-- whole file safe to re-run on a database that already has the indexes.
-- =====================================================================
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

CALL add_index_if_missing('items_package', 'items_package_item_idx', 'item_id');
CALL add_index_if_missing('items_package', 'items_package_package_idx', 'package_id');

CREATE TABLE IF NOT EXISTS items_stock
(
    id               INT AUTO_INCREMENT PRIMARY KEY,
    item_id          INT                      NOT NULL,
    stock_id         INT                      NOT NULL,
    first_balance    DECIMAL(14, 3) DEFAULT 0 NOT NULL,
    current_quantity DECIMAL(14, 3) DEFAULT 0 NOT NULL,
    CONSTRAINT items_stock_items_id_fk
        FOREIGN KEY (item_id) REFERENCES items (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT items_stock_stocks_stock_id_fk
        FOREIGN KEY (stock_id) REFERENCES stocks (stock_id),
    CONSTRAINT items_stock_uk UNIQUE (item_id, stock_id)
);

CREATE TABLE IF NOT EXISTS items_units
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    items_id      INT                                 NOT NULL,
    items_barcode VARCHAR(50)                         NOT NULL,
    unit          INT                                 NOT NULL,
    quantity      DECIMAL(14, 3)                      NOT NULL,
    buy_price     DECIMAL(14, 2)                      NOT NULL,
    sel_price     DECIMAL(14, 2)                      NOT NULL,
    date_insert   DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id       INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT items_units_pk UNIQUE (items_barcode),
    CONSTRAINT items_units_items_id_fk
        FOREIGN KEY (items_id) REFERENCES items (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT items_units_units_unit_id_fk FOREIGN KEY (unit) REFERENCES units (unit_id),
    CONSTRAINT items_units_quantity_chk CHECK (quantity > 0)
);

CALL add_index_if_missing('items_units', 'items_units_items_num_fk', 'items_id');

-- =====================================================================
-- 5) Stock movements and transfers
-- =====================================================================

CREATE TABLE IF NOT EXISTS stock_movements
(
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id           INT                                      NOT NULL,
    stock_id          INT                                      NOT NULL,
    movement_date     DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,

    movement_type     VARCHAR(30)                              NOT NULL,
    quantity_in       DECIMAL(15, 3) DEFAULT 0                 NOT NULL,
    quantity_out      DECIMAL(15, 3) DEFAULT 0                 NOT NULL,

    unit_id           INT                                      NULL,
    unit_value        DECIMAL(15, 3) DEFAULT 1                 NOT NULL,

    reference_type    VARCHAR(30)                              NULL,
    reference_id      BIGINT                                   NULL,
    reference_line_id BIGINT                                   NULL,

    notes             TEXT                                     NULL,
    user_id           INT            DEFAULT 1                 NOT NULL,

    CONSTRAINT stock_movements_items_id_fk FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT stock_movements_stocks_stock_id_fk FOREIGN KEY (stock_id) REFERENCES stocks (stock_id),
    CONSTRAINT stock_movements_units_unit_id_fk FOREIGN KEY (unit_id) REFERENCES units (unit_id),
    CONSTRAINT stock_movements_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),

    CONSTRAINT stock_movements_quantity_chk
        CHECK (
            (quantity_in > 0 AND quantity_out = 0)
                OR
            (quantity_in = 0 AND quantity_out > 0)
            ),

    CONSTRAINT stock_movements_unit_value_chk CHECK (unit_value > 0),

    CONSTRAINT stock_movements_type_chk
        CHECK (movement_type IN (
                                 'OPENING',
                                 'PURCHASE',
                                 'PURCHASE_RETURN',
                                 'SALE',
                                 'SALE_RETURN',
                                 'TRANSFER_IN',
                                 'TRANSFER_OUT',
                                 'INVENTORY_ADJUST_IN',
                                 'INVENTORY_ADJUST_OUT'
            )),

    CONSTRAINT stock_movements_reference_type_chk
        CHECK (
            reference_type IS NULL
                OR reference_type IN (
                                      'ITEM',
                                      'PURCHASE',
                                      'PURCHASE_RETURN',
                                      'SALE',
                                      'SALE_RETURN',
                                      'STOCK_TRANSFER',
                                      'INVENTORY'
                )
            )
);

CALL add_index_if_missing('stock_movements', 'idx_stock_movements_item_stock_date', 'item_id, stock_id, movement_date');

CALL add_index_if_missing('stock_movements', 'idx_stock_movements_reference', 'reference_type, reference_id');

CALL add_index_if_missing('stock_movements', 'idx_stock_movements_stock_date', 'stock_id, movement_date');

CREATE TABLE IF NOT EXISTS stock_transfer
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    transfer_date DATE                                NOT NULL,
    stock_from    INT                                 NOT NULL,
    stock_to      INT                                 NOT NULL,
    date_insert   DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id       INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT stock_transfer_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT stock_transfer_from_fk FOREIGN KEY (stock_from) REFERENCES stocks (stock_id),
    CONSTRAINT stock_transfer_to_fk FOREIGN KEY (stock_to) REFERENCES stocks (stock_id),
    CONSTRAINT stock_transfer_not_same_chk CHECK (stock_from <> stock_to)
);

CALL add_index_if_missing('stock_transfer', 'stock_transfer_stocks_stock_id_fk', 'stock_from');
CALL add_index_if_missing('stock_transfer', 'stock_transfer_stocks_stock_id_fk_2', 'stock_to');
CALL add_index_if_missing('stock_transfer', 'stock_transfer_date_idx', 'transfer_date');

CREATE TABLE IF NOT EXISTS stock_transfer_list
(
    id                INT AUTO_INCREMENT PRIMARY KEY,
    stock_transfer_id INT            NOT NULL,
    item_id           INT            NOT NULL,
    quantity          DECIMAL(14, 3) NOT NULL,

    CONSTRAINT stock_transfer_list_stock_transfer_id_fk
        FOREIGN KEY (stock_transfer_id) REFERENCES stock_transfer (id)
            ON UPDATE CASCADE ON DELETE CASCADE,

    CONSTRAINT stock_transfer_list_items_id_fk
        FOREIGN KEY (item_id) REFERENCES items (id),

    CONSTRAINT stock_transfer_list_quantity_chk
        CHECK (quantity > 0)
);

CALL add_index_if_missing('stock_transfer_list', 'stock_transfer_list_item_idx', 'item_id');

-- =====================================================================
-- 6) Treasury / Expenses
-- =====================================================================

CREATE TABLE IF NOT EXISTS treasury_deposit_expenses
(
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    statement           VARCHAR(50)                         NOT NULL,
    date_inter          DATE                                NOT NULL,
    amount              DECIMAL(14, 2)                      NOT NULL,
    description_data    TEXT                                NULL,
    deposit_or_expenses TINYINT   DEFAULT 1                 NOT NULL,
    treasury_id         INT       DEFAULT 1                 NOT NULL,
    date_insert         DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id             INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT treasury_deposit_expenses_treasury_id_fk FOREIGN KEY (treasury_id) REFERENCES treasury (id),
    CONSTRAINT treasury_deposit_expenses_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT treasury_deposit_expenses_type_chk CHECK (deposit_or_expenses IN (1, 2))
);

CALL add_index_if_missing('treasury_deposit_expenses', 'treasury_deposit_expenses_date_idx', 'date_inter');
CALL add_index_if_missing('treasury_deposit_expenses', 'treasury_deposit_expenses_treasury_idx', 'treasury_id, date_inter');

CREATE TABLE IF NOT EXISTS treasury_transfers
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    treasury_from INT                                 NOT NULL,
    treasury_to   INT                                 NOT NULL,
    amount        DECIMAL(14, 2)                      NOT NULL,
    transfer_date DATE                                NOT NULL,
    notes         LONGTEXT                            NULL,
    date_insert   DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id       INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT treasury_transfers_treasury_id_fk FOREIGN KEY (treasury_from) REFERENCES treasury (id),
    CONSTRAINT treasury_transfers_treasury_id_fk_2 FOREIGN KEY (treasury_to) REFERENCES treasury (id),
    CONSTRAINT treasury_transfers_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT treasury_transfers_not_same_chk CHECK (treasury_from <> treasury_to),
    CONSTRAINT treasury_transfers_amount_chk CHECK (amount > 0)
);

CALL add_index_if_missing('treasury_transfers', 'treasury_transfers_date_idx', 'transfer_date');

CREATE TABLE IF NOT EXISTS expenses_details
(
    id          INT AUTO_INCREMENT PRIMARY KEY,
    type_code   INT                                      NOT NULL,
    date        DATE                                     NOT NULL,
    amount      DECIMAL(14, 2) DEFAULT 0                 NOT NULL,
    notes       VARCHAR(255)                             NULL,
    emp_id      INT            DEFAULT 0                 NOT NULL,
    treasury_id INT            DEFAULT 1                 NOT NULL,
    date_insert DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id     INT            DEFAULT 1                 NOT NULL,
    CONSTRAINT expenses_details_expenses_id_fk FOREIGN KEY (type_code) REFERENCES expenses (id),
    CONSTRAINT expenses_details_treasury_id_fk FOREIGN KEY (treasury_id) REFERENCES treasury (id),
    CONSTRAINT expenses_details_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT expenses_details_amount_chk CHECK (amount >= 0)
);

CALL add_index_if_missing('expenses_details', 'expenses_details_date_idx', 'date');
CALL add_index_if_missing('expenses_details', 'expenses_details_treasury_idx', 'treasury_id, date');

CREATE TABLE IF NOT EXISTS expense_salary
(
    employee_id         INT NOT NULL,
    expenses_details_id INT NOT NULL,
    CONSTRAINT expense_salary_employees_id_fk FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT expense_salary_expenses_details_id_fk
        FOREIGN KEY (expenses_details_id) REFERENCES expenses_details (id)
            ON UPDATE CASCADE ON DELETE CASCADE
);

-- =====================================================================
-- 7) Invoice totals
-- =====================================================================

CREATE TABLE IF NOT EXISTS total_buy
(
    invoice_number BIGINT                              NOT NULL PRIMARY KEY,
    sup_code       INT                                 NOT NULL,
    invoice_type   TINYINT   DEFAULT 1                 NOT NULL,
    invoice_date   DATE                                NOT NULL,
    total          DECIMAL(14, 2)                      NOT NULL,
    discount       DECIMAL(14, 2)                      NOT NULL,
    paid_up        DECIMAL(14, 2)                      NOT NULL COMMENT 'paid from the treasury مدفوع نقدا من الخزينة',
    stock_id       INT       DEFAULT 1                 NOT NULL,
    treasury_id    INT       DEFAULT 1                 NOT NULL,
    notes          LONGTEXT                            NULL,
    table_id       INT       DEFAULT 3                 NOT NULL,
    date_insert    DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id        INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT total_buy_stocks_stock_id_fk FOREIGN KEY (stock_id) REFERENCES stocks (stock_id),
    CONSTRAINT total_buy_suppliers_sup_id_fk FOREIGN KEY (sup_code) REFERENCES suppliers (id),
    CONSTRAINT total_buy_treasury_id_fk FOREIGN KEY (treasury_id) REFERENCES treasury (id),
    CONSTRAINT total_buy_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT total_buy_invoice_type_chk CHECK (invoice_type IN (1, 2))
);

CALL add_index_if_missing('total_buy', 'total_buy_sup_code_fk', 'sup_code');
CALL add_index_if_missing('total_buy', 'total_buy_date_idx', 'invoice_date');
CALL add_index_if_missing('total_buy', 'total_buy_treasury_idx', 'treasury_id, invoice_date');

CREATE TABLE IF NOT EXISTS total_buy_re
(
    id               BIGINT                              NOT NULL PRIMARY KEY,
    sup_id           INT                                 NOT NULL,
    invoice_date     DATE                                NOT NULL,
    invoice_type     TINYINT   DEFAULT 1                 NOT NULL,
    total            DECIMAL(14, 2)                      NOT NULL,
    discount         DECIMAL(14, 2)                      NOT NULL,
    paid_to_treasury DECIMAL(14, 2)                      NOT NULL COMMENT 'Paid to the treasury مدفوعات الى الخزينة',
    stock_id         INT                                 NOT NULL,
    treasury_id      INT       DEFAULT 1                 NOT NULL,
    notes            LONGTEXT                            NULL,
    table_id         INT       DEFAULT 4                 NOT NULL,
    date_insert      DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id          INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT total_buy_re_stocks_stock_id_fk FOREIGN KEY (stock_id) REFERENCES stocks (stock_id),
    CONSTRAINT total_buy_re_suppliers_sup_id_fk FOREIGN KEY (sup_id) REFERENCES suppliers (id),
    CONSTRAINT total_buy_re_treasury_id_fk FOREIGN KEY (treasury_id) REFERENCES treasury (id),
    CONSTRAINT total_buy_re_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT total_buy_re_invoice_type_chk CHECK (invoice_type IN (1, 2))
);

CALL add_index_if_missing('total_buy_re', 'total_buy_re_date_idx', 'invoice_date');
CALL add_index_if_missing('total_buy_re', 'total_buy_re_treasury_idx', 'treasury_id, invoice_date');
CALL add_index_if_missing('total_buy_re', 'total_buy_re_sup_idx', 'sup_id');

CREATE TABLE IF NOT EXISTS total_sales
(
    invoice_number BIGINT                              NOT NULL PRIMARY KEY,
    sup_code       INT                                 NOT NULL,
    invoice_type   TINYINT   DEFAULT 1                 NOT NULL,
    invoice_date   DATE                                NOT NULL,
    total          DECIMAL(14, 2)                      NOT NULL,
    discount       DECIMAL(14, 2)                      NOT NULL,
    paid_up        DECIMAL(14, 2)                      NOT NULL COMMENT 'paid to the treasury مدفوع نقدا الى الخزينة',
    stock_id       INT       DEFAULT 1                 NOT NULL,
    delegate_id    INT                                 NOT NULL,
    treasury_id    INT       DEFAULT 1                 NOT NULL,
    notes          LONGTEXT                            NULL,
    table_id       INT       DEFAULT 3                 NOT NULL,
    date_insert    DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id        INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT total_sales_custom_sup_id_fk FOREIGN KEY (sup_code) REFERENCES custom (id),
    CONSTRAINT total_sales_employees_id_fk FOREIGN KEY (delegate_id) REFERENCES employees (id),
    CONSTRAINT total_sales_stocks_stock_id_fk FOREIGN KEY (stock_id) REFERENCES stocks (stock_id),
    CONSTRAINT total_sales_treasury_id_fk FOREIGN KEY (treasury_id) REFERENCES treasury (id),
    CONSTRAINT total_sales_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT total_sales_invoice_type_chk CHECK (invoice_type IN (1, 2))
);

CALL add_index_if_missing('total_sales', 'total_sales_sup_code_fk', 'sup_code');
CALL add_index_if_missing('total_sales', 'total_sales_users_id_fk2', 'delegate_id');
CALL add_index_if_missing('total_sales', 'total_sales_date_idx', 'invoice_date');
CALL add_index_if_missing('total_sales', 'total_sales_treasury_idx', 'treasury_id, invoice_date');

CREATE TABLE IF NOT EXISTS total_sales_re
(
    id                 BIGINT                              NOT NULL PRIMARY KEY,
    sup_id             INT                                 NOT NULL,
    invoice_date       DATE                                NOT NULL,
    invoice_type       TINYINT   DEFAULT 1                 NOT NULL,
    total              DECIMAL(14, 2)                      NOT NULL,
    discount           DECIMAL(14, 2)                      NOT NULL,
    paid_from_treasury DECIMAL(14, 2)                      NOT NULL COMMENT 'paid from the treasury مدفوع نقدا من الخزينة',
    stock_id           INT       DEFAULT 1                 NOT NULL,
    delegate_id        INT                                 NOT NULL,
    treasury_id        INT       DEFAULT 1                 NOT NULL,
    notes              LONGTEXT                            NULL,
    table_id           INT       DEFAULT 4                 NOT NULL,
    date_insert        DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id            INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT total_sales_re_custom_id_fk FOREIGN KEY (sup_id) REFERENCES custom (id),
    CONSTRAINT total_sales_re_employees_id_fk FOREIGN KEY (delegate_id) REFERENCES employees (id),
    CONSTRAINT total_sales_re_stocks_stock_id_fk FOREIGN KEY (stock_id) REFERENCES stocks (stock_id),
    CONSTRAINT total_sales_re_treasury_id_fk FOREIGN KEY (treasury_id) REFERENCES treasury (id),
    CONSTRAINT total_sales_re_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT total_sales_re_invoice_type_chk CHECK (invoice_type IN (1, 2))
);

CALL add_index_if_missing('total_sales_re', 'total_sales_re_date_idx', 'invoice_date');
CALL add_index_if_missing('total_sales_re', 'total_sales_re_treasury_idx', 'treasury_id, invoice_date');
CALL add_index_if_missing('total_sales_re', 'total_sales_re_sup_idx', 'sup_id');
CALL add_index_if_missing('total_sales_re', 'total_sales_re_delegate_idx', 'delegate_id');

-- =====================================================================
-- 8) Accounts
-- =====================================================================

CREATE TABLE IF NOT EXISTS suppliers_accounts
(
    account_num           BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_code          INT                                      NOT NULL,
    account_date          DATE                                     NOT NULL,
    purchase              DECIMAL(14, 2) DEFAULT 0                 NOT NULL,
    paid                  DECIMAL(14, 2)                           NOT NULL,
    numberInv             BIGINT                                   NOT NULL,
    notes                 LONGTEXT                                 NULL,
    treasury_id           INT            DEFAULT 1                 NOT NULL,
    table_id              INT            DEFAULT 2                 NOT NULL,
    date_insert           DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id               INT            DEFAULT 1                 NOT NULL,
    invoice_number_return BIGINT         DEFAULT 0                 NOT NULL COMMENT 'This column for number invoice for returns',
    CONSTRAINT suppliers_accounts_suppliers_id_fk FOREIGN KEY (account_code) REFERENCES suppliers (id),
    CONSTRAINT suppliers_accounts_treasury_id_fk FOREIGN KEY (treasury_id) REFERENCES treasury (id),
    CONSTRAINT suppliers_accounts_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

CALL add_index_if_missing('suppliers_accounts', 'suppliers_accounts_numberInv_idx', 'numberInv');
CALL add_index_if_missing('suppliers_accounts', 'suppliers_accounts_date_idx', 'account_date');

CREATE TABLE IF NOT EXISTS customers_accounts
(
    account_num           BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_code          INT                                      NOT NULL,
    account_date          DATE                                     NOT NULL,
    paid                  DECIMAL(14, 2)                           NOT NULL,
    notes                 LONGTEXT                                 NULL,
    treasury_id           INT            DEFAULT 1                 NOT NULL,
    purchase              DECIMAL(14, 2) DEFAULT 0                 NOT NULL,
    numberInv             BIGINT                                   NOT NULL,
    table_id              INT            DEFAULT 2                 NOT NULL,
    created_at            TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id               INT            DEFAULT 1                 NOT NULL,
    invoice_number_return BIGINT         DEFAULT 0                 NOT NULL COMMENT 'This column for number invoice for returns',
    CONSTRAINT customers_accounts_custom_id_fk FOREIGN KEY (account_code) REFERENCES custom (id),
    CONSTRAINT customers_accounts_treasury_id_fk FOREIGN KEY (treasury_id) REFERENCES treasury (id),
    CONSTRAINT customers_accounts_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

CALL add_index_if_missing('customers_accounts', 'customers_accounts_numberInv_idx', 'numberInv');
CALL add_index_if_missing('customers_accounts', 'customers_accounts_date_idx', 'account_date');

-- =====================================================================
-- 9) Invoice lines
-- =====================================================================

CREATE TABLE IF NOT EXISTS purchase
(
    id              INT AUTO_INCREMENT PRIMARY KEY,
    invoice_number  BIGINT                   NOT NULL,
    num             INT                      NOT NULL,
    type            INT            DEFAULT 1 NOT NULL,
    quantity        DECIMAL(14, 3)           NOT NULL,
    price           DECIMAL(14, 2)           NOT NULL,
    discount        DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    type_value      DECIMAL(14, 3) DEFAULT 1 NOT NULL,
    expiration_date DATE                     NULL,
    CONSTRAINT purchase_items_id_fk FOREIGN KEY (num) REFERENCES items (id),
    CONSTRAINT purchase_total_buy_invoice_number_fk
        FOREIGN KEY (invoice_number) REFERENCES total_buy (invoice_number)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT purchase_units_unit_id_fk FOREIGN KEY (type) REFERENCES units (unit_id),
    CONSTRAINT purchase_quantity_chk CHECK (quantity > 0)
);

CALL add_index_if_missing('purchase', 'purchase_item_idx', 'num');

CREATE TABLE IF NOT EXISTS purchase_re
(
    id              INT AUTO_INCREMENT PRIMARY KEY,
    invoice_number  BIGINT                   NOT NULL,
    item_id         INT                      NOT NULL,
    type            INT            DEFAULT 1 NOT NULL,
    quantity        DECIMAL(14, 3)           NOT NULL,
    price           DECIMAL(14, 2)           NOT NULL,
    discount        DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    type_value      DECIMAL(14, 3) DEFAULT 1 NOT NULL,
    expiration_date DATE                     NULL,
    CONSTRAINT purchase_re_items_id_fk FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT purchase_re_total_buy_re_id_fk
        FOREIGN KEY (invoice_number) REFERENCES total_buy_re (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT purchase_re_units_unit_id_fk FOREIGN KEY (type) REFERENCES units (unit_id),
    CONSTRAINT purchase_re_quantity_chk CHECK (quantity > 0)
);

CALL add_index_if_missing('purchase_re', 'purchase_re_item_idx', 'item_id');

CREATE TABLE IF NOT EXISTS sales
(
    id               INT AUTO_INCREMENT PRIMARY KEY,
    invoice_number   BIGINT                   NOT NULL,
    num              INT                      NOT NULL,
    type             INT            DEFAULT 1 NOT NULL,
    quantity         DECIMAL(14, 3)           NOT NULL,
    price            DECIMAL(14, 2)           NOT NULL,
    buy_price        DECIMAL(14, 2)           NOT NULL,
    total_sel_price  DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_buy_price  DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_profit     DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    discount         DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    type_value       DECIMAL(14, 3) DEFAULT 1 NOT NULL,
    expiration_date  DATE                     NULL,
    item_has_package TINYINT(1)     DEFAULT 0 NOT NULL,
    CONSTRAINT sales_items_id_fk FOREIGN KEY (num) REFERENCES items (id),
    CONSTRAINT sales_total_invoice_number_fk
        FOREIGN KEY (invoice_number) REFERENCES total_sales (invoice_number)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT sales_units_unit_id_fk FOREIGN KEY (type) REFERENCES units (unit_id),
    CONSTRAINT sales_quantity_chk CHECK (quantity > 0)
);

CALL add_index_if_missing('sales', 'sales_item_idx', 'num');

CREATE TABLE IF NOT EXISTS sales_re
(
    id              INT AUTO_INCREMENT PRIMARY KEY,
    invoice_number  BIGINT                   NOT NULL,
    item_id         INT                      NOT NULL,
    type            INT            DEFAULT 1 NOT NULL,
    quantity        DECIMAL(14, 3)           NOT NULL,
    price           DECIMAL(14, 2)           NOT NULL,
    buy_price       DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_sel_price DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_buy_price DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_profit    DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    discount        DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    type_value      DECIMAL(14, 3) DEFAULT 1 NOT NULL,
    expiration_date DATE                     NULL,
    CONSTRAINT sales_re_items_id_fk FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT sales_re_total_sales_re_id_fk
        FOREIGN KEY (invoice_number) REFERENCES total_sales_re (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT sales_re_units_unit_id_fk FOREIGN KEY (type) REFERENCES units (unit_id),
    CONSTRAINT sales_re_quantity_chk CHECK (quantity > 0)
);

CALL add_index_if_missing('sales_re', 'sales_re_item_idx', 'item_id');

-- =====================================================================
-- 10) Targets
-- =====================================================================

CREATE TABLE IF NOT EXISTS targeted_sales
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    delegate_id   INT                                     NOT NULL,
    target        DECIMAL(14, 2)                          NOT NULL,
    target_ratio1 DECIMAL(6, 2) DEFAULT 100               NOT NULL,
    rate_1        DECIMAL(6, 2) DEFAULT 0                 NOT NULL,
    target_ratio2 DECIMAL(6, 2) DEFAULT 0                 NOT NULL,
    rate_2        DECIMAL(6, 2) DEFAULT 0                 NOT NULL,
    target_ratio3 DECIMAL(6, 2) DEFAULT 0                 NOT NULL,
    rate_3        DECIMAL(6, 2) DEFAULT 0                 NOT NULL,
    notes         VARCHAR(200)                            NULL,
    date_insert   DATETIME      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id       INT           DEFAULT 1                 NOT NULL,
    CONSTRAINT targeted_sales_employees_id_fk
        FOREIGN KEY (delegate_id) REFERENCES employees (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT targeted_sales_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

-- =====================================================================
-- 11) Users permissions / shifts
-- =====================================================================

CREATE TABLE IF NOT EXISTS user_permission
(
    id            INT AUTO_INCREMENT PRIMARY KEY,
    permission_id INT                                 NOT NULL,
    user_id       INT                                 NOT NULL,
    check_status  TINYINT   DEFAULT 0                 NOT NULL,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT user_permission_permission_id_fk
        FOREIGN KEY (permission_id) REFERENCES permission (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT user_permission_users_id_fk
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT user_permission_uk UNIQUE (permission_id, user_id),
    CONSTRAINT user_permission_chk CHECK (check_status IN (0, 1))
);

CREATE TABLE IF NOT EXISTS user_shifts
(
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    user_id             INT                      NOT NULL,
    open_time           DATETIME                 NOT NULL,
    close_time          DATETIME                 NULL,
    open_balance        DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    close_balance       DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_sales         DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_sales_returns DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_expenses      DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_deposits      DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    total_withdrawals   DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    expected_balance    DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    difference          DECIMAL(14, 2) DEFAULT 0 NOT NULL,
    invoices_count      INT            DEFAULT 0 NOT NULL,
    is_open             BOOLEAN        DEFAULT TRUE,
    notes               TEXT                     NULL,
    CONSTRAINT user_shifts_users_id_fk
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CALL add_index_if_missing('user_shifts', 'idx_user_shifts_user_open', 'user_id, is_open');
CALL add_index_if_missing('user_shifts', 'idx_user_shifts_open_time', 'open_time');

-- =====================================================================
-- 12) Audit log
-- =====================================================================

CREATE TABLE IF NOT EXISTS audit_log
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name  VARCHAR(100)                          NOT NULL,
    record_id   VARCHAR(100)                          NULL,
    action_type VARCHAR(20)                           NOT NULL,
    user_id     INT                                   NULL,
    action_time DATETIME    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    old_data    JSON                                  NULL,
    new_data    JSON                                  NULL,
    source      VARCHAR(50) DEFAULT 'APP'             NOT NULL,
    notes       TEXT                                  NULL,

    CONSTRAINT audit_log_action_chk
        CHECK (action_type IN ('INSERT', 'UPDATE', 'DELETE')),

    CONSTRAINT audit_log_users_id_fk
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE SET NULL
);

CALL add_index_if_missing('audit_log', 'idx_audit_table_record', 'table_name, record_id');
CALL add_index_if_missing('audit_log', 'idx_audit_user_time', 'user_id, action_time');
CALL add_index_if_missing('audit_log', 'idx_audit_action_time', 'action_type, action_time');

#=================
CREATE TABLE IF NOT EXISTS treasury_movements
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    treasury_id    INT                                      NOT NULL,
    movement_date  DATE                                     NOT NULL,

    movement_type  VARCHAR(50)                              NOT NULL,

    amount_in      DECIMAL(14, 2) DEFAULT 0                 NOT NULL,
    amount_out     DECIMAL(14, 2) DEFAULT 0                 NOT NULL,
    balance_after  DECIMAL(14, 2) DEFAULT 0                 NOT NULL,

    reference_type VARCHAR(50)                              NULL,
    reference_id   BIGINT                                   NULL,

    notes          TEXT                                     NULL,
    date_insert    DATETIME       DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id        INT            DEFAULT 1                 NOT NULL,

    CONSTRAINT treasury_movements_treasury_id_fk
        FOREIGN KEY (treasury_id) REFERENCES treasury (id),

    CONSTRAINT treasury_movements_users_id_fk
        FOREIGN KEY (user_id) REFERENCES users (id),

    CONSTRAINT treasury_movements_amount_chk
        CHECK (
            (amount_in > 0 AND amount_out = 0)
                OR
            (amount_in = 0 AND amount_out > 0)
            ),

    CONSTRAINT treasury_movements_type_chk
        CHECK (movement_type IN (
                                 'OPENING',
                                 'DEPOSIT',
                                 'WITHDRAWAL',
                                 'TRANSFER_IN',
                                 'TRANSFER_OUT',
                                 'SALE',
                                 'SALE_RETURN',
                                 'PURCHASE',
                                 'PURCHASE_RETURN',
                                 'EXPENSE',
                                 'ADJUSTMENT_IN',
                                 'ADJUSTMENT_OUT'
            )),

    CONSTRAINT treasury_movements_reference_type_chk
        CHECK (
            reference_type IS NULL
                OR reference_type IN (
                                      'TREASURY',
                                      'TREASURY_DEPOSIT_EXPENSES',
                                      'TREASURY_TRANSFER',
                                      'SALE',
                                      'SALE_RETURN',
                                      'PURCHASE',
                                      'PURCHASE_RETURN',
                                      'EXPENSE',
                                      'ADJUSTMENT'
                )
            )
);

CALL add_index_if_missing('treasury_movements', 'treasury_movements_treasury_date_idx', 'treasury_id, movement_date, id');

CALL add_index_if_missing('treasury_movements', 'treasury_movements_reference_idx', 'reference_type, reference_id');

CALL add_index_if_missing('treasury_movements', 'treasury_movements_date_idx', 'movement_date');

DROP PROCEDURE IF EXISTS add_index_if_missing;




CREATE TABLE IF NOT EXISTS system_info (
                                           id INT PRIMARY KEY,
                                           client_code VARCHAR(50),
                                           client_name VARCHAR(255),
                                           app_version VARCHAR(50),
                                           database_version VARCHAR(50),
                                           install_date DATETIME,
                                           last_update DATETIME,
                                           database_name VARCHAR(100),
                                           server_ip VARCHAR(100),
                                           license_key VARCHAR(255),
                                           notes TEXT
);

CREATE TABLE IF NOT EXISTS database_migrations (
                                     id INT AUTO_INCREMENT PRIMARY KEY,
                                     version VARCHAR(50) NOT NULL,
                                     description VARCHAR(255),
                                     executed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                                     CONSTRAINT database_migrations_version_uk UNIQUE (version)
);



-- V4 - Convergence of legacy schemas.
--
-- V1 only ever runs on a brand-new database. Every existing client is stamped
-- with it by baselineOnMigrate without it being executed, so anything V1 says
-- about repairing an old schema never reaches the databases that need it - the
-- stamp is taken on trust, and the marker-table guard in DatabaseMigrationService
-- checks seven tables and no views, which is how a database missing
-- view_customer_receivables was recorded as being the v4.1.3 schema.
--
-- This file closes that gap by being a versioned migration instead. A client
-- sitting at V3 runs it and converges; a fresh install runs V1 through V3 first
-- and then reaches this as a no-op. No Flyway configuration change and no
-- hand-editing of flyway_schema_history at each client.
--
-- CREATE TABLE IF NOT EXISTS is silent about a table that already exists with
-- the wrong shape - an `items` from v3.x keeps its old columns and gains none
-- of the new ones. What follows walks every table whose definition changed
-- since the old releases and brings the columns, types and constraints up to
-- the current shape, checking information_schema before touching anything.
--
-- It runs unconditionally rather than being gated on a detected version: the
-- old schemas carry no version marker to detect, which is precisely what made
-- the manual upgrade path skippable in the first place. On a database that is
-- already current, every statement here is a no-op.
--
-- Tested against a v3.x database built from the original script.sql: repeated
-- runs leave the schema and the data unchanged, and the result differs from a
-- fresh install only by the legacy-only columns it deliberately does not drop
-- (company.trial_*, company.installation_date, custom.status, suppliers.status).
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- Foreign keys over columns that are about to change type.
--
-- v3.0.0 declared total_buy.invoice_number and total_sales.invoice_number as
-- INT; the current schema has them BIGINT. Widening the parent while the child
-- purchase.invoice_number is still INT breaks the foreign key between them, and
-- MySQL refuses outright:
--
--   ERROR 3780: Referencing column 'invoice_number' and referenced column
--   'invoice_number' in foreign key constraint
--   'purchase_total_buy_invoice_number_fk' are incompatible
--
-- SET FOREIGN_KEY_CHECKS = 0 does not help - it suppresses row validation, not
-- the type check on the constraint definition. The keys have to come off, both
-- sides retyped, and the keys put back. This is what V000_forrienKey.sql did
-- before V001_update_database.sql in the old manual bundle, and it has to run
-- here for the same reason and in the same order.
--
-- Idempotent on a current database: the drops find the keys, the MODIFYs are
-- no-ops because the columns are already BIGINT, and the same keys go back on.
-- total_buy_re_total_buy_invoice_number_fk is dropped without being restored,
-- matching V1 - that key exists only in the legacy schema.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS DropForeignKeyIfExists;
DELIMITER $$
CREATE PROCEDURE DropForeignKeyIfExists(IN target_table VARCHAR(255), IN fk_name VARCHAR(255))
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE()
                 AND TABLE_NAME       = target_table
                 AND CONSTRAINT_NAME  = fk_name
                 AND CONSTRAINT_TYPE  = 'FOREIGN KEY')
    THEN
        SET @query = CONCAT('ALTER TABLE `', target_table, '` DROP FOREIGN KEY `', fk_name, '`');
        PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL DropForeignKeyIfExists('purchase',           'purchase_total_buy_invoice_number_fk');
CALL DropForeignKeyIfExists('sales',              'sales_total_invoice_number_fk');
CALL DropForeignKeyIfExists('purchase_re',        'purchase_re_total_buy_re_id_fk');
CALL DropForeignKeyIfExists('sales_re',           'sales_re_total_sales_re_id_fk');
CALL DropForeignKeyIfExists('total_buy_re',       'total_buy_re_total_buy_invoice_number_fk');
CALL DropForeignKeyIfExists('total_buy_re',       'total_buy_re_suppliers_sup_id_fk');
CALL DropForeignKeyIfExists('total_sales_re',     'total_sales_re_custom_id_fk');
CALL DropForeignKeyIfExists('suppliers_accounts', 'suppliers_accounts_suppliers_id_fk');
CALL DropForeignKeyIfExists('customers_accounts', 'customers_accounts_custom_id_fk');

-- Parents first, then children, so the pair is never half-converted.
ALTER TABLE total_buy      MODIFY invoice_number BIGINT NOT NULL;
ALTER TABLE total_sales    MODIFY invoice_number BIGINT NOT NULL;
ALTER TABLE total_buy_re   MODIFY id BIGINT NOT NULL;
ALTER TABLE total_sales_re MODIFY id BIGINT NOT NULL;
ALTER TABLE suppliers      MODIFY id INT NOT NULL;
ALTER TABLE custom         MODIFY id INT NOT NULL;

ALTER TABLE purchase    MODIFY invoice_number BIGINT NOT NULL;
ALTER TABLE sales       MODIFY invoice_number BIGINT NOT NULL;
ALTER TABLE purchase_re MODIFY invoice_number BIGINT NOT NULL;
ALTER TABLE sales_re    MODIFY invoice_number BIGINT NOT NULL;

ALTER TABLE purchase ADD CONSTRAINT purchase_total_buy_invoice_number_fk
    FOREIGN KEY (invoice_number) REFERENCES total_buy (invoice_number) ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE sales ADD CONSTRAINT sales_total_invoice_number_fk
    FOREIGN KEY (invoice_number) REFERENCES total_sales (invoice_number) ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE purchase_re ADD CONSTRAINT purchase_re_total_buy_re_id_fk
    FOREIGN KEY (invoice_number) REFERENCES total_buy_re (id) ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE sales_re ADD CONSTRAINT sales_re_total_sales_re_id_fk
    FOREIGN KEY (invoice_number) REFERENCES total_sales_re (id) ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE total_buy_re ADD CONSTRAINT total_buy_re_suppliers_sup_id_fk
    FOREIGN KEY (sup_id) REFERENCES suppliers (id);
ALTER TABLE total_sales_re ADD CONSTRAINT total_sales_re_custom_id_fk
    FOREIGN KEY (sup_id) REFERENCES custom (id);
ALTER TABLE suppliers_accounts ADD CONSTRAINT suppliers_accounts_suppliers_id_fk
    FOREIGN KEY (account_code) REFERENCES suppliers (id);
ALTER TABLE customers_accounts ADD CONSTRAINT customers_accounts_custom_id_fk
    FOREIGN KEY (account_code) REFERENCES custom (id);

DROP PROCEDURE IF EXISTS DropForeignKeyIfExists;

DELIMITER $$

-- أ. إجراء لإضافة أو تعديل الأعمدة
DROP PROCEDURE IF EXISTS ManageColumn$$
CREATE PROCEDURE ManageColumn(IN t_name VARCHAR(255), IN c_name VARCHAR(255), IN col_def TEXT)
BEGIN
    DECLARE col_exists INT;
    SELECT COUNT(*) INTO col_exists FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t_name AND COLUMN_NAME = c_name;

    IF col_exists > 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' MODIFY COLUMN ', c_name, ' ', col_def);
    ELSE
        SET @query = CONCAT('ALTER TABLE ', t_name, ' ADD COLUMN ', c_name, ' ', col_def);
    END IF;
    PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;
END$$

-- ب. إجراء لتغيير اسم العمود بأمان (بدون خطأ إذا تم تغييره مسبقاً)
DROP PROCEDURE IF EXISTS RenameColumnSafe$$
CREATE PROCEDURE RenameColumnSafe(IN t_name VARCHAR(255), IN old_c VARCHAR(255), IN new_c VARCHAR(255), IN col_def TEXT)
BEGIN
    DECLARE old_exists INT; DECLARE new_exists INT;
    SELECT COUNT(*) INTO old_exists FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t_name AND COLUMN_NAME = old_c;
    SELECT COUNT(*) INTO new_exists FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t_name AND COLUMN_NAME = new_c;

    IF new_exists = 0 AND old_exists > 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' CHANGE COLUMN ', old_c, ' ', new_c, ' ', col_def);
        PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    ELSEIF new_exists > 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' MODIFY COLUMN ', new_c, ' ', col_def);
        PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$

-- ج. إجراء لإضافة وتحديث القيود (Constraints & Indexes)
DROP PROCEDURE IF EXISTS ManageConstraint$$
CREATE PROCEDURE ManageConstraint(IN t_name VARCHAR(255), IN c_name VARCHAR(255), IN const_def TEXT, IN c_type VARCHAR(50))
BEGIN
    DECLARE const_exists INT;

    IF c_type = 'UNIQUE' THEN
        SELECT COUNT(*) INTO const_exists FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t_name AND INDEX_NAME = c_name;
    ELSE
        SELECT COUNT(*) INTO const_exists FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = t_name AND CONSTRAINT_NAME = c_name;
    END IF;

    -- The original one-shot script dropped the constraint and re-added it. That
    -- cannot work here: items_stock_uk backs a foreign key, so dropping it fails
    -- with errno 1553, and on a live database the window between the drop and the
    -- re-add is a window with no constraint at all. Add-if-missing converges to
    -- the same end state and is safe to repeat.
    IF const_exists = 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' ADD CONSTRAINT ', c_name, ' ', const_def);
        PREPARE stmt FROM @query; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$

-- د. إجراء ترحيل البيانات وحذف الجداول القديمة بأمان
DROP PROCEDURE IF EXISTS MigrateDataSafe$$
CREATE PROCEDURE MigrateDataSafe()
BEGIN
    DECLARE tbl_exists INT;
    -- ترحيل أسعار العناصر
    SELECT COUNT(*) INTO tbl_exists FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='items_price';
    IF tbl_exists > 0 THEN
        SET @q1 = 'UPDATE items i JOIN items_price ip1 ON i.id = ip1.item_id AND ip1.price_id = 1 SET i.sel_price1 = ip1.sel_price where i.id != 0';
        PREPARE stmt1 FROM @q1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;
        SET @q2 = 'DROP TABLE items_price';
        PREPARE stmt2 FROM @q2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;
    END IF;

    -- نسخ احتياطي لـ processes_data
    SELECT COUNT(*) INTO tbl_exists FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='processes_data';
    IF tbl_exists > 0 THEN
        SET @q1 = 'CREATE TABLE IF NOT EXISTS processes_data_backup AS SELECT * FROM processes_data';
        PREPARE stmt1 FROM @q1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;
        SET @q2 = 'DROP TABLE processes_data';
        PREPARE stmt2 FROM @q2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;
    END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------
-- Columns and constraints, table by table.
-- ---------------------------------------------------------------------

CALL ManageColumn('company', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

-- v3.x declared user_name as VARCHAR(100) NOT NULL where the current schema has
-- it nullable. Converging on the wider type rather than the narrower one: the
-- width is harmless either way, but MODIFY-ing 100 down to 30 would truncate any
-- name a client had already stored.
CALL ManageColumn('users', 'user_name', 'VARCHAR(100) NULL');
CALL ManageColumn('users', 'user_pass', 'VARCHAR(255) NULL');
CALL ManageColumn('users', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('users', 'users_activity_chk', 'CHECK (user_activity IN (0, 1))', 'CHECK');
CALL ManageConstraint('users', 'users_available_chk', 'CHECK (user_available IN (0, 1))', 'CHECK');

CALL ManageColumn('main_group', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageColumn('stocks', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('treasury', 'amount', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('treasury', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('type_price', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('units', 'value_d', 'DECIMAL(14, 3) NOT NULL DEFAULT 1');
CALL ManageColumn('units', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('employees', 'salary', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('employees', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('sub_group', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('suppliers', 'id', 'int AUTO_INCREMENT');
CALL ManageColumn('suppliers', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('suppliers', 'first_balance', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('suppliers', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('custom', 'id', 'int AUTO_INCREMENT');
-- Legacy custom.price_id carried no default, so an insert that omits it fails.
CALL ManageColumn('custom', 'price_id', 'INT NOT NULL DEFAULT 1');
CALL ManageColumn('custom', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('custom', 'limit_num', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('custom', 'first_balance', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL RenameColumnSafe('custom', 'date_insert', 'created_at', 'TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP');
CALL ManageColumn('custom', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('items', 'buy_price', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'sel_price1', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'sel_price2', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'sel_price3', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'mini_quantity', 'DECIMAL(14, 3) NOT NULL DEFAULT 1');
CALL ManageColumn('items', 'first_balance', 'DECIMAL(14, 3) NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'item_active', 'TINYINT(1) NOT NULL DEFAULT 1');
CALL ManageColumn('items', 'item_has_validity', 'TINYINT(1) NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'number_validity_days', 'INT NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'alert_days_before_expire', 'INT NOT NULL DEFAULT 0');
CALL ManageColumn('items', 'item_has_package', 'TINYINT(1) NOT NULL DEFAULT 0');
CALL RenameColumnSafe('items', 'date_insert', 'created_at', 'TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP');
CALL ManageColumn('items', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('items_stock', 'first_balance', 'DECIMAL(14, 3) NOT NULL DEFAULT 0');
CALL ManageColumn('items_stock', 'current_quantity', 'DECIMAL(14, 3) NOT NULL DEFAULT 0');
CALL ManageConstraint('items_stock', 'items_stock_uk', 'UNIQUE (item_id, stock_id)', 'UNIQUE');

CALL ManageColumn('items_units', 'quantity', 'DECIMAL(14, 3) NOT NULL');
CALL ManageColumn('items_units', 'buy_price', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('items_units', 'sel_price', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('items_units', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('items_units', 'items_units_quantity_chk', 'CHECK (quantity > 0)', 'CHECK');

CALL ManageColumn('treasury_deposit_expenses', 'amount', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('treasury_deposit_expenses', 'deposit_or_expenses', 'TINYINT NOT NULL DEFAULT 1');
CALL ManageColumn('treasury_deposit_expenses', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('treasury_deposit_expenses', 'treasury_deposit_expenses_type_chk', 'CHECK (deposit_or_expenses IN (1, 2))', 'CHECK');

CALL ManageColumn('treasury_transfers', 'amount', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('treasury_transfers', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('treasury_transfers', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('treasury_transfers', 'treasury_transfers_not_same_chk', 'CHECK (treasury_from <> treasury_to)', 'CHECK');
CALL ManageConstraint('treasury_transfers', 'treasury_transfers_amount_chk', 'CHECK (amount > 0)', 'CHECK');

CALL ManageColumn('expenses_details', 'amount', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('expenses_details', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('expenses_details', 'expenses_details_amount_chk', 'CHECK (amount >= 0)', 'CHECK');

CALL ManageColumn('total_buy', 'invoice_number', 'BIGINT NOT NULL');
CALL ManageColumn('total_buy', 'discount', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_buy', 'paid_up', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_buy', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('total_buy', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('total_buy', 'total_buy_invoice_type_chk', 'CHECK (invoice_type IN (1, 2))', 'CHECK');

CALL ManageColumn('total_buy_re', 'id', 'BIGINT NOT NULL');
CALL ManageColumn('total_buy_re', 'discount', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_buy_re', 'paid_to_treasury', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_buy_re', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('total_buy_re', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('total_buy_re', 'total_buy_re_invoice_type_chk', 'CHECK (invoice_type IN (1, 2))', 'CHECK');

CALL ManageColumn('total_sales', 'invoice_number', 'BIGINT NOT NULL');
CALL ManageColumn('total_sales', 'discount', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_sales', 'paid_up', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_sales', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('total_sales', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('total_sales', 'total_sales_invoice_type_chk', 'CHECK (invoice_type IN (1, 2))', 'CHECK');

CALL ManageColumn('total_sales_re', 'id', 'BIGINT NOT NULL');
CALL ManageColumn('total_sales_re', 'discount', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_sales_re', 'paid_from_treasury', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_sales_re', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('total_sales_re', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('total_sales_re', 'total_sales_re_invoice_type_chk', 'CHECK (invoice_type IN (1, 2))', 'CHECK');

CALL ManageColumn('suppliers_accounts', 'account_num', 'BIGINT AUTO_INCREMENT');
CALL ManageColumn('suppliers_accounts', 'purchase', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('suppliers_accounts', 'paid', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('suppliers_accounts', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('suppliers_accounts', 'invoice_number_return', 'BIGINT NOT NULL DEFAULT 0');
CALL ManageColumn('suppliers_accounts', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('customers_accounts', 'account_num', 'BIGINT AUTO_INCREMENT');
CALL ManageColumn('customers_accounts', 'paid', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('customers_accounts', 'notes', 'LONGTEXT NULL');
CALL ManageColumn('customers_accounts', 'purchase', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('customers_accounts', 'invoice_number_return', 'BIGINT NOT NULL DEFAULT 0');
CALL RenameColumnSafe('customers_accounts', 'date_insert', 'created_at', 'TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP');
CALL ManageColumn('customers_accounts', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('purchase', 'invoice_number', 'BIGINT NOT NULL');
CALL ManageColumn('purchase', 'quantity', 'DECIMAL(14, 3) NOT NULL');
CALL ManageColumn('purchase', 'price', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('purchase', 'discount', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('purchase', 'type_value', 'DECIMAL(14, 3) NOT NULL DEFAULT 1');
CALL ManageColumn('purchase', 'expiration_date', 'DATE NULL');
CALL ManageConstraint('purchase', 'purchase_quantity_chk', 'CHECK (quantity > 0)', 'CHECK');

CALL ManageColumn('purchase_re', 'invoice_number', 'BIGINT NOT NULL');
CALL ManageColumn('purchase_re', 'quantity', 'DECIMAL(14, 3) NOT NULL');
CALL ManageColumn('purchase_re', 'price', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('purchase_re', 'discount', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('purchase_re', 'type_value', 'DECIMAL(14, 3) NOT NULL DEFAULT 1');
CALL ManageColumn('purchase_re', 'expiration_date', 'DATE NULL');
CALL ManageConstraint('purchase_re', 'purchase_re_quantity_chk', 'CHECK (quantity > 0)', 'CHECK');

CALL ManageColumn('sales', 'invoice_number', 'BIGINT NOT NULL');
CALL ManageColumn('sales', 'quantity', 'DECIMAL(14, 3) NOT NULL');
CALL ManageColumn('sales', 'price', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('sales', 'buy_price', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('sales', 'total_sel_price', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales', 'total_buy_price', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales', 'total_profit', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales', 'discount', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales', 'type_value', 'DECIMAL(14, 3) NOT NULL DEFAULT 1');
CALL ManageColumn('sales', 'expiration_date', 'DATE NULL');
CALL ManageColumn('sales', 'item_has_package', 'TINYINT(1) NOT NULL DEFAULT 0');
CALL ManageConstraint('sales', 'sales_quantity_chk', 'CHECK (quantity > 0)', 'CHECK');

CALL ManageColumn('sales_re', 'invoice_number', 'BIGINT NOT NULL');
CALL ManageColumn('sales_re', 'quantity', 'DECIMAL(14, 3) NOT NULL');
CALL ManageColumn('sales_re', 'price', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('sales_re', 'buy_price', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales_re', 'total_sel_price', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales_re', 'total_buy_price', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales_re', 'total_profit', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales_re', 'discount', 'DECIMAL(14, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('sales_re', 'type_value', 'DECIMAL(14, 3) NOT NULL DEFAULT 1');
CALL ManageColumn('sales_re', 'expiration_date', 'DATE NULL');
CALL ManageConstraint('sales_re', 'sales_re_quantity_chk', 'CHECK (quantity > 0)', 'CHECK');

CALL ManageColumn('targeted_sales', 'target', 'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('targeted_sales', 'target_ratio1', 'DECIMAL(6, 2) NOT NULL DEFAULT 100');
CALL ManageColumn('targeted_sales', 'rate_1', 'DECIMAL(6, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('targeted_sales', 'target_ratio2', 'DECIMAL(6, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('targeted_sales', 'rate_2', 'DECIMAL(6, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('targeted_sales', 'target_ratio3', 'DECIMAL(6, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('targeted_sales', 'rate_3', 'DECIMAL(6, 2) NOT NULL DEFAULT 0');
CALL ManageColumn('targeted_sales', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');

CALL ManageColumn('user_permission', 'check_status', 'TINYINT NOT NULL DEFAULT 0');
CALL ManageColumn('user_permission', 'updated_at', 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP');
CALL ManageConstraint('user_permission', 'user_permission_uk', 'UNIQUE (permission_id, user_id)', 'UNIQUE');
CALL ManageConstraint('user_permission', 'user_permission_chk', 'CHECK (check_status IN (0, 1))', 'CHECK');

-- ---------------------------------------------------------------------
-- Constraints on tables that predate the convergence helpers.
-- ---------------------------------------------------------------------
-- قيود Stock Transfer الجديدة
CALL ManageConstraint('stock_transfer', 'stock_transfer_from_fk', 'FOREIGN KEY (stock_from) REFERENCES stocks (stock_id)', 'FOREIGN KEY');
CALL ManageConstraint('stock_transfer', 'stock_transfer_to_fk', 'FOREIGN KEY (stock_to) REFERENCES stocks (stock_id)', 'FOREIGN KEY');
CALL ManageConstraint('stock_transfer', 'stock_transfer_not_same_chk', 'CHECK (stock_from <> stock_to)', 'CHECK');

CALL ManageConstraint('stock_transfer_list', 'stock_transfer_list_items_id_fk', 'FOREIGN KEY (item_id) REFERENCES items (id)', 'FOREIGN KEY');
CALL ManageConstraint('stock_transfer_list', 'stock_transfer_list_quantity_chk', 'CHECK (quantity > 0)', 'CHECK');

-- ---------------------------------------------------------------------
-- ---------------------------------------------------------------------
-- Column types that v3.0.0 got wrong and V001_update_database never covered.
--
-- The old manual script was written against a later "old" schema than v3.0.0,
-- so these thirteen columns fell through it. Found by diffing a migrated v3.0.0
-- database against a fresh install, column by column.
--
-- The money columns matter most: v3.0.0 stored invoice totals as DOUBLE, which
-- carries rounding error into every sum the reports run. DECIMAL(14,2) is what
-- the current schema uses and what the balances depend on.
--
-- The narrowings to TINYINT are safe: each of those columns is a flag already
-- constrained to (0,1) or (1,2) by a CHECK added further down.
-- ---------------------------------------------------------------------
CALL ManageColumn('customers_accounts',  'numberInv',      'BIGINT NOT NULL');
CALL ManageColumn('suppliers_accounts',  'numberInv',      'BIGINT NOT NULL');
CALL ManageColumn('stock_transfer_list', 'quantity',       'DECIMAL(14, 3) NOT NULL');

CALL ManageColumn('total_buy',       'total',        'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_buy',       'invoice_type', 'TINYINT NOT NULL DEFAULT 1');
CALL ManageColumn('total_buy_re',    'total',        'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_buy_re',    'invoice_type', 'TINYINT NOT NULL DEFAULT 1');
CALL ManageColumn('total_sales',     'total',        'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_sales',     'invoice_type', 'TINYINT NOT NULL DEFAULT 1');
CALL ManageColumn('total_sales_re',  'total',        'DECIMAL(14, 2) NOT NULL');
CALL ManageColumn('total_sales_re',  'invoice_type', 'TINYINT NOT NULL DEFAULT 1');

CALL ManageColumn('users', 'user_activity',  'TINYINT NOT NULL DEFAULT 1');
CALL ManageColumn('users', 'user_available', 'TINYINT NOT NULL DEFAULT 0');

-- Legacy data moves. Each one checks first, so they are inert on a
-- database that has already been through them.
-- ---------------------------------------------------------------------

-- Folds items_price into items.sel_price1 and drops processes_data,
-- both only if those legacy tables are still present.
CALL MigrateDataSafe();

-- تهيئة stock_movements للمرة الأولى فقط
INSERT INTO stock_movements (item_id, stock_id, movement_date, movement_type, quantity_in, user_id)
SELECT is2.item_id, is2.stock_id, NOW(), 'OPENING', is2.first_balance, 1
FROM items_stock is2
WHERE is2.first_balance > 0 AND NOT EXISTS (SELECT 1 FROM stock_movements);


-- Recompute on-hand quantities from the movement ledger. Idempotent by
-- construction - it derives the value rather than adjusting it.
UPDATE items_stock ist
SET current_quantity = (SELECT COALESCE(SUM(quantity_in) - SUM(quantity_out), 0)
                        FROM stock_movements sm
                        WHERE sm.item_id = ist.item_id
                          AND sm.stock_id = ist.stock_id);

-- database_migrations.version is UNIQUE, but CREATE TABLE IF NOT EXISTS above
-- cannot retrofit that onto a database where the table already exists - either
-- from an earlier build of this file, or from the hand-run v4.1.0 delta. The
-- check is on "any unique index over version" rather than on a constraint name,
-- because the delta's inline UNIQUE produced an index named `version` while the
-- named constraint produces database_migrations_version_uk; either one counts.
DROP PROCEDURE IF EXISTS fix_migrations_version_uk;
DELIMITER $$
CREATE PROCEDURE fix_migrations_version_uk()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE table_schema = DATABASE()
                     AND table_name   = 'database_migrations'
                     AND column_name  = 'version'
                     AND non_unique   = 0)
    THEN
        -- A duplicate version would make the index fail to build. Keep the
        -- earliest row of each version and drop the rest.
        DELETE d FROM database_migrations d
            JOIN database_migrations keep
              ON keep.version = d.version AND keep.id < d.id;

        ALTER TABLE database_migrations
            ADD CONSTRAINT database_migrations_version_uk UNIQUE (version);
    END IF;
END$$
DELIMITER ;

CALL fix_migrations_version_uk();
DROP PROCEDURE IF EXISTS fix_migrations_version_uk;

-- The original script did TRUNCATE TABLE type_price followed by a re-insert of
-- the three defaults. That is safe as a one-shot upgrade from a v3.x database,
-- but destructive here: a client who renamed their price tiers would silently
-- lose the names every time this file ran. Seeding only when the table is empty
-- gives an old database the rows it lacks and leaves a populated one alone.
INSERT INTO type_price (name)
SELECT * FROM (SELECT 'سعر1' UNION ALL SELECT 'سعر2' UNION ALL SELECT 'سعر3') AS d
WHERE NOT EXISTS (SELECT 1 FROM type_price);

DROP TABLE IF EXISTS `sales_package`;

-- ---------------------------------------------------------------------
-- Retire the v3.0.0 trigger set.
--
-- v3.0.0 logged every change into a `processes_data` table through ~60 AFTER
-- triggers calling handle_processes_data(). v4 replaced that mechanism with
-- audit_log, and MigrateDataSafe() above drops processes_data - which leaves
-- those triggers pointing at a table that no longer exists. The failure is not
-- subtle: the next INSERT into items dies with
--
--   ERROR 1146: Table 'processes_data' doesn't exist
--
-- and the application is unusable. The old manual bundle never dropped them
-- either, so this hits any v3.0.0 client that was upgraded by hand as well.
--
-- The remaining v3.0.0 triggers here are not broken - they set defaults and
-- guard deletes - but v4 does not define them, so leaving them would keep a
-- migrated database behaving differently from a fresh install forever.
--
-- Listed by name rather than discovered from information_schema because MySQL
-- rejects DROP TRIGGER through the prepared-statement protocol (ERROR 1295), so
-- a cursor over the catalogue cannot execute the drop it finds.
-- ---------------------------------------------------------------------
DROP TRIGGER IF EXISTS after_company_update;
DROP TRIGGER IF EXISTS after_custom_account_delete;
DROP TRIGGER IF EXISTS after_custom_account_insert;
DROP TRIGGER IF EXISTS after_custom_account_update;
DROP TRIGGER IF EXISTS after_custom_delete;
DROP TRIGGER IF EXISTS after_custom_insert;
DROP TRIGGER IF EXISTS after_custom_update;
DROP TRIGGER IF EXISTS after_employees_delete;
DROP TRIGGER IF EXISTS after_employees_insert;
DROP TRIGGER IF EXISTS after_employees_update;
DROP TRIGGER IF EXISTS after_expenses_details_delete;
DROP TRIGGER IF EXISTS after_expenses_details_insert;
DROP TRIGGER IF EXISTS after_expenses_details_update;
DROP TRIGGER IF EXISTS after_items_delete;
DROP TRIGGER IF EXISTS after_items_insert;
DROP TRIGGER IF EXISTS after_items_units_delete;
DROP TRIGGER IF EXISTS after_items_units_insert;
DROP TRIGGER IF EXISTS after_items_units_update;
DROP TRIGGER IF EXISTS after_main_group_delete;
DROP TRIGGER IF EXISTS after_main_group_insert;
DROP TRIGGER IF EXISTS after_main_group_update;
DROP TRIGGER IF EXISTS after_stock_transfer_delete;
DROP TRIGGER IF EXISTS after_stock_transfer_insert;
DROP TRIGGER IF EXISTS after_stock_transfer_update;
DROP TRIGGER IF EXISTS after_stocks_delete;
DROP TRIGGER IF EXISTS after_stocks_insert;
DROP TRIGGER IF EXISTS after_stocks_update;
DROP TRIGGER IF EXISTS after_sub_group_delete;
DROP TRIGGER IF EXISTS after_sub_group_insert;
DROP TRIGGER IF EXISTS after_sub_group_update;
DROP TRIGGER IF EXISTS after_supplier_account_delete;
DROP TRIGGER IF EXISTS after_supplier_account_insert;
DROP TRIGGER IF EXISTS after_supplier_account_update;
DROP TRIGGER IF EXISTS after_suppliers_delete;
DROP TRIGGER IF EXISTS after_suppliers_insert;
DROP TRIGGER IF EXISTS after_suppliers_update;
DROP TRIGGER IF EXISTS after_total_buy_delete;
DROP TRIGGER IF EXISTS after_total_buy_insert;
DROP TRIGGER IF EXISTS after_total_buy_re_delete;
DROP TRIGGER IF EXISTS after_total_buy_re_insert;
DROP TRIGGER IF EXISTS after_total_buy_re_update;
DROP TRIGGER IF EXISTS after_total_buy_update;
DROP TRIGGER IF EXISTS after_total_sales_delete;
DROP TRIGGER IF EXISTS after_total_sales_insert;
DROP TRIGGER IF EXISTS after_total_sales_re_delete;
DROP TRIGGER IF EXISTS after_total_sales_re_insert;
DROP TRIGGER IF EXISTS after_total_sales_re_update;
DROP TRIGGER IF EXISTS after_total_sales_update;
DROP TRIGGER IF EXISTS after_treasury_delete;
DROP TRIGGER IF EXISTS after_treasury_deposit_expenses_delete;
DROP TRIGGER IF EXISTS after_treasury_deposit_expenses_insert;
DROP TRIGGER IF EXISTS after_treasury_deposit_expenses_update;
DROP TRIGGER IF EXISTS after_treasury_insert;
DROP TRIGGER IF EXISTS after_treasury_transfers_delete;
DROP TRIGGER IF EXISTS after_treasury_transfers_insert;
DROP TRIGGER IF EXISTS after_treasury_transfers_update;
DROP TRIGGER IF EXISTS after_treasury_update;
DROP TRIGGER IF EXISTS after_units_delete;
DROP TRIGGER IF EXISTS after_units_insert;
DROP TRIGGER IF EXISTS after_units_update;
DROP TRIGGER IF EXISTS before_custom_account_insert;
DROP TRIGGER IF EXISTS before_custom_insert;
DROP TRIGGER IF EXISTS before_employees_insert;
DROP TRIGGER IF EXISTS before_expenses_details_insert;
DROP TRIGGER IF EXISTS before_items_insert;
DROP TRIGGER IF EXISTS before_main_group_delete;
DROP TRIGGER IF EXISTS before_main_group_insert;
DROP TRIGGER IF EXISTS before_purchase_insert;
DROP TRIGGER IF EXISTS before_purchase_re_insert;
DROP TRIGGER IF EXISTS before_sales_insert;
DROP TRIGGER IF EXISTS before_sales_re_insert;
DROP TRIGGER IF EXISTS before_stock_transfer_insert;
DROP TRIGGER IF EXISTS before_stocks_delete;
DROP TRIGGER IF EXISTS before_stocks_insert;
DROP TRIGGER IF EXISTS before_sub_group_delete;
DROP TRIGGER IF EXISTS before_sub_group_insert;
DROP TRIGGER IF EXISTS before_suppliers_accounts_insert;
DROP TRIGGER IF EXISTS before_suppliers_insert;
DROP TRIGGER IF EXISTS before_total_buy_insert;
DROP TRIGGER IF EXISTS before_total_buy_re_insert;
DROP TRIGGER IF EXISTS before_total_sales_insert;
DROP TRIGGER IF EXISTS before_total_sales_re_insert;
DROP TRIGGER IF EXISTS before_treasury_delete;
DROP TRIGGER IF EXISTS before_treasury_deposit_expenses_insert;
DROP TRIGGER IF EXISTS before_treasury_insert;
DROP TRIGGER IF EXISTS before_treasury_transfers_insert;
DROP TRIGGER IF EXISTS before_units_delete;
DROP TRIGGER IF EXISTS before_units_insert;
DROP TRIGGER IF EXISTS before_users_delete;

-- Orphaned by the same change.
DROP PROCEDURE IF EXISTS handle_processes_data;
DROP PROCEDURE IF EXISTS max_stock_transfer_id;

SET FOREIGN_KEY_CHECKS = 1;

-- The helpers exist only for the duration of this migration.
DROP PROCEDURE IF EXISTS ManageColumn;
DROP PROCEDURE IF EXISTS RenameColumnSafe;
DROP PROCEDURE IF EXISTS ManageConstraint;
DROP PROCEDURE IF EXISTS MigrateDataSafe;
