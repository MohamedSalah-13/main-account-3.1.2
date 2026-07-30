-- ======================================================
-- Genesis baseline schema
-- Creates the FULL current business schema for a brand-new client in one
-- shot: tables, triggers (audit log, permission auto-assignment, items),
-- the max_item_id/write_audit_log/truncate procedures, default seed data
-- (admin user, lookups, permission list), and all reporting views.
-- Mirrors scripts/main/ (the manual RunAllSqlScripts.bat bundle) - if you
-- change one, change the other.
-- SAFETY: this file must ONLY ever be executed by DatabaseMigrationService
-- against a database verified to be completely empty (no 'items' table).
-- It is NEVER safe to re-run against a populated/existing client database
-- (several CREATE INDEX statements below are not idempotent, and the seed
-- data section would duplicate rows).
-- ======================================================


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

CREATE INDEX items_package_item_idx ON items_package (item_id);
CREATE INDEX items_package_package_idx ON items_package (package_id);

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

CREATE INDEX items_units_items_num_fk ON items_units (items_id);

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

CREATE INDEX idx_stock_movements_item_stock_date
    ON stock_movements (item_id, stock_id, movement_date);

CREATE INDEX idx_stock_movements_reference
    ON stock_movements (reference_type, reference_id);

CREATE INDEX idx_stock_movements_stock_date
    ON stock_movements (stock_id, movement_date);

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

CREATE INDEX stock_transfer_stocks_stock_id_fk ON stock_transfer (stock_from);
CREATE INDEX stock_transfer_stocks_stock_id_fk_2 ON stock_transfer (stock_to);
CREATE INDEX stock_transfer_date_idx ON stock_transfer (transfer_date);

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

CREATE INDEX stock_transfer_list_item_idx ON stock_transfer_list (item_id);

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

CREATE INDEX treasury_deposit_expenses_date_idx ON treasury_deposit_expenses (date_inter);
CREATE INDEX treasury_deposit_expenses_treasury_idx ON treasury_deposit_expenses (treasury_id, date_inter);

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

CREATE INDEX treasury_transfers_date_idx ON treasury_transfers (transfer_date);

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

CREATE INDEX expenses_details_date_idx ON expenses_details (date);
CREATE INDEX expenses_details_treasury_idx ON expenses_details (treasury_id, date);

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

CREATE INDEX total_buy_sup_code_fk ON total_buy (sup_code);
CREATE INDEX total_buy_date_idx ON total_buy (invoice_date);
CREATE INDEX total_buy_treasury_idx ON total_buy (treasury_id, invoice_date);

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

CREATE INDEX total_buy_re_date_idx ON total_buy_re (invoice_date);
CREATE INDEX total_buy_re_treasury_idx ON total_buy_re (treasury_id, invoice_date);
CREATE INDEX total_buy_re_sup_idx ON total_buy_re (sup_id);

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

CREATE INDEX total_sales_sup_code_fk ON total_sales (sup_code);
CREATE INDEX total_sales_users_id_fk2 ON total_sales (delegate_id);
CREATE INDEX total_sales_date_idx ON total_sales (invoice_date);
CREATE INDEX total_sales_treasury_idx ON total_sales (treasury_id, invoice_date);

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

CREATE INDEX total_sales_re_date_idx ON total_sales_re (invoice_date);
CREATE INDEX total_sales_re_treasury_idx ON total_sales_re (treasury_id, invoice_date);
CREATE INDEX total_sales_re_sup_idx ON total_sales_re (sup_id);
CREATE INDEX total_sales_re_delegate_idx ON total_sales_re (delegate_id);

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

CREATE INDEX suppliers_accounts_numberInv_idx ON suppliers_accounts (numberInv);
CREATE INDEX suppliers_accounts_date_idx ON suppliers_accounts (account_date);

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

CREATE INDEX customers_accounts_numberInv_idx ON customers_accounts (numberInv);
CREATE INDEX customers_accounts_date_idx ON customers_accounts (account_date);

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

CREATE INDEX purchase_item_idx ON purchase (num);

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

CREATE INDEX purchase_re_item_idx ON purchase_re (item_id);

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

CREATE INDEX sales_item_idx ON sales (num);

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

CREATE INDEX sales_re_item_idx ON sales_re (item_id);

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

CREATE INDEX idx_user_shifts_user_open ON user_shifts (user_id, is_open);
CREATE INDEX idx_user_shifts_open_time ON user_shifts (open_time);

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

CREATE INDEX idx_audit_table_record ON audit_log (table_name, record_id);
CREATE INDEX idx_audit_user_time ON audit_log (user_id, action_time);
CREATE INDEX idx_audit_action_time ON audit_log (action_type, action_time);

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

CREATE INDEX treasury_movements_treasury_date_idx
    ON treasury_movements (treasury_id, movement_date, id);

CREATE INDEX treasury_movements_reference_idx
    ON treasury_movements (reference_type, reference_id);

CREATE INDEX treasury_movements_date_idx
    ON treasury_movements (movement_date);

-- =====================================================
-- 13) Item barcodes (multiple barcodes per item)
-- =====================================================

-- ======================================================
-- Migration: add support for multiple barcodes per item
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

-- =====================================================
-- 14) Audit log: write_audit_log procedure + triggers
-- =====================================================

DROP PROCEDURE IF EXISTS write_audit_log;

DELIMITER |
CREATE PROCEDURE write_audit_log(
    IN p_table_name VARCHAR(100),
    IN p_record_id VARCHAR(100),
    IN p_action_type VARCHAR(20),
    IN p_user_id INT,
    IN p_old_data JSON,
    IN p_new_data JSON,
    IN p_notes TEXT
)
BEGIN
    INSERT INTO audit_log
    (
        table_name,
        record_id,
        action_type,
        user_id,
        old_data,
        new_data,
        notes
    )
    VALUES
        (
            UPPER(p_table_name),
            p_record_id,
            UPPER(p_action_type),
            p_user_id,
            p_old_data,
            p_new_data,
            p_notes
        );
END;
|
DELIMITER ;

###############################################
DELIMITER |
CREATE TRIGGER audit_items_insert
    AFTER INSERT ON items
    FOR EACH ROW
BEGIN
    CALL write_audit_log(
            'items',
            NEW.id,
            'INSERT',
            COALESCE(@app_user_id, NEW.user_id, 1),
            NULL,
            JSON_OBJECT(
                    'id', NEW.id,
                    'barcode', NEW.barcode,
                    'nameItem', NEW.nameItem,
                    'buy_price', NEW.buy_price,
                    'first_balance', NEW.first_balance
            ),
            NULL
         );
END;
|
DELIMITER ;

DELIMITER |
CREATE TRIGGER audit_items_update
    AFTER UPDATE ON items
    FOR EACH ROW
BEGIN
    CALL write_audit_log(
            'items',
            NEW.id,
            'UPDATE',
            COALESCE(@app_user_id, NEW.user_id, OLD.user_id, 1),
            JSON_OBJECT(
                    'id', OLD.id,
                    'barcode', OLD.barcode,
                    'nameItem', OLD.nameItem,
                    'buy_price', OLD.buy_price,
                    'first_balance', OLD.first_balance
            ),
            JSON_OBJECT(
                    'id', NEW.id,
                    'barcode', NEW.barcode,
                    'nameItem', NEW.nameItem,
                    'buy_price', NEW.buy_price,
                    'first_balance', NEW.first_balance
            ),
            NULL
         );
END;
|
DELIMITER ;

DELIMITER |
CREATE TRIGGER audit_items_delete
    AFTER DELETE ON items
    FOR EACH ROW
BEGIN
    CALL write_audit_log(
            'items',
            OLD.id,
            'DELETE',
            COALESCE(@app_user_id, OLD.user_id, 1),
            JSON_OBJECT(
                    'id', OLD.id,
                    'barcode', OLD.barcode,
                    'nameItem', OLD.nameItem,
                    'buy_price', OLD.buy_price,
                    'first_balance', OLD.first_balance
            ),
            NULL,
            NULL
         );
END;
|
DELIMITER ;

-- =====================================================================
-- 1. جدول المستخدمين (Users)
-- =====================================================================
DELIMITER |
CREATE TRIGGER audit_users_insert AFTER INSERT ON users FOR EACH ROW
BEGIN
    CALL write_audit_log('users', NEW.id, 'INSERT', COALESCE(@app_user_id, 1), NULL,
                         JSON_OBJECT('id', NEW.id, 'user_name', NEW.user_name, 'user_activity', NEW.user_activity, 'user_available', NEW.user_available), NULL);
END;
|
CREATE TRIGGER audit_users_update AFTER UPDATE ON users FOR EACH ROW
BEGIN
    CALL write_audit_log('users', NEW.id, 'UPDATE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'user_name', OLD.user_name, 'user_activity', OLD.user_activity, 'user_available', OLD.user_available),
                         JSON_OBJECT('id', NEW.id, 'user_name', NEW.user_name, 'user_activity', NEW.user_activity, 'user_available', NEW.user_available), NULL);
END;
|
CREATE TRIGGER audit_users_delete AFTER DELETE ON users FOR EACH ROW
BEGIN
    CALL write_audit_log('users', OLD.id, 'DELETE', COALESCE(@app_user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'user_name', OLD.user_name, 'user_activity', OLD.user_activity), NULL, NULL);
END;
|
DELIMITER ;

-- =====================================================================
-- 2. جدول العملاء (Custom)
-- =====================================================================
DELIMITER |
CREATE TRIGGER audit_custom_insert AFTER INSERT ON custom FOR EACH ROW
BEGIN
    CALL write_audit_log('custom', NEW.id, 'INSERT', COALESCE(@app_user_id, NEW.user_id, 1), NULL,
                         JSON_OBJECT('id', NEW.id, 'name', NEW.name, 'limit_num', NEW.limit_num, 'first_balance', NEW.first_balance), NULL);
END;
|
CREATE TRIGGER audit_custom_update AFTER UPDATE ON custom FOR EACH ROW
BEGIN
    CALL write_audit_log('custom', NEW.id, 'UPDATE', COALESCE(@app_user_id, NEW.user_id, OLD.user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'name', OLD.name, 'limit_num', OLD.limit_num, 'first_balance', OLD.first_balance),
                         JSON_OBJECT('id', NEW.id, 'name', NEW.name, 'limit_num', NEW.limit_num, 'first_balance', NEW.first_balance), NULL);
END;
|
CREATE TRIGGER audit_custom_delete AFTER DELETE ON custom FOR EACH ROW
BEGIN
    CALL write_audit_log('custom', OLD.id, 'DELETE', COALESCE(@app_user_id, OLD.user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'name', OLD.name, 'first_balance', OLD.first_balance), NULL, NULL);
END;
|
DELIMITER ;

-- =====================================================================
-- 3. جدول الموردين (Suppliers)
-- =====================================================================
DELIMITER |
CREATE TRIGGER audit_suppliers_insert AFTER INSERT ON suppliers FOR EACH ROW
BEGIN
    CALL write_audit_log('suppliers', NEW.id, 'INSERT', COALESCE(@app_user_id, NEW.user_id, 1), NULL,
                         JSON_OBJECT('id', NEW.id, 'name', NEW.name, 'first_balance', NEW.first_balance), NULL);
END;
|
CREATE TRIGGER audit_suppliers_update AFTER UPDATE ON suppliers FOR EACH ROW
BEGIN
    CALL write_audit_log('suppliers', NEW.id, 'UPDATE', COALESCE(@app_user_id, NEW.user_id, OLD.user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'name', OLD.name, 'first_balance', OLD.first_balance),
                         JSON_OBJECT('id', NEW.id, 'name', NEW.name, 'first_balance', NEW.first_balance), NULL);
END;
|
CREATE TRIGGER audit_suppliers_delete AFTER DELETE ON suppliers FOR EACH ROW
BEGIN
    CALL write_audit_log('suppliers', OLD.id, 'DELETE', COALESCE(@app_user_id, OLD.user_id, 1),
                         JSON_OBJECT('id', OLD.id, 'name', OLD.name, 'first_balance', OLD.first_balance), NULL, NULL);
END;
|
DELIMITER ;

-- =====================================================================
-- 4. جدول إجمالي المبيعات (Total Sales)
-- =====================================================================
DELIMITER |
CREATE TRIGGER audit_total_sales_insert AFTER INSERT ON total_sales FOR EACH ROW
BEGIN
    CALL write_audit_log('total_sales', NEW.invoice_number, 'INSERT', COALESCE(@app_user_id, NEW.user_id, 1), NULL,
                         JSON_OBJECT('invoice_number', NEW.invoice_number, 'sup_code', NEW.sup_code, 'total', NEW.total, 'paid_up', NEW.paid_up, 'invoice_type', NEW.invoice_type), NULL);
END;
|
CREATE TRIGGER audit_total_sales_update AFTER UPDATE ON total_sales FOR EACH ROW
BEGIN
    CALL write_audit_log('total_sales', NEW.invoice_number, 'UPDATE', COALESCE(@app_user_id, NEW.user_id, OLD.user_id, 1),
                         JSON_OBJECT('invoice_number', OLD.invoice_number, 'sup_code', OLD.sup_code, 'total', OLD.total, 'paid_up', OLD.paid_up),
                         JSON_OBJECT('invoice_number', NEW.invoice_number, 'sup_code', NEW.sup_code, 'total', NEW.total, 'paid_up', NEW.paid_up), NULL);
END;
|
CREATE TRIGGER audit_total_sales_delete AFTER DELETE ON total_sales FOR EACH ROW
BEGIN
    CALL write_audit_log('total_sales', OLD.invoice_number, 'DELETE', COALESCE(@app_user_id, OLD.user_id, 1),
                         JSON_OBJECT('invoice_number', OLD.invoice_number, 'sup_code', OLD.sup_code, 'total', OLD.total), NULL, NULL);
END;
|
DELIMITER ;

-- =====================================================================
-- 5. جدول إجمالي المشتريات (Total Buy)
-- =====================================================================
DELIMITER |
CREATE TRIGGER audit_total_buy_insert AFTER INSERT ON total_buy FOR EACH ROW
BEGIN
    CALL write_audit_log('total_buy', NEW.invoice_number, 'INSERT', COALESCE(@app_user_id, NEW.user_id, 1), NULL,
                         JSON_OBJECT('invoice_number', NEW.invoice_number, 'sup_code', NEW.sup_code, 'total', NEW.total, 'paid_up', NEW.paid_up, 'invoice_type', NEW.invoice_type), NULL);
END;
|
CREATE TRIGGER audit_total_buy_update AFTER UPDATE ON total_buy FOR EACH ROW
BEGIN
    CALL write_audit_log('total_buy', NEW.invoice_number, 'UPDATE', COALESCE(@app_user_id, NEW.user_id, OLD.user_id, 1),
                         JSON_OBJECT('invoice_number', OLD.invoice_number, 'sup_code', OLD.sup_code, 'total', OLD.total, 'paid_up', OLD.paid_up),
                         JSON_OBJECT('invoice_number', NEW.invoice_number, 'sup_code', NEW.sup_code, 'total', NEW.total, 'paid_up', NEW.paid_up), NULL);
END;
|
CREATE TRIGGER audit_total_buy_delete AFTER DELETE ON total_buy FOR EACH ROW
BEGIN
    CALL write_audit_log('total_buy', OLD.invoice_number, 'DELETE', COALESCE(@app_user_id, OLD.user_id, 1),
                         JSON_OBJECT('invoice_number', OLD.invoice_number, 'sup_code', OLD.sup_code, 'total', OLD.total), NULL, NULL);
END;
|
DELIMITER ;

-- =====================================================================
-- 6. جدول الخزينة (Treasury)
-- =====================================================================
DELIMITER |
CREATE TRIGGER audit_treasury_insert AFTER INSERT ON treasury FOR EACH ROW
BEGIN
    CALL write_audit_log('treasury', NEW.id, 'INSERT', COALESCE(@app_user_id, NEW.user_id, 1), NULL,
                         JSON_OBJECT('id', NEW.id, 't_name', NEW.t_name, 'amount', NEW.amount), NULL);
END;
|
CREATE TRIGGER audit_treasury_update AFTER UPDATE ON treasury FOR EACH ROW
BEGIN
    CALL write_audit_log('treasury', NEW.id, 'UPDATE', COALESCE(@app_user_id, NEW.user_id, OLD.user_id, 1),
                         JSON_OBJECT('id', OLD.id, 't_name', OLD.t_name, 'amount', OLD.amount),
                         JSON_OBJECT('id', NEW.id, 't_name', NEW.t_name, 'amount', NEW.amount), NULL);
END;
|
CREATE TRIGGER audit_treasury_delete AFTER DELETE ON treasury FOR EACH ROW
BEGIN
    CALL write_audit_log('treasury', OLD.id, 'DELETE', COALESCE(@app_user_id, OLD.user_id, 1),
                         JSON_OBJECT('id', OLD.id, 't_name', OLD.t_name, 'amount', OLD.amount), NULL, NULL);
END;
|
DELIMITER ;
-- =====================================================
-- 15) User permission auto-assignment trigger
-- =====================================================

-- user permission
DROP TRIGGER IF EXISTS after_users_insert;

-- insert permission for new user
DELIMITER |
create trigger after_users_insert
    after insert
    on users
    for each row
begin
    declare maxPermissions int unsigned default (SELECT count(*) FROM permission);
    declare currentPermissionId int unsigned default 1;
    IF (NEW.id > 1) THEN
        while currentPermissionId <= maxPermissions
            do
                set @permissionId = (SELECT p.id FROM permission p WHERE p.id = currentPermissionId);
                insert into user_permission (permission_id, user_id)
                VALUES (@permissionId, NEW.id);
                set currentPermissionId = currentPermissionId + 1;
            end while;
    end if;

end;
|
DELIMITER ;


-- permission
DROP TRIGGER IF EXISTS after_permission_insert;

DELIMITER |
create trigger after_permission_insert
    after insert
    on permission
    for each row
begin
    INSERT INTO user_permission (permission_id, user_id, check_status)
    SELECT NEW.id, users.id, 0
    FROM users
    WHERE users.id != 1;
end;
|
DELIMITER ;
-- =====================================================
-- 16) Items triggers + max_item_id procedure
-- =====================================================

-- items
DROP TRIGGER IF EXISTS after_items_update;
DROP PROCEDURE IF EXISTS max_item_id;


/*----------------------------------------------- update -----------------------------------------------*/
DELIMITER |
create trigger after_items_update
    after update
    on items
    for each row
begin
    update items_stock
    set first_balance = NEW.first_balance
    where items_stock.item_id = NEW.id
      and items_stock.stock_id = 1;
end;
|
DELIMITER ;
/*----------------------------------------------- max_id -----------------------------------------------*/
DELIMITER |
create
    definer = root@localhost procedure max_item_id(OUT itemId int)
begin
    SET itemId = (SELECT id
                  from items
                  ORDER BY id DESC
                  LIMIT 1);
end;
|
DELIMITER ;

-- items_stock
DROP TRIGGER IF EXISTS before_items_stock_insert;

DELIMITER |
create trigger before_items_stock_insert
    before insert
    on items_stock
    for each row
begin
    -- Define a constant for the error message
    DECLARE err_msg VARCHAR(255) DEFAULT 'Cannot insert: Duplicate entry stock and item combination';

    -- Check if a matching stock and item combination already exists
    IF EXISTS (SELECT 1
               FROM items_stock
               WHERE items_stock.stock_id = NEW.stock_id
                 AND items_stock.item_id = NEW.item_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = err_msg;
    END IF;
end;
|
DELIMITER ;

-- items_units
DROP TRIGGER IF EXISTS before_items_units_insert;

DELIMITER |
create trigger before_items_units_insert
    before insert
    on items_units
    for each row
begin
    -- Define a constant for the error message
    DECLARE err_msg VARCHAR(255) DEFAULT 'Cannot insert : Duplicate entry combination';

    -- Get the latest item id
#     set NEW.items_id = (SELECT id FROM items ORDER BY id DESC LIMIT 1);

    -- Check if a matching stock and item combination already exists
    IF EXISTS (SELECT 1
               FROM items_units
               WHERE items_units.unit = NEW.unit
                 AND items_units.items_id = NEW.items_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = err_msg;
    END IF;
end;
|
DELIMITER ;



-- =====================================================
-- 17) Seed data (default admin user, lookups, permissions)
-- must run after the triggers above are created
-- =====================================================

# this use after create table and triggers
INSERT INTO users(id, user_name, user_pass, user_available)
VALUES (1, 'admin', 'admin', 1);
INSERT INTO type_price (name)
VALUES ('سعر1'),
       ('سعر2'),
       ('سعر3');

INSERT INTO table_area (area_name)
values ('القاهرة');

INSERT INTO custom(name, limit_num, price_id)
VALUES ('بيع نقدي', 5000, 1);

INSERT INTO suppliers(name)
VALUES ('مورد عام');

INSERT INTO units(unit_name)
VALUES ('قطعة'),
       ('كرتونه');
INSERT INTO stocks(stock_name)
VALUES ('الرئيسي');
INSERT INTO main_group(name_g)
VALUES ('عام 1');
INSERT INTO sub_group(name, main_id)
VALUES ('فرع 1', 1);

insert into jobs (id, job_name)
values (2, 'المدير'),
       (1, 'المسئول'),
       (4, 'مندوب'),
       (3, 'موظف');

insert into permission (name_permission)
values ('purchase_show'),
       ('purchase_update'),
       ('purchase_delete'),
       ('total_purchase_show'),
       ('total_purchase_show_invoice'),
       ('purchase_re_show'),
       ('purchase_re_update'),
       ('purchase_re_delete'),
       ('total_purchase_re_show'),
       ('total_purchase_re_show_invoice'),
       ('sales_show'),
       ('sales_update'),
       ('sales_delete'),
       ('total_sales_show'),
       ('total_sales_show_invoice'),
       ('sales_re_show'),
       ('sales_re_update'),
       ('sales_re_delete'),
       ('total_sales_re_show'),
       ('total_sales_re_show_invoice'),
       ('items_show'),
       ('items_update'),
       ('items_delete'),
       ('items_add_excel'),
       ('stock_show'),
       ('stock_update'),
       ('stock_delete'),
       ('stock_convert_show'),
       ('stock_convert_update'),
       ('stock_convert_delete'),
       ('main_group_show'),
       ('main_group_update'),
       ('main_group_delete'),
       ('sub_group_show'),
       ('sub_group_update'),
       ('sub_group_delete'),
       ('inventory_show'),
       ('treasury_show'),
       ('treasury_update'),
       ('treasury_delete'),
       ('units_show'),
       ('units_update'),
       ('units_delete'),
       ('sel_price_show'),
       ('sel_price_update'),
       ('sel_price_delete'),
       ('customer_show'),
       ('customer_update'),
       ('customer_delete'),
       ('customer_account_show'),
       ('customer_account_update'),
       ('customer_account_delete'),
       ('suppliers_show'),
       ('suppliers_update'),
       ('suppliers_delete'),
       ('suppliers_account_show'),
       ('suppliers_account_update'),
       ('suppliers_account_delete'),
       ('expenses_show'),
       ('expenses_update'),
       ('expenses_delete'),
       ('employee_show'),
       ('employee_update'),
       ('employee_delete'),
       ('setting_show'),
       ('setting_company_show'),
       ('setting_backup_show'),
       ('setting_other_show'),
       ('setting_items_show'),
       ('setting_shows_show'),
       ('invoice_profit_show'),
       ('employees_show_salary'),
       ('show_column_buy_price'),
       ('update_data_before_month'),
       ('show_data_before_month'),
       ('setting_update_name'),
       ('setting_update_pass'),
       ('reports_show_summary'),
       ('reports_show_items'),
       ('reports_show_customers'),
       ('reports_show_suppliers'),
       ('reports_show_customers_account_area'),
       ('reports_show_sales'),
       ('reports_show_purchase'),
       ('reports_show_day_details'),
       ('reports_show_delegate'),
       ('reports_show_profit');



INSERT INTO employees (column_name, birth_date, hire_date, salary, job)
VALUES ('بيع مباشر', CURRENT_DATE(), CURRENT_DATE(), 0, 4);
INSERT INTO treasury(t_name, amount)
VALUES ('الخزينة الرئيسية', 0);

# this use with type
insert into expenses (id, expenses_name)
values (1, 'مرتبات'),
       (2, 'كهرباء'),
       (3, 'سلف'),
       (4, 'مياه'),
       (5, 'إيجارات'),
       (6, 'أخرى');

-- =====================================================
-- 18) Data-reset utility procedures
-- =====================================================

/*------------------------------------ truncateTableSales - 6 tables ------------------------------------ */
DROP procedure if exists truncateTableSales;

DELIMITER |
create
    definer = root@localhost procedure truncateTableSales(IN salesReturn tinyint(1), IN deleteSales tinyint(1),
                                                          IN deleteAccount tinyint(1), IN deleteName tinyint(1))
begin
    SET FOREIGN_KEY_CHECKS = 0;
    if (salesReturn) THEN
        TRUNCATE table total_sales_re;
        TRUNCATE table sales_re;
    End IF;

    if (deleteSales) THEN
        TRUNCATE table total_sales;
        TRUNCATE table sales;
    End IF;

    IF (deleteAccount) Then
        TRUNCATE table customers_accounts;
    End IF;

    IF (deleteName) Then
        # this use for customer
        TRUNCATE table custom;
        INSERT INTO custom(id, name, limit_num, price_id)
        VALUES (1, 'بيع نقدى', 5000, 1);
    End IF;

    SET FOREIGN_KEY_CHECKS = 1;
END
|
DELIMITER ;

/*------------------------------------ truncateTablePurchase - 6 tables ------------------------------------ */
DROP procedure if exists truncateTablePurchase;

DELIMITER |
create
    definer = root@localhost procedure truncateTablePurchase(IN deletePurchaseReturn tinyint(1),
                                                             IN deletePurchase tinyint(1),
                                                             IN deleteAccount tinyint(1), IN deleteName tinyint(1))
begin
    SET FOREIGN_KEY_CHECKS = 0;
    if (deletePurchaseReturn) THEN
        TRUNCATE table total_buy_re;
        TRUNCATE table purchase_re;
    End IF;

    if (deletePurchase) THEN
        TRUNCATE table total_buy;
        TRUNCATE table purchase;
    End IF;

    IF (deleteAccount) Then
        TRUNCATE table suppliers_accounts;
    End IF;

    IF (deleteName) Then
        TRUNCATE table suppliers;
        INSERT INTO suppliers(id, name)
        VALUES (1, 'مورد عام');
    End IF;

    SET FOREIGN_KEY_CHECKS = 1;
END
|
DELIMITER ;

/*------------------------------------ truncateTableItems -12 tables ------------------------------------ */
DROP procedure IF EXISTS truncateTableItems;
DELIMITER |
CREATE
    DEFINER = root@localhost PROCEDURE truncateTableItems(IN deleteItems TINYINT(1),
                                                          IN deleteStock TINYINT(1),
                                                          IN deleteSubGroup TINYINT(1),
                                                          IN deleteMainGroup TINYINT(1))
BEGIN
    SET FOREIGN_KEY_CHECKS = 0;

    IF (deleteItems) THEN
        CALL truncateAndInitializeItemsTables();
    END IF;

    IF (deleteStock) THEN
        CALL truncateAndInitializeStocksTables();
    END IF;

    IF (deleteSubGroup) THEN
        CALL truncateAndInitializeSubGroupTable();
    END IF;

    IF (deleteMainGroup) THEN
        CALL truncateAndInitializeMainGroupTable();
    END IF;

    SET FOREIGN_KEY_CHECKS = 1;
END
|

DROP PROCEDURE IF EXISTS truncateAndInitializeItemsTables;
CREATE PROCEDURE truncateAndInitializeItemsTables()
BEGIN
    TRUNCATE TABLE units;
    INSERT INTO units(unit_name) VALUES ('قطعة'), ('كرتونة');

    TRUNCATE TABLE type_price;
    INSERT INTO type_price (name)
    VALUES ('سعر1'),
           ('سعر2'),
           ('سعر3');

    TRUNCATE TABLE items;
    TRUNCATE TABLE items_package;
    TRUNCATE TABLE items_units;
    TRUNCATE TABLE items_stock;
    TRUNCATE TABLE stock_movements;
END
|

DROP PROCEDURE IF EXISTS truncateAndInitializeStocksTables;
CREATE PROCEDURE truncateAndInitializeStocksTables()
BEGIN
    TRUNCATE TABLE stock_movements;
    TRUNCATE TABLE stocks;
    TRUNCATE TABLE stock_transfer;
    TRUNCATE TABLE stock_transfer_list;
    INSERT INTO stocks(stock_name) VALUES ('الرئيسى');
END
|

DROP PROCEDURE IF EXISTS truncateAndInitializeSubGroupTable;
CREATE PROCEDURE truncateAndInitializeSubGroupTable()
BEGIN
    TRUNCATE TABLE sub_group;
    INSERT INTO sub_group(name, main_id) VALUES ('فرع 1', 1);
END
|

DROP PROCEDURE IF EXISTS truncateAndInitializeMainGroupTable;
CREATE PROCEDURE truncateAndInitializeMainGroupTable()
BEGIN
    TRUNCATE TABLE main_group;
    INSERT INTO main_group(name_g) VALUES ('عام 1');
END
|
DELIMITER ;

/*------------------------------------ truncateTableOthers -8 tables ------------------------------------ */
DROP procedure if exists truncateTableOthers;

DELIMITER |
create
    definer = root@localhost procedure truncateTableOthers(IN deleteEmployees tinyint(1),
                                                           IN deleteProcesses tinyint(1),
                                                           IN deleteExpenses tinyint(1), IN deleteUsers tinyint(1))
begin
    SET FOREIGN_KEY_CHECKS = 0;

    IF (deleteUsers) Then
        TRUNCATE table users;
        TRUNCATE table user_permission;
        INSERT INTO users(id, user_name, user_pass, user_available) VALUES (1, 'admin', 'admin', 1);

    End IF;

    if (deleteEmployees) THEN
        TRUNCATE table employees;
        INSERT INTO employees (column_name, birth_date, hire_date, salary, job)
        VALUES ('بيع مباشر', CURRENT_DATE(), CURRENT_DATE(), 0, 4);
        TRUNCATE table treasury_deposit_expenses;
        TRUNCATE table treasury_transfers;
        TRUNCATE table treasury;
        INSERT INTO treasury(t_name, amount)
        VALUES ('الخزينة الرئيسية', 0);

        TRUNCATE table targeted_sales;

    End IF;

    IF (deleteExpenses) Then
        TRUNCATE table expenses_details;
        TRUNCATE table expense_salary;
    End IF;

    if (deleteProcesses) THEN
        TRUNCATE table audit_log;
    End IF;

    SET FOREIGN_KEY_CHECKS = 1;
END
|
DELIMITER ;

/*------------------------------------ delete all ------------------------------------ */
# CALL truncateTableSales(true, true, true, true);
# CALL truncateTablePurchase(true, true, true, true);
# CALL truncateTableOthers(true, true, true, true);
# CALL truncateTableItems(true, true, true, true);

/*------------------------------------ table not truncate ------------------------------------ */
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'account_system_db'
  AND TABLE_TYPE = 'BASE TABLE'
  AND table_name NOT IN (
                         'total_sales_re', 'sales_re', 'total_sales', 'sales',
                         'customers_accounts', 'custom', 'total_buy_re', 'purchase_re',
                         'total_buy', 'purchase', 'suppliers_accounts', 'suppliers',
                         'units', 'type_price', 'items', 'items_price',
                         'items_units', 'items_stock', 'stocks', 'stock_transfer',
                         'stock_transfer_list', 'sub_group', 'main_group', 'users',
                         'user_permission', 'employees', 'treasury_deposit_expenses',
                         'treasury_transfers', 'treasury', 'targeted_sales', 'expenses_details',
                         'expense_salary', 'processes_data'
    );
-- =====================================================
-- 19) Reporting/aggregation views
-- =====================================================

-- =====================================================================
-- V017 — Views (cleaned & optimized)
-- =====================================================================

-- --------------------------------------purchase_names_table---------------------------------------

CREATE OR REPLACE VIEW purchase_names_table AS
SELECT p.id,
       p.invoice_number,
       p.num,
       p.type,
       p.type_value,
       p.quantity AS quantity,
       p.price,
       p.discount,
       p.expiration_date,
       i.nameItem,
       i.barcode,
       u.unit_name,
       t.id       AS name_id,
       t.name,
       tb.invoice_date,
       tb.stock_id
FROM purchase p
         JOIN items     i  ON i.id = p.num
         JOIN units     u  ON p.type = u.unit_id
         JOIN total_buy tb ON tb.invoice_number = p.invoice_number
         JOIN suppliers t  ON t.id = tb.sup_code;

-- --------------------------------------sales_names_table------------------------------------------

CREATE OR REPLACE VIEW sales_names_table AS
SELECT s.id,
       s.invoice_number,
       s.num,
       s.type,
       s.type_value,
       s.quantity,
       s.price,
       s.buy_price,
       s.total_sel_price AS total_sales,
       s.total_buy_price AS total_buy,
       s.total_profit,
       s.discount,
       s.item_has_package,
       s.expiration_date,
       i.nameItem,
       i.barcode,
       u.unit_name,
       c.id              AS name_id,
       c.name,
       ts.invoice_date,
       ts.stock_id
FROM sales s
         JOIN items       i  ON i.id = s.num
         JOIN units       u  ON s.type = u.unit_id
         JOIN total_sales ts ON ts.invoice_number = s.invoice_number
         JOIN custom      c  ON c.id = ts.sup_code;

-- --------------------------------------sales_return_names_table-----------------------------------

CREATE OR REPLACE VIEW sales_return_names_table AS
SELECT sr.id,
       sr.invoice_number,
       sr.item_id,
       sr.type,
       sr.type_value,
       sr.quantity,
       sr.price,
       sr.buy_price,
       sr.total_sel_price,
       sr.total_buy_price,
       sr.total_profit,
       sr.discount,
       sr.expiration_date,
       i.nameItem,
       i.barcode,
       u.unit_name,
       tsr.invoice_date,
       tsr.stock_id
FROM sales_re sr
         JOIN items          i   ON i.id = sr.item_id
         JOIN units          u   ON sr.type = u.unit_id
         JOIN total_sales_re tsr ON tsr.id = sr.invoice_number;

-- --------------------------------------purchase_return_names_table--------------------------------

CREATE OR REPLACE VIEW purchase_return_names_table AS
SELECT pr.id,
       pr.invoice_number,
       pr.item_id,
       pr.type,
       pr.type_value,
       pr.quantity,
       pr.price,
       pr.discount,
       pr.expiration_date,
       i.nameItem,
       i.barcode,
       u.unit_name,
       tbr.invoice_date,
       tbr.stock_id
FROM purchase_re pr
         JOIN items        i   ON i.id = pr.item_id
         JOIN units        u   ON pr.type = u.unit_id
         JOIN total_buy_re tbr ON tbr.id = pr.invoice_number;

-- --------------------------------------stock_transfer_view----------------------------------------

CREATE OR REPLACE VIEW stock_transfer_view AS
SELECT st.id,
       st.transfer_date,
       st.stock_from,
       st.stock_to,
       stf.stock_name AS name_from,
       stt.stock_name AS name_to,
       stl.item_id,
       stl.quantity,
       i.nameItem
FROM stock_transfer st
         JOIN stock_transfer_list stl ON st.id = stl.stock_transfer_id
         JOIN items  i   ON i.id = stl.item_id
         JOIN stocks stf ON stf.stock_id = st.stock_from
         JOIN stocks stt ON stt.stock_id = st.stock_to;

-- --------------------------------------quantity_items_table (optimized via JOINs)----------------

CREATE OR REPLACE VIEW quantity_items_table AS
WITH purchase_agg AS (SELECT stock_id, num AS item_id,
                             SUM(quantity * type_value) AS qty
                      FROM purchase_names_table
                      GROUP BY stock_id, num),
     sales_agg AS (SELECT stock_id, num AS item_id,
                          SUM(quantity * type_value) AS qty
                   FROM sales_names_table
                   GROUP BY stock_id, num),
     purchase_re_agg AS (SELECT stock_id, item_id,
                                SUM(quantity * type_value) AS qty
                         FROM purchase_return_names_table
                         GROUP BY stock_id, item_id),
     sales_re_agg AS (SELECT stock_id, item_id,
                             SUM(quantity * type_value) AS qty
                      FROM sales_return_names_table
                      GROUP BY stock_id, item_id),
     transfer_from_agg AS (SELECT stock_from AS stock_id, item_id, SUM(quantity) AS qty
                           FROM stock_transfer_view
                           GROUP BY stock_from, item_id),
     transfer_to_agg AS (SELECT stock_to AS stock_id, item_id, SUM(quantity) AS qty
                         FROM stock_transfer_view
                         GROUP BY stock_to, item_id)
SELECT ist.item_id,
       ist.stock_id,
       ist.first_balance,
       COALESCE(pa.qty,   0) AS quantityPurchase,
       COALESCE(sa.qty,   0) AS quantitySales,
       COALESCE(pra.qty,  0) AS quantityPurchaseRe,
       COALESCE(sra.qty,  0) AS quantitySalesRe,
       COALESCE(tfa.qty,  0) AS fromStock,
       COALESCE(tta.qty,  0) AS toStock
FROM items_stock ist
         LEFT JOIN purchase_agg     pa  ON pa.stock_id  = ist.stock_id AND pa.item_id  = ist.item_id
         LEFT JOIN sales_agg        sa  ON sa.stock_id  = ist.stock_id AND sa.item_id  = ist.item_id
         LEFT JOIN purchase_re_agg  pra ON pra.stock_id = ist.stock_id AND pra.item_id = ist.item_id
         LEFT JOIN sales_re_agg     sra ON sra.stock_id = ist.stock_id AND sra.item_id = ist.item_id
         LEFT JOIN transfer_from_agg tfa ON tfa.stock_id = ist.stock_id AND tfa.item_id = ist.item_id
         LEFT JOIN transfer_to_agg   tta ON tta.stock_id = ist.stock_id AND tta.item_id = ist.item_id;

-- --------------------------------------total_sales_names_table------------------------------------

CREATE OR REPLACE VIEW total_sales_names_table AS
WITH TotalPaidAmounts AS (SELECT numberInv AS InvoiceNumber,
                                 SUM(paid) AS TotalPaid
                          FROM customers_accounts
                          WHERE numberInv > 0
                          GROUP BY numberInv),
     sales_invoice_profit AS (SELECT invoice_number,
                                     SUM(total_profit)    AS total_profit,
                                     SUM(total_buy_price) AS total_buy_price
                              FROM sales
                              GROUP BY invoice_number)
SELECT ts.invoice_number,
       ts.sup_code,
       ts.invoice_type,
       ts.invoice_date,
       ts.total,
       ts.discount,
       ts.paid_up,
       ts.stock_id,
       ts.delegate_id,
       ts.treasury_id,
       ts.notes,
       ts.date_insert,
       c.name,
       s.stock_name,
       e.column_name,
       t.t_name,
       ts.user_id,
       ROUND(sip.total_profit, 2)                                   AS total_profit,
       sip.total_buy_price,
       ROUND((sip.total_profit * 100) / NULLIF(ts.total, 0), 2)     AS profit_percent,
       COALESCE(tpa.TotalPaid, 0)                                   AS OtherPaid
FROM total_sales ts
         JOIN custom    c  ON c.id = ts.sup_code
         JOIN stocks    s  ON s.stock_id = ts.stock_id
         JOIN employees e  ON ts.delegate_id = e.id
         JOIN treasury  t  ON ts.treasury_id = t.id
         LEFT JOIN sales_invoice_profit sip ON ts.invoice_number = sip.invoice_number
         LEFT JOIN TotalPaidAmounts     tpa ON ts.invoice_number = tpa.InvoiceNumber;

-- --------------------------------------total_purchase_names_table---------------------------------

CREATE OR REPLACE VIEW total_purchase_names_table AS
WITH PaidAmounts AS (SELECT numberInv AS InvoiceNumber, SUM(paid) AS total_paid
                     FROM suppliers_accounts
                     WHERE numberInv > 0
                     GROUP BY numberInv)
SELECT tb.invoice_number,
       tb.sup_code,
       tb.invoice_type,
       tb.invoice_date,
       tb.total,
       tb.discount,
       tb.paid_up,
       tb.stock_id,
       tb.treasury_id,
       tb.notes,
       tb.date_insert,
       c.name,
       s.stock_name,
       t.t_name,
       tb.user_id,
       COALESCE(pa.total_paid, 0) AS OtherPaid
FROM total_buy tb
         JOIN suppliers c ON c.id = tb.sup_code
         JOIN stocks    s ON s.stock_id = tb.stock_id
         JOIN treasury  t ON tb.treasury_id = t.id
         LEFT JOIN PaidAmounts pa ON tb.invoice_number = pa.InvoiceNumber;

-- --------------------------------------total_purchase_return_names_table--------------------------

CREATE OR REPLACE VIEW total_purchase_return_names_table AS
SELECT tbr.id,
       tbr.sup_id,
       tbr.invoice_date,
       tbr.total,
       tbr.discount,
       tbr.paid_to_treasury,
       tbr.stock_id,
       tbr.treasury_id,
       tbr.notes,
       tbr.invoice_type,
       tbr.date_insert,
       c.name,
       s.stock_name,
       t.t_name,
       tbr.user_id
FROM total_buy_re tbr
         JOIN suppliers c ON c.id = tbr.sup_id
         JOIN stocks    s ON s.stock_id = tbr.stock_id
         JOIN treasury  t ON tbr.treasury_id = t.id;

-- --------------------------------------total_sales_return_names_table-----------------------------

CREATE OR REPLACE VIEW total_sales_return_names_table AS
WITH sales_invoice_profit AS (SELECT invoice_number,
                                     SUM(total_profit)    AS total_profit,
                                     SUM(total_buy_price) AS total_buy_price
                              FROM sales_re
                              GROUP BY invoice_number)
SELECT tsr.id,
       tsr.sup_id,
       tsr.invoice_date,
       tsr.total,
       tsr.discount,
       tsr.paid_from_treasury,
       tsr.stock_id,
       tsr.delegate_id,
       tsr.treasury_id,
       tsr.notes,
       tsr.invoice_type,
       tsr.date_insert,
       c.name,
       s.stock_name,
       t.t_name,
       e.column_name,
       tsr.user_id,
       ROUND(sip.total_profit, 2)                                AS total_profit,
       sip.total_buy_price,
       ROUND((sip.total_profit * 100) / NULLIF(tsr.total, 0), 2) AS profit_percent
FROM total_sales_re tsr
         JOIN custom    c ON c.id = tsr.sup_id
         JOIN stocks    s ON s.stock_id = tsr.stock_id
         JOIN treasury  t ON tsr.treasury_id = t.id
         JOIN employees e ON e.id = tsr.delegate_id
         LEFT JOIN sales_invoice_profit sip ON tsr.id = sip.invoice_number;

-- --------------------------------------account_customer_table-------------------------------------

CREATE OR REPLACE VIEW account_customer_table AS
SELECT 0                                     AS account_num,
       c.id                                  AS account_code,
       DATE_FORMAT(c.created_at, '%Y-%m-%d') AS account_date,
       c.first_balance                       AS purchase,
       0                                     AS discount,
       0                                     AS paid,
       'رصيد اول'                            AS notes,
       1                                     AS information,
       0                                     AS type,
       c.created_at                          AS created_at,
       0                                     AS treasury_id,
       0                                     AS numberInv
FROM custom c
UNION ALL
SELECT account_num,
       account_code,
       account_date,
       purchase,
       0        AS discount,
       paid,
       notes,
       2        AS information,
       0        AS type,
       created_at,
       treasury_id,
       numberInv
FROM customers_accounts
UNION ALL
SELECT invoice_number,
       sup_code,
       invoice_date,
       total,
       discount,
       paid_up,
       notes,
       3           AS information,
       invoice_type AS type,
       date_insert,
       treasury_id,
       0           AS numberInv
FROM total_sales
UNION ALL
-- إذا كانت الفاتورة نقدا يتم خصم كل المدفوع
-- واذا كانت اجل: مبلغ من الخزينة ومبلغ من الحساب
SELECT tsr.id,
       tsr.sup_id,
       tsr.invoice_date,
       IF(tsr.invoice_type = 1, tsr.total, 0),
       IF(tsr.invoice_type = 1, tsr.discount, 0),
       tsr.paid_from_treasury,
       tsr.notes,
       4                    AS information,
       tsr.invoice_type     AS type,
       tsr.date_insert,
       tsr.treasury_id,
       0                    AS numberInv
FROM total_sales_re tsr
ORDER BY created_at;

-- --------------------------------------account_suppliers_table------------------------------------

CREATE OR REPLACE VIEW account_suppliers_table AS
SELECT 0                                      AS account_num,
       c.id                                   AS account_code,
       DATE_FORMAT(c.date_insert, '%Y-%m-%d') AS account_date,
       c.first_balance                        AS purchase,
       0                                      AS discount,
       0                                      AS paid,
       'رصيد اول'                             AS notes,
       1                                      AS information,
       0                                      AS type,
       c.date_insert                          AS date_insert,
       0                                      AS treasury_id,
       0                                      AS numberInv
FROM suppliers c
UNION ALL
SELECT account_num,
       account_code,
       account_date,
       purchase,
       0        AS discount,
       paid,
       notes,
       2        AS information,
       0        AS type,
       date_insert,
       treasury_id,
       numberInv
FROM suppliers_accounts
UNION ALL
SELECT invoice_number,
       sup_code,
       invoice_date,
       total,
       discount,
       paid_up,
       total_buy.notes,
       3            AS information,
       invoice_type AS type,
       date_insert,
       treasury_id,
       0            AS numberInv
FROM total_buy
UNION ALL
SELECT tbr.id,
       tbr.sup_id,
       tbr.invoice_date,
       IF(tbr.invoice_type = 1, tbr.total, 0),
       IF(tbr.invoice_type = 1, tbr.discount, 0),
       tbr.paid_to_treasury,
       tbr.notes,
       4                AS information,
       tbr.invoice_type AS type,
       tbr.date_insert,
       tbr.treasury_id,
       0                AS numberInv
FROM total_buy_re tbr
ORDER BY date_insert;

-- --------------------------------------card_item_view---------------------------------------------

CREATE OR REPLACE VIEW card_item_view AS
WITH sales_data AS (SELECT s.id,
                           s.invoice_number,
                           t.invoice_date,
                           s.num       AS item_num,
                           s.type      AS unit_type,
                           s.quantity,
                           s.price,
                           s.buy_price,
                           s.discount,
                           c.name      AS name_custom,
                           t.date_insert,
                           t.delegate_id,
                           'sales'     AS table_name,
                           s.expiration_date
                    FROM sales s
                             JOIN total_sales t ON t.invoice_number = s.invoice_number
                             JOIN custom      c ON t.sup_code = c.id),
     sales_return_data AS (SELECT sre.id,
                                  sre.invoice_number,
                                  t.invoice_date,
                                  sre.item_id AS item_num,
                                  sre.type    AS unit_type,
                                  sre.quantity,
                                  sre.price,
                                  sre.buy_price,
                                  0           AS discount,
                                  c.name      AS name_custom,
                                  t.date_insert,
                                  t.delegate_id,
                                  'sales_re'  AS table_name,
                                  sre.expiration_date
                           FROM sales_re sre
                                    JOIN total_sales_re t ON t.id = sre.invoice_number
                                    JOIN custom         c ON t.sup_id = c.id),
     purchase_data AS (SELECT p.id,
                              p.invoice_number,
                              t.invoice_date,
                              p.num      AS item_num,
                              p.type     AS unit_type,
                              p.quantity,
                              p.price,
                              0          AS buy_price,
                              p.discount,
                              s.name     AS name_custom,
                              t.date_insert,
                              0          AS delegate_id,
                              'purchase' AS table_name,
                              p.expiration_date
                       FROM purchase p
                                JOIN total_buy  t ON t.invoice_number = p.invoice_number
                                JOIN suppliers  s ON t.sup_code = s.id),
     purchase_return_data AS (SELECT pre.id,
                                     pre.invoice_number,
                                     t.invoice_date,
                                     pre.item_id AS item_num,
                                     pre.type    AS unit_type,
                                     pre.quantity,
                                     pre.price,
                                     0           AS buy_price,
                                     0           AS discount,
                                     s.name      AS name_custom,
                                     t.date_insert,
                                     0           AS delegate_id,
                                     'purchase_re' AS table_name,
                                     pre.expiration_date
                              FROM purchase_re pre
                                       JOIN total_buy_re t ON t.id = pre.invoice_number
                                       JOIN suppliers    s ON t.sup_id = s.id)
SELECT * FROM sales_data
UNION ALL
SELECT * FROM sales_return_data
UNION ALL
SELECT * FROM purchase_data
UNION ALL
SELECT * FROM purchase_return_data;

-- --------------------------------------card_item_view_details-------------------------------------

CREATE OR REPLACE VIEW card_item_view_details AS
SELECT c.id,
       c.invoice_number,
       c.invoice_date,
       c.item_num,
       c.unit_type,
       c.quantity,
       c.price,
       c.buy_price,
       IF(c.table_name IN ('purchase','purchase_re'), 0,
          (c.price - c.buy_price) * c.quantity) AS profit,
       c.discount,
       c.name_custom,
       c.date_insert,
       c.delegate_id,
       em.column_name                            AS delegate_name,
       c.table_name,
       i.barcode,
       i.nameItem,
       un.unit_name,
       c.expiration_date
FROM card_item_view c
         JOIN items i  ON c.item_num  = i.id
         JOIN units un ON c.unit_type = un.unit_id
         LEFT JOIN employees em ON c.delegate_id = em.id;

-- --------------------------------------expenses_details_view--------------------------------------

CREATE OR REPLACE VIEW expenses_details_view AS
SELECT ed.id,
       ed.type_code,
       ed.date,
       ed.amount,
       ed.notes,
       ed.treasury_id,
       ed.emp_id,
       e.expenses_name,
       IFNULL(e2.column_name, '') AS column_name
FROM expenses_details ed
         JOIN expenses e ON e.id = ed.type_code
         LEFT JOIN expense_salary es ON ed.id = es.expenses_details_id
         LEFT JOIN employees     e2 ON e2.id = es.employee_id;

-- --------------------------------------mini_quantity_view-----------------------------------------

CREATE OR REPLACE VIEW mini_quantity_view AS
WITH calculated_balance AS (SELECT item_id,
                                   SUM((first_balance + quantityPurchase + quantitySalesRe + toStock) -
                                       (quantitySales + quantityPurchaseRe + fromStock)) AS balance
                            FROM quantity_items_table
                            GROUP BY item_id)
SELECT i.id,
       i.nameItem,
       i.mini_quantity,
       cb.balance
FROM items i
         JOIN calculated_balance cb ON i.id = cb.item_id
WHERE i.mini_quantity >= cb.balance;

-- --------------------------------------target_delegate--------------------------------------------

CREATE OR REPLACE VIEW target_delegate AS
WITH sales_sums AS (SELECT delegate_id,
                           YEAR(invoice_date)  AS sales_year,
                           MONTH(invoice_date) AS sales_month,
                           SUM(total)          AS total_sales_sum
                    FROM total_sales
                    GROUP BY delegate_id, YEAR(invoice_date), MONTH(invoice_date)),
     sales_re_sums AS (SELECT delegate_id,
                              YEAR(invoice_date)  AS sales_year,
                              MONTH(invoice_date) AS sales_month,
                              SUM(total)          AS total_sales_re_sum
                       FROM total_sales_re
                       GROUP BY delegate_id, YEAR(invoice_date), MONTH(invoice_date)),
     sales_data AS (SELECT s.delegate_id,
                           s.sales_year,
                           s.sales_month,
                           COALESCE(s.total_sales_sum,    0)                                  AS total_sales_sum,
                           COALESCE(sr.total_sales_re_sum, 0)                                 AS total_sales_re_sum,
                           COALESCE(s.total_sales_sum, 0) - COALESCE(sr.total_sales_re_sum,0) AS sales_difference
                    FROM sales_sums s
                             LEFT JOIN sales_re_sums sr
                                       ON sr.delegate_id = s.delegate_id
                                           AND sr.sales_year  = s.sales_year
                                           AND sr.sales_month = s.sales_month)
SELECT e.id                                                           AS employee_id,
       e.column_name                                                  AS employee_name,
       sd.total_sales_sum,
       sd.total_sales_re_sum,
       sd.sales_difference                                            AS Amount,
       tgt.target_ratio1,
       tgt.rate_1,
       tgt.target_ratio2,
       tgt.rate_2,
       tgt.target_ratio3,
       tgt.rate_3,
       tgt.target,
       sd.sales_year,
       sd.sales_month,
       IF(sd.sales_difference >= (tgt.target * tgt.target_ratio1) / 100,
          (sd.sales_difference * tgt.rate_1) / 100,
          IF(sd.sales_difference >= (tgt.target * tgt.target_ratio2) / 100,
             (sd.sales_difference * tgt.rate_2) / 100,
             (sd.sales_difference * tgt.rate_3) / 100)) AS commission
FROM employees e
         JOIN sales_data      sd  ON e.id = sd.delegate_id
         JOIN targeted_sales  tgt ON e.id = tgt.delegate_id
ORDER BY e.id;

-- --------------------------------------treasury_balance-------------------------------------------

CREATE OR REPLACE VIEW treasury_balance AS
WITH cte_union_data AS (SELECT invoice_number AS id_no,
                               invoice_date   AS date_val,
                               0              AS income,
                               paid_up        AS output,
                               treasury_id,
                               date_insert,
                               user_id,
                               'المشتريات'    AS information
                        FROM total_buy
                        UNION ALL
                        SELECT id,
                               invoice_date,
                               IF(invoice_type = 1, paid_to_treasury, total - discount - paid_to_treasury) AS income,
                               0              AS output,
                               treasury_id,
                               date_insert,
                               user_id,
                               'مرتجع المشتريات'
                        FROM total_buy_re
                        UNION ALL
                        SELECT invoice_number,
                               invoice_date,
                               paid_up,
                               0,
                               treasury_id,
                               date_insert,
                               user_id,
                               'المبيعات'
                        FROM total_sales
                        UNION ALL
                        SELECT id,
                               invoice_date,
                               0,
                               IF(invoice_type = 1, paid_from_treasury, total - discount - paid_from_treasury) AS output,
                               treasury_id,
                               date_insert,
                               user_id,
                               'مرتجع المبيعات'
                        FROM total_sales_re
                        UNION ALL
                        SELECT account_num,
                               account_date,
                               paid,
                               0,
                               treasury_id,
                               created_at,
                               user_id,
                               'حسابات العملاء'
                        FROM customers_accounts
                        UNION ALL
                        SELECT account_num,
                               account_date,
                               0,
                               paid,
                               treasury_id,
                               date_insert,
                               user_id,
                               'حسابات الموردين'
                        FROM suppliers_accounts
                        UNION ALL
                        SELECT id,
                               date,
                               0,
                               amount,
                               treasury_id,
                               date_insert,
                               user_id,
                               'المصروفات'
                        FROM expenses_details
                        UNION ALL
                        SELECT id,
                               date_inter,
                               IF(deposit_or_expenses = 1, amount, 0) AS income,
                               IF(deposit_or_expenses = 2, amount, 0) AS output,
                               treasury_id,
                               date_insert,
                               user_id,
                               IF(deposit_or_expenses = 1, 'إيداع', 'صرف')
                        FROM treasury_deposit_expenses)
SELECT c.id_no,
       c.date_val,
       c.income,
       c.output,
       c.treasury_id,
       c.date_insert,
       c.user_id,
       c.information,
       t.t_name    AS treasury_name,
       u.user_name AS user_name
FROM cte_union_data c
         JOIN treasury t ON t.id = c.treasury_id
         JOIN users    u ON u.id = c.user_id
ORDER BY date_val;

-- --------------------------------------treasury_transfers_and_names-------------------------------

CREATE OR REPLACE VIEW treasury_transfers_and_names AS
SELECT tt.id,
       tt.treasury_from,
       tt.treasury_to,
       tt.amount,
       tt.transfer_date,
       tt.notes,
       tFrom.t_name AS treasury_name_from,
       tTo.t_name   AS treasury_name_to
FROM treasury_transfers tt
         JOIN treasury tFrom ON tFrom.id = tt.treasury_from
         JOIN treasury tTo   ON tTo.id   = tt.treasury_to;

-- --------------------------------------treasury_balance_after_convert-----------------------------

CREATE OR REPLACE VIEW treasury_balance_after_convert AS
WITH sum_treasury_amount_from AS (SELECT treasury_from, COALESCE(SUM(amount), 0) AS sum_transfer_from
                                  FROM treasury_transfers
                                  GROUP BY treasury_from),
     sum_treasury_amount_to AS (SELECT treasury_to, COALESCE(SUM(amount), 0) AS sum_transfer_to
                                FROM treasury_transfers
                                GROUP BY treasury_to)
SELECT treasury.*,
       f.sum_transfer_from,
       t.sum_transfer_to,
       (treasury.amount + COALESCE(t.sum_transfer_to, 0) - COALESCE(f.sum_transfer_from, 0))
           AS amount_after_transfer
FROM treasury
         LEFT JOIN sum_treasury_amount_from f ON f.treasury_from = treasury.id
         LEFT JOIN sum_treasury_amount_to   t ON t.treasury_to   = treasury.id;

-- --------------------------------------account_customer_totals------------------------------------

CREATE OR REPLACE VIEW account_customer_totals AS
SELECT act.account_code,
       c.name,
       SUM(act.purchase)                                           AS purchase,
       SUM(act.discount)                                           AS discount,
       SUM(act.paid)                                               AS paid,
       ROUND(SUM(act.purchase) - SUM(act.discount) - SUM(act.paid), 2) AS amount,
       MAX(act.account_date)                                       AS account_date,
       ta.id                                                       AS area_id,
       ta.area_name                                                AS area_name
FROM account_customer_table act
         JOIN custom     c  ON act.account_code = c.id
         JOIN table_area ta ON ta.id = c.area_id
GROUP BY act.account_code, c.name, ta.id, ta.area_name;

-- --------------------------------------account_suppliers_totals-----------------------------------

CREATE OR REPLACE VIEW account_suppliers_totals AS
SELECT ast.account_code,
       c.name,
       ROUND(SUM(ast.purchase), 2)                                       AS purchase,
       ROUND(SUM(ast.discount), 2)                                       AS discount,
       ROUND(SUM(ast.paid), 2)                                           AS paid,
       ROUND(SUM(ast.purchase) - SUM(ast.discount) - SUM(ast.paid), 2)   AS amount,
       MAX(ast.account_date)                                             AS account_date
FROM account_suppliers_table ast
         JOIN suppliers c ON ast.account_code = c.id
GROUP BY ast.account_code, c.name;

-- --------------------------------------earnings_reports-------------------------------------------

CREATE OR REPLACE VIEW earnings_reports AS
WITH computed_profit AS (SELECT ts.invoice_number,
                                SUM(snt.total_profit) AS profit
                         FROM sales_names_table snt
                                  JOIN total_sales ts ON snt.invoice_number = ts.invoice_number
                         GROUP BY ts.invoice_number),
     computed_profit_sales_return AS (SELECT ts.id,
                                             SUM(snt.total_profit) AS profit
                                      FROM sales_return_names_table snt
                                               JOIN total_sales_re ts ON snt.invoice_number = ts.id
                                      GROUP BY ts.id),
     sales_query AS (SELECT ts.invoice_number AS id,
                            ts.invoice_date,
                            ts.total,
                            ts.discount,
                            ts.paid_up,
                            ts.treasury_id,
                            ts.date_insert,
                            ts.user_id,
                            'sales'           AS table_name,
                            cp.profit
                     FROM total_sales ts
                              JOIN computed_profit cp ON cp.invoice_number = ts.invoice_number),
     buy_query AS (SELECT invoice_number AS id,
                          invoice_date,
                          total,
                          discount,
                          paid_up,
                          treasury_id,
                          date_insert,
                          user_id,
                          'buy'          AS table_name,
                          0              AS profit
                   FROM total_buy),
     sales_return_query AS (SELECT tsr.id,
                                   tsr.invoice_date,
                                   tsr.total,
                                   tsr.discount,
                                   tsr.paid_from_treasury AS paid_up,
                                   tsr.treasury_id,
                                   tsr.date_insert,
                                   tsr.user_id,
                                   'sales_re'             AS table_name,
                                   cpsr.profit
                            FROM total_sales_re tsr
                                     JOIN computed_profit_sales_return cpsr ON cpsr.id = tsr.id),
     buy_return_query AS (SELECT id,
                                 invoice_date,
                                 total,
                                 discount,
                                 paid_to_treasury AS paid_up,
                                 treasury_id,
                                 date_insert,
                                 user_id,
                                 'buy_re'         AS table_name,
                                 0                AS profit
                          FROM total_buy_re),
     expenses_query AS (SELECT id,
                               date        AS invoice_date,
                               amount      AS total,
                               0           AS discount,
                               0           AS paid_up,
                               treasury_id,
                               date_insert,
                               user_id,
                               'expenses'  AS table_name,
                               0           AS profit
                        FROM expenses_details),
     customer_account_query AS (SELECT account_num          AS id,
                                       account_date         AS invoice_date,
                                       paid                 AS total,
                                       0                    AS discount,
                                       0                    AS paid_up,
                                       treasury_id,
                                       created_at           AS date_insert,
                                       user_id,
                                       'customers_accounts' AS table_name,
                                       0                    AS profit
                                FROM customers_accounts),
     suppliers_accounts_query AS (SELECT account_num          AS id,
                                         account_date         AS invoice_date,
                                         paid                 AS total,
                                         0                    AS discount,
                                         0                    AS paid_up,
                                         treasury_id,
                                         date_insert,
                                         user_id,
                                         'suppliers_accounts' AS table_name,
                                         0                    AS profit
                                  FROM suppliers_accounts),
     treasury_deposit_query AS (SELECT id,
                                       date_inter  AS invoice_date,
                                       amount      AS total,
                                       0           AS discount,
                                       0           AS paid_up,
                                       treasury_id,
                                       date_insert,
                                       user_id,
                                       'deposit'   AS table_name,
                                       0           AS profit
                                FROM treasury_deposit_expenses
                                WHERE deposit_or_expenses = 1),
     treasury_expenses_query AS (SELECT id,
                                        date_inter         AS invoice_date,
                                        amount             AS total,
                                        0                  AS discount,
                                        0                  AS paid_up,
                                        treasury_id,
                                        date_insert,
                                        user_id,
                                        'deposit_expenses' AS table_name,
                                        0                  AS profit
                                 FROM treasury_deposit_expenses
                                 WHERE deposit_or_expenses = 2)
SELECT * FROM sales_query
UNION ALL
SELECT * FROM buy_query
UNION ALL
SELECT * FROM sales_return_query
UNION ALL
SELECT * FROM buy_return_query
UNION ALL
SELECT * FROM expenses_query
UNION ALL
SELECT * FROM customer_account_query
UNION ALL
SELECT * FROM suppliers_accounts_query
UNION ALL
SELECT * FROM treasury_deposit_query
UNION ALL
SELECT * FROM treasury_expenses_query;

CREATE OR REPLACE VIEW daily_dashboard_report AS
SELECT
    -- ==========================================
    -- 1. المبيعات (اليوم، أمس، الأسبوع، الشهر)
    -- ==========================================
    (SELECT COUNT(invoice_number) FROM total_sales WHERE invoice_date = CURDATE()) AS sales_count_today,
    COALESCE((SELECT SUM(total) FROM total_sales WHERE invoice_date = CURDATE()), 0) AS sales_total_today,
    COALESCE((SELECT SUM(total) FROM total_sales WHERE invoice_date = CURDATE() - INTERVAL 1 DAY), 0) AS sales_total_yesterday,
    COALESCE((SELECT SUM(total) FROM total_sales WHERE YEARWEEK(invoice_date, 1) = YEARWEEK(CURDATE(), 1)), 0) AS sales_total_week,
    COALESCE((SELECT SUM(total) FROM total_sales WHERE YEAR(invoice_date) = YEAR(CURDATE()) AND MONTH(invoice_date) = MONTH(CURDATE())), 0) AS sales_total_month,

    -- ==========================================
    -- 2. المشتريات (اليوم)
    -- ==========================================
    (SELECT COUNT(invoice_number) FROM total_buy WHERE invoice_date = CURDATE()) AS purchases_count_today,
    COALESCE((SELECT SUM(total) FROM total_buy WHERE invoice_date = CURDATE()), 0) AS purchases_total_today,

    -- ==========================================
    -- 3. مرتجعات المبيعات (اليوم)
    -- ==========================================
    (SELECT COUNT(id) FROM total_sales_re WHERE invoice_date = CURDATE()) AS sales_returns_count_today,
    COALESCE((SELECT SUM(total) FROM total_sales_re WHERE invoice_date = CURDATE()), 0) AS sales_returns_total_today,

    -- ==========================================
    -- 4. مرتجعات المشتريات (اليوم)
    -- ==========================================
    (SELECT COUNT(id) FROM total_buy_re WHERE invoice_date = CURDATE()) AS purchases_returns_count_today,
    COALESCE((SELECT SUM(total) FROM total_buy_re WHERE invoice_date = CURDATE()), 0) AS purchases_returns_total_today,

    -- ==========================================
    -- 5. المقبوضات (النقدية الداخلة للخزينة اليوم)
    -- ==========================================
    (
        COALESCE((SELECT SUM(paid_up) FROM total_sales WHERE invoice_date = CURDATE()), 0) +
        COALESCE((SELECT SUM(paid_to_treasury) FROM total_buy_re WHERE invoice_date = CURDATE()), 0) +
        COALESCE((SELECT SUM(amount) FROM treasury_deposit_expenses WHERE date_inter = CURDATE() AND deposit_or_expenses = 1), 0)
        ) AS total_receipts_today,

    -- ==========================================
    -- 6. المدفوعات والمصروفات (النقدية الخارجة اليوم)
    -- ==========================================
    (
        COALESCE((SELECT SUM(paid_up) FROM total_buy WHERE invoice_date = CURDATE()), 0) +
        COALESCE((SELECT SUM(paid_from_treasury) FROM total_sales_re WHERE invoice_date = CURDATE()), 0) +
        COALESCE((SELECT SUM(amount) FROM treasury_deposit_expenses WHERE date_inter = CURDATE() AND deposit_or_expenses = 2), 0) +
        COALESCE((SELECT SUM(amount) FROM expenses_details WHERE date = CURDATE()), 0)
        ) AS total_payments_and_expenses_today,

    -- ==========================================
    -- 7. الخصومات (إجمالي خصومات اليوم الممنوحة والمكتسبة)
    -- ==========================================
    (
        COALESCE((SELECT SUM(discount) FROM total_sales WHERE invoice_date = CURDATE()), 0) +
        COALESCE((SELECT SUM(discount) FROM total_buy WHERE invoice_date = CURDATE()), 0) +
        COALESCE((SELECT SUM(discount) FROM total_sales_re WHERE invoice_date = CURDATE()), 0) +
        COALESCE((SELECT SUM(discount) FROM total_buy_re WHERE invoice_date = CURDATE()), 0)
        ) AS total_discounts_today
;


CREATE OR REPLACE VIEW top_selling_items_current_month AS
SELECT
    i.nameItem AS item_name,
    SUM(s.quantity) AS total_quantity,
    CAST((SUM(s.total_sel_price) / SUM(s.quantity)) AS DECIMAL(14,2)) AS average_price
FROM sales s
         JOIN total_sales ts ON s.invoice_number = ts.invoice_number
         JOIN items i ON s.num = i.id
WHERE YEAR(ts.invoice_date) = YEAR(CURDATE())
  AND MONTH(ts.invoice_date) = MONTH(CURDATE())
GROUP BY i.id, i.nameItem
ORDER BY total_quantity DESC
LIMIT 10;

CREATE OR REPLACE VIEW view_monthly_sales AS
SELECT
    YEAR(invoice_date) AS sales_year,

    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 1 THEN total ELSE 0 END), 2) AS January,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 2 THEN total ELSE 0 END), 2) AS February,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 3 THEN total ELSE 0 END), 2) AS March,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 4 THEN total ELSE 0 END), 2) AS April,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 5 THEN total ELSE 0 END), 2) AS May,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 6 THEN total ELSE 0 END), 2) AS June,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 7 THEN total ELSE 0 END), 2) AS July,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 8 THEN total ELSE 0 END), 2) AS August,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 9 THEN total ELSE 0 END), 2) AS September,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 10 THEN total ELSE 0 END), 2) AS October,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 11 THEN total ELSE 0 END), 2) AS November,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 12 THEN total ELSE 0 END), 2) AS December,

    -- إجمالي مبيعات السنة بالكامل
    ROUND(SUM(total), 2) AS total_yearly_sales

FROM
    total_sales
GROUP BY
    YEAR(invoice_date);



CREATE OR REPLACE VIEW view_monthly_purchase AS
SELECT
    YEAR(invoice_date) AS sales_year,

    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 1 THEN total ELSE 0 END), 2) AS January,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 2 THEN total ELSE 0 END), 2) AS February,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 3 THEN total ELSE 0 END), 2) AS March,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 4 THEN total ELSE 0 END), 2) AS April,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 5 THEN total ELSE 0 END), 2) AS May,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 6 THEN total ELSE 0 END), 2) AS June,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 7 THEN total ELSE 0 END), 2) AS July,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 8 THEN total ELSE 0 END), 2) AS August,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 9 THEN total ELSE 0 END), 2) AS September,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 10 THEN total ELSE 0 END), 2) AS October,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 11 THEN total ELSE 0 END), 2) AS November,
    ROUND(SUM(CASE WHEN MONTH(invoice_date) = 12 THEN total ELSE 0 END), 2) AS December,

    -- إجمالي مبيعات السنة بالكامل
    ROUND(SUM(total), 2) AS total_yearly_sales

FROM
    total_buy
GROUP BY
    YEAR(invoice_date);


CREATE OR REPLACE VIEW view_customer_purchased_items AS
SELECT
    c.id AS customer_id,
    c.name AS customer_name,
    i.nameItem AS item_name,
    s.quantity,
    s.price AS selling_price,
    ts.invoice_date,
    ts.invoice_number
FROM custom c
         JOIN total_sales ts ON c.id = ts.sup_code
         JOIN sales s ON ts.invoice_number = s.invoice_number
         JOIN items i ON s.num = i.id;

CREATE OR REPLACE VIEW view_suppliers_sales_items AS
SELECT
    c.id AS customer_id,
    c.name AS customer_name,
    i.nameItem AS item_name,
    s.quantity,
    s.price AS selling_price,
    ts.invoice_date,
    ts.invoice_number
FROM suppliers c
         JOIN total_buy ts ON c.id = ts.sup_code
         JOIN purchase s ON ts.invoice_number = s.invoice_number
         JOIN items i ON s.num = i.id;


CREATE OR REPLACE VIEW view_yearly_monthly_report AS
SELECT
    t.action_year AS report_year,
    t.action_month AS report_month,

    ROUND(SUM(t.purchases), 2) AS purchases,
    ROUND(SUM(t.purchases_discount), 2) AS purchases_discount,

    ROUND(SUM(t.sales), 2) AS sales,
    ROUND(SUM(t.sales_discount), 2) AS sales_discount,

    ROUND(SUM(t.purchases_return), 2) AS purchases_return,
    ROUND(SUM(t.purchases_return_discount), 2) AS purchases_return_discount,

    ROUND(SUM(t.sales_return), 2) AS sales_return,
    ROUND(SUM(t.sales_return_discount), 2) AS sales_return_discount,

    ROUND(SUM(t.expenses), 2) AS expenses,

    -- Net Profit Calculation: (Sales - Sales_RE - Sales_Discount) - (Purchases - Purchases_RE - Purchases_Discount) - Expenses
    ROUND(
            (SUM(t.sales) - SUM(t.sales_return) - SUM(t.sales_discount)) -
            (SUM(t.purchases) - SUM(t.purchases_return) - SUM(t.purchases_discount)) -
            SUM(t.expenses),
            2) AS estimated_net_profit

FROM (
         -- 1. Sales
         SELECT
             YEAR(invoice_date) AS action_year, MONTH(invoice_date) AS action_month,
             0 AS purchases, 0 AS purchases_discount,
             total AS sales, discount AS sales_discount,
             0 AS purchases_return, 0 AS purchases_return_discount,
             0 AS sales_return, 0 AS sales_return_discount,
             0 AS expenses
         FROM total_sales

         UNION ALL

         -- 2. Sales Returns
         SELECT
             YEAR(invoice_date), MONTH(invoice_date),
             0, 0,
             0, 0,
             0, 0,
             total, discount,
             0
         FROM total_sales_re

         UNION ALL

         -- 3. Purchases
         SELECT
             YEAR(invoice_date), MONTH(invoice_date),
             total, discount,
             0, 0,
             0, 0,
             0, 0,
             0
         FROM total_buy

         UNION ALL

         -- 4. Purchases Returns
         SELECT
             YEAR(invoice_date), MONTH(invoice_date),
             0, 0,
             0, 0,
             total, discount,
             0, 0,
             0
         FROM total_buy_re

         UNION ALL

         -- 5. Expenses
         SELECT
             YEAR(date_insert), MONTH(date_insert),
             0, 0,
             0, 0,
             0, 0,
             0, 0,
             amount
         FROM treasury_transfers
     ) AS t

GROUP BY
    t.action_year,
    t.action_month
ORDER BY
    t.action_year DESC,
    t.action_month ASC;


CREATE OR REPLACE VIEW view_item_sales_rank AS
SELECT
    num AS item_id,
    nameItem AS item_name,
    YEAR(invoice_date) AS sales_year,
    MONTH(invoice_date) AS sales_month,
    SUM(quantity) AS total_qty,
    ROUND(SUM(total_sales), 2) AS total_amount,
    -- حساب صافي الربح من الصنف (المبيعات - التكلفة)
    ROUND(SUM(total_sales - (quantity * buy_price)), 2) AS total_profit
FROM sales_names_table
GROUP BY num, nameItem, YEAR(invoice_date), MONTH(invoice_date);


CREATE OR REPLACE VIEW view_customer_receivables AS
SELECT
    c.id AS customer_id,
    c.name AS customer_name,
    c.tel AS customer_phone,

    -- رصيد أول المدة (المديونية عند تسجيل العميل)
    ROUND(c.first_balance, 2) AS opening_balance,

    -- إجمالي المبالغ الآجلة (المتبقية) من فواتير المبيعات
    ROUND((SELECT IFNULL(SUM((ts.total - ts.discount) - ts.paid_up), 0)
           FROM total_sales ts
           WHERE ts.sup_code = c.id), 2) AS total_invoices_debt,

    -- إجمالي التحصيلات والمدفوعات من جدول حسابات العملاء
    ROUND((SELECT IFNULL(SUM(paid), 0)
           FROM customers_accounts ca
           WHERE ca.account_code = c.id), 2) AS total_payments,

    -- صافي المديونية النهائية
    ROUND((c.first_balance +
           (SELECT IFNULL(SUM((ts.total - ts.discount) - ts.paid_up), 0) FROM total_sales ts WHERE ts.sup_code = c.id) -
           (SELECT IFNULL(SUM(paid), 0) FROM customers_accounts ca WHERE ca.account_code = c.id)
              ), 2) AS final_balance

FROM custom c
-- إظهار العملاء الذين لديهم تعاملات مادية فقط (اختياري)
WHERE (c.first_balance <> 0 OR
       EXISTS (SELECT 1 FROM total_sales WHERE sup_code = c.id) OR
       EXISTS (SELECT 1 FROM customers_accounts WHERE account_code = c.id));