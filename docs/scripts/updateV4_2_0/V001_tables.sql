DROP DATABASE IF EXISTS account_system_db;
CREATE DATABASE account_system_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE account_system_db;

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
    CONSTRAINT items_stock_uk UNIQUE (item_id, stock_id),
    CONSTRAINT items_stock_first_balance_chk CHECK (first_balance >= 0),
    CONSTRAINT items_stock_current_quantity_chk CHECK (current_quantity >= 0)
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
    id                   INT AUTO_INCREMENT PRIMARY KEY,
    transfer_date        DATE                                NOT NULL,
    stock_from           INT                                 NOT NULL,
    stock_to             INT                                 NOT NULL,
    status               VARCHAR(20) DEFAULT 'POSTED'        NOT NULL,
    cancelled_at         DATETIME                            NULL,
    cancelled_by         INT                                 NULL,
    cancel_reason        TEXT                                NULL,
    reversal_transfer_id INT                                 NULL,
    date_insert          DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP,
    user_id              INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT stock_transfer_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT stock_transfer_from_fk FOREIGN KEY (stock_from) REFERENCES stocks (stock_id),
    CONSTRAINT stock_transfer_to_fk FOREIGN KEY (stock_to) REFERENCES stocks (stock_id),
    CONSTRAINT stock_transfer_cancelled_by_fk FOREIGN KEY (cancelled_by) REFERENCES users (id),
    CONSTRAINT stock_transfer_reversal_fk FOREIGN KEY (reversal_transfer_id) REFERENCES stock_transfer (id),
    CONSTRAINT stock_transfer_not_same_chk CHECK (stock_from <> stock_to),
    CONSTRAINT stock_transfer_status_chk CHECK (status IN ('POSTED', 'CANCELLED'))
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
            ON UPDATE CASCADE ON DELETE RESTRICT,

    CONSTRAINT stock_transfer_list_items_id_fk
        FOREIGN KEY (item_id) REFERENCES items (id),

    CONSTRAINT stock_transfer_list_quantity_chk
        CHECK (quantity > 0),

    CONSTRAINT stock_transfer_list_uk
        UNIQUE (stock_transfer_id, item_id)
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

CREATE TABLE database_migrations (
                                     id INT AUTO_INCREMENT PRIMARY KEY,
                                     version VARCHAR(50) NOT NULL,
                                     description VARCHAR(255),
                                     executed_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_info (
    id,
    client_code,
    client_name,
    app_version,
    database_version,
    install_date,
    last_update,
    notes
)
SELECT
    1,
    'CLIENT-001',
    'Default Client',
    '4.1.0',
    '4.1.0',
    NOW(),
    NOW(),
    'Initial system info'
WHERE NOT EXISTS (
    SELECT 1 FROM system_info WHERE id = 1
);