DROP DATABASE IF EXISTS account_system_db;
CREATE DATABASE account_system_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE account_system_db;

# for %S in (*.sql) do mysql -u root --default-character-set=utf8mb4 --password=m13ido account_system_db < %S
# docker bash
# for sql_file in *.sql; do mysql -u root --default-character-set=utf8mb4 --password=m13ido account_system_db < $sql_file ; done
# add user hamza
/*CREATE USER 'hamza'@'%' IDENTIFIED BY 'm13ido';
GRANT ALL PRIVILEGES ON *.* TO 'hamza'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;*/

-- =====================================================================
-- Lookup & base tables
-- =====================================================================

create table if not exists company
(
    comp_id      int auto_increment primary key,
    comp_name    varchar(50)                         not null,
    comp_tel     varchar(50)                         null,
    comp_address varchar(100)                        null,
    comp_tax     varchar(100)                        null,
    comp_comm    varchar(50)                         null,
    comp_image   longblob                            null,
    updated_at   timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
);

create table if not exists expenses
(
    id            int         not null primary key,
    expenses_name varchar(50) not null,
    constraint expenses_pk unique (expenses_name)
);

create table if not exists jobs
(
    id       int         not null primary key,
    job_name varchar(20) not null,
    constraint jobs_pk_2 unique (job_name)
);

create table if not exists permission
(
    id              int auto_increment primary key,
    name_permission varchar(50) not null,
    description     varchar(50) null,
    constraint users_permission_pk unique (name_permission)
);

create table if not exists table_area
(
    id        int auto_increment primary key,
    area_name varchar(100) not null,
    constraint table_area_pk_2 unique (area_name)
);

create table if not exists users
(
    id             int auto_increment primary key,
    user_name      varchar(30)                         null,
    user_pass      varchar(255)                        null,
    user_activity  tinyint   default 1                 not null,
    user_available tinyint   default 0                 not null,
    updated_at     timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint users_pk unique (user_name),
    constraint users_activity_chk check (user_activity in (0, 1)),
    constraint users_available_chk check (user_available in (0, 1))
);

create table if not exists employees
(
    id          int auto_increment primary key,
    column_name varchar(50)                         not null,
    birth_date  date                                not null,
    hire_date   date                                not null,
    salary      decimal(14, 2)                      not null,
    email       varchar(200)                        null,
    tel         varchar(200)                        null,
    address     varchar(200)                        null,
    image       longblob                            null,
    job         int                                 not null,
    date_insert datetime  default CURRENT_TIMESTAMP not null,
    updated_at  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id     int       default 1                 not null,
    constraint employees_pk2 unique (column_name),
    constraint employees_jobs_id_fk foreign key (job) references jobs (id),
    constraint employees_users_id_fk foreign key (user_id) references users (id)
);

create table if not exists main_group
(
    id          int auto_increment primary key,
    name_g      varchar(50)                         not null,
    date_insert datetime  default CURRENT_TIMESTAMP not null,
    updated_at  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id     int       default 1                 not null,
    constraint main_group_pk unique (name_g),
    constraint main_group_users_id_fk foreign key (user_id) references users (id)
);

create table if not exists processes_data
(
    id             int auto_increment primary key,
    user_id        int                                 not null,
    processes_name varchar(50)                         not null,
    table_name     varchar(50)                         not null,
    table_id       int                                 not null,
    date_insert    datetime  default CURRENT_TIMESTAMP not null,
    updated_at     timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    notes          longtext                            null,
    constraint processes_data_users_id_fk foreign key (user_id) references users (id)
        on update cascade on delete cascade
);
create index processes_data_table_idx on processes_data (table_name, table_id);
create index processes_data_date_idx on processes_data (date_insert);

create table if not exists stocks
(
    stock_id      int auto_increment primary key,
    stock_name    varchar(50)                         not null,
    stock_address varchar(50)                         null,
    date_insert   datetime  default CURRENT_TIMESTAMP not null,
    updated_at    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id       int       default 1                 not null,
    constraint stocks_pk unique (stock_name),
    constraint stocks_users_id_fk foreign key (user_id) references users (id)
);

create table if not exists stock_transfer
(
    id            int auto_increment primary key,
    transfer_date date                                not null,
    stock_from    int                                 not null,
    stock_to      int                                 not null,
    date_insert   datetime  default CURRENT_TIMESTAMP not null,
    updated_at    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id       int       default 1                 not null,
    constraint stock_transfer_users_id_fk foreign key (user_id) references users (id),
    constraint stock_transfer_from_fk foreign key (stock_from) references stocks (stock_id),
    constraint stock_transfer_to_fk foreign key (stock_to) references stocks (stock_id),
    constraint stock_transfer_not_same_chk check (stock_from <> stock_to)
);
create index stock_transfer_stocks_stock_id_fk on stock_transfer (stock_from);
create index stock_transfer_stocks_stock_id_fk_2 on stock_transfer (stock_to);
create index stock_transfer_date_idx on stock_transfer (transfer_date);

create table if not exists stock_transfer_list
(
    id                int auto_increment primary key,
    stock_transfer_id int            not null,
    item_id           int            not null,
    quantity          decimal(14, 3) not null,
    constraint stock_transfer_list_stock_transfer_id_fk
        foreign key (stock_transfer_id) references stock_transfer (id)
            on update cascade on delete cascade
);
create index stock_transfer_list_item_idx on stock_transfer_list (item_id);

create table if not exists sub_group
(
    id          int auto_increment primary key,
    name        varchar(50)                         not null,
    main_id     int                                 not null,
    date_insert datetime  default CURRENT_TIMESTAMP not null,
    updated_at  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id     int       default 1                 not null,
    constraint sub_group_pk unique (name),
    constraint sub_group_main_group_id_fk foreign key (main_id) references main_group (id),
    constraint sub_group_users_id_fk foreign key (user_id) references users (id)
);

create table if not exists suppliers
(
    id            int auto_increment primary key,
    name          varchar(50)                              not null,
    tel           varchar(50)                              null,
    address       varchar(255)                             null,
    notes         longtext                                 null,
    first_balance decimal(14, 2) default 0                 not null,
    table_id      int            default 1                 not null,
    date_insert   datetime       default CURRENT_TIMESTAMP not null,
    updated_at    timestamp      default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id       int            default 1                 not null,
    area_id       int            default 1                 not null,
    constraint suppliers_pk unique (name),
    constraint suppliers_table_area_id_fk foreign key (area_id) references table_area (id),
    constraint suppliers_users_id_fk foreign key (user_id) references users (id)
);

create table if not exists targeted_sales
(
    id            int auto_increment primary key,
    delegate_id   int                                     not null,
    target        decimal(14, 2)                          not null,
    target_ratio1 decimal(6, 2) default 100               not null,
    rate_1        decimal(6, 2) default 0                 not null,
    target_ratio2 decimal(6, 2) default 0                 not null,
    rate_2        decimal(6, 2) default 0                 not null,
    target_ratio3 decimal(6, 2) default 0                 not null,
    rate_3        decimal(6, 2) default 0                 not null,
    notes         varchar(200)                            null,
    date_insert   datetime      default CURRENT_TIMESTAMP not null,
    updated_at    timestamp     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id       int           default 1                 not null,
    constraint targeted_sales_employees_id_fk foreign key (delegate_id) references employees (id)
        on update cascade on delete cascade,
    constraint targeted_sales_users_id_fk foreign key (user_id) references users (id)
);

create table if not exists treasury
(
    id          int auto_increment primary key,
    t_name      varchar(50)                              not null,
    amount      decimal(14, 2) default 0                 not null,
    date_insert datetime       default CURRENT_TIMESTAMP not null,
    updated_at  timestamp      default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id     int            default 1                 not null,
    constraint treasury_pk unique (t_name),
    constraint treasury_users_id_fk foreign key (user_id) references users (id)
);

-- =====================================================================
-- Expenses
-- =====================================================================

create table if not exists expenses_details
(
    id          int auto_increment primary key,
    type_code   int                                      not null,
    date        date                                     not null,
    amount      decimal(14, 2) default 0                 not null,
    notes       varchar(255)                             null,
    emp_id      int            default 0                 not null,
    treasury_id int            default 1                 not null,
    date_insert datetime       default CURRENT_TIMESTAMP not null,
    updated_at  timestamp      default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id     int            default 1                 not null,
    constraint expenses_details_expenses_id_fk foreign key (type_code) references expenses (id),
    constraint expenses_details_treasury_id_fk foreign key (treasury_id) references treasury (id),
    constraint expenses_details_users_id_fk foreign key (user_id) references users (id)
);
create index expenses_details_date_idx on expenses_details (date);
create index expenses_details_treasury_idx on expenses_details (treasury_id, date);

create table if not exists expense_salary
(
    employee_id         int not null,
    expenses_details_id int not null,
    constraint expense_salary_employees_id_fk foreign key (employee_id) references employees (id),
    constraint expense_salary_expenses_details_id_fk foreign key (expenses_details_id) references expenses_details (id)
        on update cascade on delete cascade
);

-- =====================================================================
-- Suppliers accounts & Purchase invoices
-- =====================================================================

create table if not exists suppliers_accounts
(
    account_num           bigint auto_increment primary key,
    account_code          int                                      not null,
    account_date          date                                     not null,
    purchase              decimal(14, 2) default 0                 not null,
    paid                  decimal(14, 2)                           not null,
    numberInv             bigint                                   not null,
    notes                 longtext                                 null,
    treasury_id           int            default 1                 not null,
    table_id              int            default 2                 not null,
    date_insert           datetime       default CURRENT_TIMESTAMP not null,
    updated_at            timestamp      default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id               int            default 1                 not null,
    invoice_number_return bigint         default 0                 not null comment 'This column for number invoice for returns',
    constraint suppliers_accounts_suppliers_id_fk foreign key (account_code) references suppliers (id),
    constraint suppliers_accounts_treasury_id_fk foreign key (treasury_id) references treasury (id),
    constraint suppliers_accounts_users_id_fk foreign key (user_id) references users (id)
);
create index suppliers_accounts_numberInv_idx on suppliers_accounts (numberInv);
create index suppliers_accounts_date_idx on suppliers_accounts (account_date);

create table if not exists total_buy
(
    invoice_number bigint                              not null primary key,
    sup_code       int                                 not null,
    invoice_type   tinyint   default 1                 not null,
    invoice_date   date                                not null,
    total          decimal(14, 2)                      not null,
    discount       decimal(14, 2)                      not null,
    paid_up        decimal(14, 2)                      not null comment 'paid from the treasury مدفوع نقدا من الخزينة',
    stock_id       int       default 1                 not null,
    treasury_id    int       default 1                 not null,
    notes          longtext                            null,
    table_id       int       default 3                 not null,
    date_insert    datetime  default CURRENT_TIMESTAMP not null,
    updated_at     timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id        int       default 1                 not null,
    constraint total_buy_stocks_stock_id_fk foreign key (stock_id) references stocks (stock_id),
    constraint total_buy_suppliers_sup_id_fk foreign key (sup_code) references suppliers (id),
    constraint total_buy_treasury_id_fk foreign key (treasury_id) references treasury (id),
    constraint total_buy_users_id_fk foreign key (user_id) references users (id),
    constraint total_buy_invoice_type_chk check (invoice_type in (1, 2))
);
create index total_buy_sup_code_fk on total_buy (sup_code);
create index total_buy_date_idx on total_buy (invoice_date);
create index total_buy_treasury_idx on total_buy (treasury_id, invoice_date);

create table if not exists total_buy_re
(
    id               bigint                              not null primary key,
    sup_id           int                                 not null,
    invoice_date     date                                not null,
    invoice_type     tinyint   default 1                 not null,
    total            decimal(14, 2)                      not null,
    discount         decimal(14, 2)                      not null,
    paid_to_treasury decimal(14, 2)                      not null comment 'Paid to the treasury مدفوعات الى الخزينة',
    stock_id         int                                 not null,
    treasury_id      int       default 1                 not null,
    notes            longtext                            null,
    table_id         int       default 4                 not null,
    date_insert      datetime  default CURRENT_TIMESTAMP not null,
    updated_at       timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id          int       default 1                 not null,
    constraint total_buy_re_stocks_stock_id_fk foreign key (stock_id) references stocks (stock_id),
    constraint total_buy_re_suppliers_sup_id_fk foreign key (sup_id) references suppliers (id),
    constraint total_buy_re_treasury_id_fk foreign key (treasury_id) references treasury (id),
    constraint total_buy_re_users_id_fk foreign key (user_id) references users (id),
    constraint total_buy_re_invoice_type_chk check (invoice_type in (1, 2))
);
create index total_buy_re_date_idx on total_buy_re (invoice_date);
create index total_buy_re_treasury_idx on total_buy_re (treasury_id, invoice_date);
create index total_buy_re_sup_idx on total_buy_re (sup_id);

create table if not exists treasury_deposit_expenses
(
    id                  int auto_increment primary key,
    statement           varchar(50)                         not null,
    date_inter          date                                not null,
    amount              decimal(14, 2)                      not null,
    description_data    text                                null,
    deposit_or_expenses tinyint   default 1                 not null,
    treasury_id         int       default 1                 not null,
    date_insert         datetime  default CURRENT_TIMESTAMP not null,
    updated_at          timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id             int       default 1                 not null,
    constraint treasury_deposit_expenses_treasury_id_fk foreign key (treasury_id) references treasury (id),
    constraint treasury_deposit_expenses_users_id_fk foreign key (user_id) references users (id),
    constraint treasury_deposit_expenses_type_chk check (deposit_or_expenses in (1, 2))
);
create index treasury_deposit_expenses_date_idx on treasury_deposit_expenses (date_inter);
create index treasury_deposit_expenses_treasury_idx on treasury_deposit_expenses (treasury_id, date_inter);

create table if not exists treasury_transfers
(
    id            int auto_increment primary key,
    treasury_from int                                 not null,
    treasury_to   int                                 not null,
    amount        decimal(14, 2)                      not null,
    transfer_date date                                not null,
    notes         longtext                            null,
    date_insert   datetime  default CURRENT_TIMESTAMP not null,
    updated_at    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id       int       default 1                 not null,
    constraint treasury_transfers_treasury_id_fk foreign key (treasury_from) references treasury (id),
    constraint treasury_transfers_treasury_id_fk_2 foreign key (treasury_to) references treasury (id),
    constraint treasury_transfers_users_id_fk foreign key (user_id) references users (id),
    constraint treasury_transfers_not_same_chk check (treasury_from <> treasury_to)
);
create index treasury_transfers_date_idx on treasury_transfers (transfer_date);

-- =====================================================================
-- Customers & Sales
-- =====================================================================

create table if not exists type_price
(
    id          int auto_increment primary key,
    name        varchar(50)                         not null,
    date_insert datetime  default CURRENT_TIMESTAMP not null,
    updated_at  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id     int       default 1                 not null,
    constraint items_price_pk unique (name),
    constraint type_price_users_id_fk foreign key (user_id) references users (id)
);

create table if not exists custom
(
    id            int auto_increment primary key,
    name          varchar(100)                             not null,
    tel           varchar(50)                              null,
    address       varchar(200)                             null,
    notes         longtext                                 null,
    limit_num     decimal(14, 2)                           not null,
    first_balance decimal(14, 2) default 0                 not null,
    price_id      int                                      not null,
    table_id      int            default 1                 not null,
    created_at    timestamp      default CURRENT_TIMESTAMP not null,
    updated_at    timestamp      default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id       int            default 1                 not null,
    area_id       int            default 1                 not null,
    constraint custom_pk unique (name),
    constraint custom_items_price_id_fk foreign key (price_id) references type_price (id),
    constraint custom_table_area_id_fk foreign key (area_id) references table_area (id),
    constraint custom_users_id_fk foreign key (user_id) references users (id)
);

create table if not exists customers_accounts
(
    account_num           bigint auto_increment primary key,
    account_code          int                                      not null,
    account_date          date                                     not null,
    paid                  decimal(14, 2)                           not null,
    notes                 longtext                                 null,
    treasury_id           int            default 1                 not null,
    purchase              decimal(14, 2) default 0                 not null,
    numberInv             bigint                                   not null,
    table_id              int            default 2                 not null,
    created_at            timestamp      default CURRENT_TIMESTAMP not null,
    updated_at            timestamp      default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id               int            default 1                 not null,
    invoice_number_return bigint         default 0                 not null comment 'This column for number invoice for returns',
    constraint customers_accounts_custom_id_fk foreign key (account_code) references custom (id),
    constraint customers_accounts_treasury_id_fk foreign key (treasury_id) references treasury (id),
    constraint customers_accounts_users_id_fk foreign key (user_id) references users (id)
);
create index customers_accounts_numberInv_idx on customers_accounts (numberInv);
create index customers_accounts_date_idx on customers_accounts (account_date);

create table if not exists total_sales
(
    invoice_number bigint                              not null primary key,
    sup_code       int                                 not null,
    invoice_type   tinyint   default 1                 not null,
    invoice_date   date                                not null,
    total          decimal(14, 2)                      not null,
    discount       decimal(14, 2)                      not null,
    paid_up        decimal(14, 2)                      not null comment 'paid to the treasury مدفوع نقدا الى الخزينة',
    stock_id       int       default 1                 not null,
    delegate_id    int                                 not null,
    treasury_id    int       default 1                 not null,
    notes          longtext                            null,
    table_id       int       default 3                 not null,
    date_insert    datetime  default CURRENT_TIMESTAMP not null,
    updated_at     timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id        int       default 1                 not null,
    constraint total_sales_custom_sup_id_fk foreign key (sup_code) references custom (id),
    constraint total_sales_employees_id_fk foreign key (delegate_id) references employees (id),
    constraint total_sales_stocks_stock_id_fk foreign key (stock_id) references stocks (stock_id),
    constraint total_sales_treasury_id_fk foreign key (treasury_id) references treasury (id),
    constraint total_sales_users_id_fk foreign key (user_id) references users (id),
    constraint total_sales_invoice_type_chk check (invoice_type in (1, 2))
);
create index total_sales_sup_code_fk on total_sales (sup_code);
create index total_sales_users_id_fk2 on total_sales (delegate_id);
create index total_sales_date_idx on total_sales (invoice_date);
create index total_sales_treasury_idx on total_sales (treasury_id, invoice_date);

create table if not exists total_sales_re
(
    id                 bigint                              not null primary key,
    sup_id             int                                 not null,
    invoice_date       date                                not null,
    invoice_type       tinyint   default 1                 not null,
    total              decimal(14, 2)                      not null,
    discount           decimal(14, 2)                      not null,
    paid_from_treasury decimal(14, 2)                      not null comment 'paid from the treasury مدفوع نقدا من الخزينة',
    stock_id           int       default 1                 not null,
    delegate_id        int                                 not null,
    treasury_id        int       default 1                 not null,
    notes              longtext                            null,
    table_id           int       default 4                 not null,
    date_insert        datetime  default CURRENT_TIMESTAMP not null,
    updated_at         timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id            int       default 1                 not null,
    constraint total_sales_re_custom_id_fk foreign key (sup_id) references custom (id),
    constraint total_sales_re_employees_id_fk foreign key (delegate_id) references employees (id),
    constraint total_sales_re_stocks_stock_id_fk foreign key (stock_id) references stocks (stock_id),
    constraint total_sales_re_treasury_id_fk foreign key (treasury_id) references treasury (id),
    constraint total_sales_re_users_id_fk foreign key (user_id) references users (id),
    constraint total_sales_re_invoice_type_chk check (invoice_type in (1, 2))
);
create index total_sales_re_date_idx on total_sales_re (invoice_date);
create index total_sales_re_treasury_idx on total_sales_re (treasury_id, invoice_date);
create index total_sales_re_sup_idx on total_sales_re (sup_id);
create index total_sales_re_delegate_idx on total_sales_re (delegate_id);

-- =====================================================================
-- Items
-- =====================================================================

create table if not exists units
(
    unit_id     int auto_increment primary key,
    unit_name   varchar(50)                              not null,
    value_d     decimal(14, 3) default 1                 not null,
    date_insert datetime       default CURRENT_TIMESTAMP not null,
    updated_at  timestamp      default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id     int            default 1                 not null,
    constraint units_pk unique (unit_name),
    constraint units_users_id_fk foreign key (user_id) references users (id)
);

create table if not exists items
(
    id                       int auto_increment primary key,
    barcode                  varchar(200)                             not null,
    nameItem                 varchar(200)                             not null,
    sub_num                  int                                      not null,
    buy_price                decimal(14, 2) default 0                 not null,
    sel_price1               decimal(14, 2) default 0                 not null,
    sel_price2               decimal(14, 2) default 0                 not null,
    sel_price3               decimal(14, 2) default 0                 not null,
    unit_id                  int                                      not null,
    mini_quantity            decimal(14, 3) default 1                 not null,
    first_balance            decimal(14, 3) default 0                 not null,
    item_image               longblob                                 null,
    item_active              tinyint(1)     default 1                 not null,
    item_has_validity        tinyint(1)     default 0                 not null,
    number_validity_days     int            default 0                 not null,
    alert_days_before_expire int            default 0                 not null,
    item_has_package         tinyint(1)     default 0                 not null,
    created_at               timestamp      default CURRENT_TIMESTAMP not null,
    updated_at               timestamp      default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id                  int            default 1                 not null,
    constraint items_barcode_uindex unique (barcode),
    constraint items_pk unique (nameItem),
    constraint items_sub_group_id_fk foreign key (sub_num) references sub_group (id),
    constraint items_units_unit_id_fk foreign key (unit_id) references units (unit_id),
    constraint items_users_id_fk foreign key (user_id) references users (id)
);

create table if not exists items_package
(
    id         int auto_increment primary key,
    item_id    int                                not null,
    package_id int                                not null,
    quantity   decimal(14, 3)                     not null,
    created_at datetime default CURRENT_TIMESTAMP null,
    updated_at datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint items_package_items_id_fk foreign key (package_id) references items (id)
        on update cascade on delete cascade
);
create index items_package_item_idx on items_package (item_id);

create table if not exists items_stock
(
    id            int auto_increment primary key,
    item_id       int                      not null,
    stock_id      int                      not null,
    first_balance decimal(14, 3) default 0 not null,
    constraint items_stock_items_id_fk foreign key (item_id) references items (id)
        on update cascade on delete cascade,
    constraint items_stock_stocks_stock_id_fk foreign key (stock_id) references stocks (stock_id),
    constraint items_stock_uk unique (item_id, stock_id)
);

create table if not exists items_units
(
    id            int auto_increment primary key,
    items_id      int                                 not null,
    items_barcode varchar(50)                         not null,
    unit          int                                 not null,
    quantity      decimal(14, 3)                      not null,
    buy_price     decimal(14, 2)                      not null,
    sel_price     decimal(14, 2)                      not null,
    date_insert   datetime  default CURRENT_TIMESTAMP not null,
    updated_at    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id       int       default 1                 not null,
    constraint items_units_pk unique (items_barcode),
    constraint items_units_items_id_fk foreign key (items_id) references items (id)
        on update cascade on delete cascade,
    constraint items_units_units_unit_id_fk foreign key (unit) references units (unit_id)
);
create index items_units_items_num_fk on items_units (items_id);

-- =====================================================================
-- Invoice lines
-- =====================================================================

create table if not exists purchase
(
    id              int auto_increment primary key,
    invoice_number  bigint                   not null,
    num             int                      not null,
    type            int            default 1 not null,
    quantity        decimal(14, 3)           not null,
    price           decimal(14, 2)           not null,
    discount        decimal(14, 2) default 0 not null,
    type_value      decimal(14, 3) default 1 not null,
    expiration_date date                     null,
    constraint purchase_items_id_fk foreign key (num) references items (id),
    constraint purchase_total_buy_invoice_number_fk foreign key (invoice_number) references total_buy (invoice_number)
        on update cascade on delete cascade,
    constraint purchase_units_unit_id_fk foreign key (type) references units (unit_id)
);
create index purchase_item_idx on purchase (num);

create table if not exists purchase_re
(
    id              int auto_increment primary key,
    invoice_number  bigint                   not null,
    item_id         int                      not null,
    type            int            default 1 not null,
    quantity        decimal(14, 3)           not null,
    price           decimal(14, 2)           not null,
    discount        decimal(14, 2) default 0 not null,
    type_value      decimal(14, 3) default 1 not null,
    expiration_date date                     null,
    constraint purchase_re_items_id_fk foreign key (item_id) references items (id),
    constraint purchase_re_total_buy_re_id_fk foreign key (invoice_number) references total_buy_re (id)
        on update cascade on delete cascade,
    constraint purchase_re_units_unit_id_fk foreign key (type) references units (unit_id)
);
create index purchase_re_item_idx on purchase_re (item_id);

create table if not exists sales
(
    id               int auto_increment primary key,
    invoice_number   bigint                   not null,
    num              int                      not null,
    type             int            default 1 not null,
    quantity         decimal(14, 3)           not null,
    price            decimal(14, 2)           not null,
    buy_price        decimal(14, 2)           not null,
    total_sel_price  decimal(14, 2) default 0 not null,
    total_buy_price  decimal(14, 2) default 0 not null,
    total_profit     decimal(14, 2) default 0 not null,
    discount         decimal(14, 2) default 0 not null,
    type_value       decimal(14, 3) default 1 not null,
    expiration_date  date                     null,
    item_has_package tinyint(1)     default 0 not null,
    constraint sales_items_id_fk foreign key (num) references items (id),
    constraint sales_total_invoice_number_fk foreign key (invoice_number) references total_sales (invoice_number)
        on update cascade on delete cascade,
    constraint sales_units_unit_id_fk foreign key (type) references units (unit_id)
);
create index sales_item_idx on sales (num);

create table if not exists sales_package
(
    id              int auto_increment primary key,
    sales_id        int                      not null,
    item_id         int                      not null,
    unit_id         int            default 1 not null,
    quantity        decimal(14, 3)           not null,
    price           decimal(14, 2)           not null,
    buy_price       decimal(14, 2)           not null,
    total_sel_price decimal(14, 2) default 0 not null,
    total_buy_price decimal(14, 2) default 0 not null,
    total_profit    decimal(14, 2) default 0 not null,
    discount        decimal(14, 2) default 0 not null,
    unit_value      decimal(14, 3) default 1 not null,
    expiration_date date                     null,
    constraint sales_package_items_id_fk foreign key (item_id) references items (id),
    constraint sales_package_sales_id_fk foreign key (sales_id) references sales (id)
        on update cascade on delete cascade,
    constraint sales_package_units_unit_id_fk foreign key (unit_id) references units (unit_id)
);
create index sales_package_item_idx on sales_package (item_id);
create index sales_package_sales_idx on sales_package (sales_id);
create index sales_package_unit_idx on sales_package (unit_id);

create table if not exists sales_re
(
    id              int auto_increment primary key,
    invoice_number  bigint                   not null,
    item_id         int                      not null,
    type            int            default 1 not null,
    quantity        decimal(14, 3)           not null,
    price           decimal(14, 2)           not null,
    buy_price       decimal(14, 2) default 0 not null,
    total_sel_price decimal(14, 2) default 0 not null,
    total_buy_price decimal(14, 2) default 0 not null,
    total_profit    decimal(14, 2) default 0 not null,
    discount        decimal(14, 2) default 0 not null,
    type_value      decimal(14, 3) default 1 not null,
    expiration_date date                     null,
    constraint sales_re_items_id_fk foreign key (item_id) references items (id),
    constraint sales_re_total_sales_re_id_fk foreign key (invoice_number) references total_sales_re (id)
        on update cascade on delete cascade,
    constraint sales_re_units_unit_id_fk foreign key (type) references units (unit_id)
);
create index sales_re_item_idx on sales_re (item_id);

-- =====================================================================
-- Users / Permissions / Shifts
-- =====================================================================

create table if not exists user_permission
(
    id            int auto_increment primary key,
    permission_id int                                 not null,
    user_id       int                                 not null,
    check_status  tinyint   default 0                 not null,
    updated_at    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint user_permission_permission_id_fk foreign key (permission_id) references permission (id)
        on update cascade on delete cascade,
    constraint user_permission_users_id_fk foreign key (user_id) references users (id)
        on update cascade on delete cascade,
    constraint user_permission_uk unique (permission_id, user_id),
    constraint user_permission_chk check (check_status in (0, 1))
);

CREATE TABLE user_shifts
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
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_shifts_user_open ON user_shifts (user_id, is_open);
CREATE INDEX idx_user_shifts_open_time ON user_shifts (open_time);