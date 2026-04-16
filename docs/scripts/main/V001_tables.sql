DROP DATABASE IF EXISTS account_system_db;
CREATE DATABASE account_system_db;
USE account_system_db;
# for %S in (*.sql) do mysql -u root --default-character-set=utf8mb4 --password=m13ido account_system_db < %S
# docker bash
# for sql_file in *.sql; do mysql -u root --default-character-set=utf8mb4 --password=m13ido account_system_db < $sql_file ; done
# add user hamza
/*CREATE USER 'hamza'@'%' IDENTIFIED BY 'm13ido';
GRANT ALL PRIVILEGES ON *.* TO 'hamza'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;*/

create table if not exists company
(
    comp_id      int auto_increment
        primary key,
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
    id            int         not null
        primary key,
    expenses_name varchar(50) not null,
    constraint expenses_pk
        unique (expenses_name)
);

create table if not exists jobs
(
    id       int         not null
        primary key,
    job_name varchar(20) not null,
    constraint jobs_pk_2
        unique (job_name)
);

create table if not exists permission
(
    id              int auto_increment
        primary key,
    name_permission varchar(50) not null,
    description     varchar(50) null,
    constraint users_permission_pk
        unique (name_permission)
);

create table if not exists table_area
(
    id        int auto_increment
        primary key,
    area_name varchar(100) not null,
    constraint table_area_pk_2
        unique (area_name)
);

create table if not exists users
(
    id             int auto_increment
        primary key,
    user_name      varchar(30)                         null,
    user_pass      varchar(50)                         null,
    user_activity  int       default 1                 not null,
    user_available int       default 0                 not null,
    updated_at     timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint users_pk
        unique (user_name)
);

create table if not exists employees
(
    id          int auto_increment
        primary key,
    column_name varchar(50)                         not null,
    birth_date  date                                not null,
    hire_date   date                                not null,
    salary      double                              not null,
    email       varchar(200)                        null,
    tel         varchar(200)                        null,
    address     varchar(200)                        null,
    image       longblob                            null,
    job         int                                 not null,
    date_insert datetime  default CURRENT_TIMESTAMP not null,
    updated_at  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id     int       default 1                 not null,
    constraint employees_pk2
        unique (column_name),
    constraint employees_jobs_id_fk
        foreign key (job) references jobs (id),
    constraint employees_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists main_group
(
    id          int auto_increment
        primary key,
    name_g      varchar(50)                         not null,
    date_insert datetime  default CURRENT_TIMESTAMP not null,
    updated_at  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id     int       default 1                 not null,
    constraint main_group_pk
        unique (name_g),
    constraint main_group_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists processes_data
(
    id             int auto_increment
        primary key,
    user_id        int                                 not null,
    processes_name varchar(50)                         not null,
    table_name     varchar(50)                         not null,
    table_id       int                                 not null,
    date_insert    datetime  default CURRENT_TIMESTAMP not null,
    updated_at     timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    notes          longtext                            null,
    constraint processes_data_users_id_fk
        foreign key (user_id) references users (id)
            on update cascade on delete cascade
);

create table if not exists stock_transfer
(
    id            int auto_increment
        primary key,
    transfer_date date                                not null,
    stock_from    int                                 not null,
    stock_to      int                                 not null,
    date_insert   datetime  default CURRENT_TIMESTAMP not null,
    updated_at    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id       int       default 1                 not null,
    constraint stock_transfer_users_id_fk
        foreign key (user_id) references users (id)
);

create index stock_transfer_stocks_stock_id_fk
    on stock_transfer (stock_from);

create index stock_transfer_stocks_stock_id_fk_2
    on stock_transfer (stock_to);

create table if not exists stock_transfer_list
(
    id                int auto_increment
        primary key,
    stock_transfer_id int    not null,
    item_id           int    not null,
    quantity          double not null,
    constraint stock_transfer_list_stock_transfer_id_fk
        foreign key (stock_transfer_id) references stock_transfer (id)
            on update cascade on delete cascade
);

create table if not exists stocks
(
    stock_id      int auto_increment
        primary key,
    stock_name    varchar(50)                         not null,
    stock_address varchar(50)                         null,
    date_insert   datetime  default CURRENT_TIMESTAMP not null,
    updated_at    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id       int       default 1                 not null,
    constraint stocks_pk
        unique (stock_name),
    constraint stocks_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists sub_group
(
    id          int auto_increment
        primary key,
    name        varchar(50)                         not null,
    main_id     int                                 not null,
    date_insert datetime  default CURRENT_TIMESTAMP not null,
    updated_at  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id     int       default 1                 not null,
    constraint sub_group_pk
        unique (name),
    constraint sub_group_main_group_id_fk
        foreign key (main_id) references main_group (id),
    constraint sub_group_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists suppliers
(
    id            int auto_increment
        primary key,
    name          varchar(50)                         not null,
    tel           varchar(50)                         null,
    address       varchar(255)                        null,
    notes         longtext                            null,
    first_balance double    default 0                 not null,
    table_id      int       default 1                 not null,
    date_insert   datetime  default CURRENT_TIMESTAMP not null,
    updated_at    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id       int       default 1                 not null,
    area_id       int       default 1                 not null,
    constraint suppliers_pk
        unique (name),
    constraint suppliers_table_area_id_fk
        foreign key (area_id) references table_area (id),
    constraint suppliers_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists targeted_sales
(
    id            int auto_increment
        primary key,
    delegate_id   int                                 not null,
    target        double                              not null,
    target_ratio1 double    default 100               not null,
    rate_1        double    default 0                 not null,
    target_ratio2 double    default 0                 not null,
    rate_2        double    default 0                 not null,
    target_ratio3 double    default 0                 not null,
    rate_3        double    default 0                 not null,
    notes         varchar(200)                        null,
    date_insert   datetime  default CURRENT_TIMESTAMP not null,
    updated_at    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id       int       default 1                 not null,
    constraint targeted_sales_employees_id_fk
        foreign key (delegate_id) references employees (id)
            on update cascade on delete cascade,
    constraint targeted_sales_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists treasury
(
    id          int auto_increment
        primary key,
    t_name      varchar(50)                         not null,
    amount      double    default 0                 not null,
    date_insert datetime  default CURRENT_TIMESTAMP not null,
    updated_at  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id     int       default 1                 not null,
    constraint treasury_pk
        unique (t_name),
    constraint treasury_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists expenses_details
(
    id          int auto_increment
        primary key,
    type_code   int                                 not null,
    date        date                                not null,
    amount      int       default 0                 not null,
    notes       varchar(255)                        null,
    emp_id      int       default 0                 not null,
    treasury_id int       default 1                 not null,
    date_insert datetime  default CURRENT_TIMESTAMP not null,
    updated_at  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id     int       default 1                 not null,
    constraint expenses_details_expenses_id_fk
        foreign key (type_code) references expenses (id),
    constraint expenses_details_treasury_id_fk
        foreign key (treasury_id) references treasury (id),
    constraint expenses_details_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists expense_salary
(
    employee_id         int not null,
    expenses_details_id int not null,
    constraint expense_salary_employees_id_fk
        foreign key (employee_id) references employees (id),
    constraint expense_salary_expenses_details_id_fk
        foreign key (expenses_details_id) references expenses_details (id)
            on update cascade on delete cascade
);

create table if not exists suppliers_accounts
(
    account_num           int                                 not null
        primary key,
    account_code          int                                 not null,
    account_date          date                                not null,
    purchase              double    default 0                 not null,
    paid                  double                              not null,
    numberInv             int                                 not null,
    notes                 longtext                            null,
    treasury_id           int       default 1                 not null,
    table_id              int       default 2                 not null,
    date_insert           datetime  default CURRENT_TIMESTAMP not null,
    updated_at            timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id               int       default 1                 not null,
    invoice_number_return int       default 0                 not null comment 'This column for number invoice for returns',
    constraint suppliers_accounts_suppliers_id_fk
        foreign key (account_code) references suppliers (id),
    constraint suppliers_accounts_treasury_id_fk
        foreign key (treasury_id) references treasury (id),
    constraint suppliers_accounts_users_id_fk
        foreign key (user_id) references users (id)
);

create index suppliers_accounts_total_buy_invoice_number_fk
    on suppliers_accounts (numberInv);

create table if not exists total_buy
(
    invoice_number int                                 not null
        primary key,
    sup_code       int                                 not null,
    invoice_type   int       default 1                 not null,
    invoice_date   date                                not null,
    total          double                              not null,
    discount       double                              not null,
    paid_up        double                              not null comment 'paid from the treasury مدفوع نقدا من الخزينة',
    stock_id       int       default 1                 not null,
    treasury_id    int       default 1                 not null,
    notes          longtext                            null,
    table_id       int       default 3                 not null,
    date_insert    datetime  default CURRENT_TIMESTAMP not null,
    updated_at     timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id        int       default 1                 not null,
    constraint total_buy_stocks_stock_id_fk
        foreign key (stock_id) references stocks (stock_id),
    constraint total_buy_suppliers_sup_id_fk
        foreign key (sup_code) references suppliers (id),
    constraint total_buy_treasury_id_fk
        foreign key (treasury_id) references treasury (id),
    constraint total_buy_users_id_fk
        foreign key (user_id) references users (id)
);

create index total_buy_suppliers_sup_code_fk
    on total_buy (sup_code);

create table if not exists total_buy_re
(
    id               bigint                              not null
        primary key,
    sup_id           int                                 not null,
    invoice_date     date                                not null,
    invoice_type     int       default 1                 not null,
    total            double                              not null,
    discount         double                              not null,
    paid_to_treasury double                              not null comment 'Paid to the treasury مدفوعات الى الخزينة',
    stock_id         int                                 not null,
    treasury_id      int       default 1                 not null,
    notes            longtext                            null,
    table_id         int       default 4                 not null,
    date_insert      datetime  default CURRENT_TIMESTAMP not null,
    updated_at       timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id          int       default 1                 not null,
    constraint total_buy_re_stocks_stock_id_fk
        foreign key (stock_id) references stocks (stock_id),
    constraint total_buy_re_suppliers_sup_id_fk
        foreign key (sup_id) references suppliers (id),
    constraint total_buy_re_treasury_id_fk
        foreign key (treasury_id) references treasury (id),
    constraint total_buy_re_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists treasury_deposit_expenses
(
    id                  int auto_increment
        primary key,
    statement           varchar(50)                         not null,
    date_inter          date                                not null,
    amount              double                              not null,
    description_data    text                                null,
    deposit_or_expenses int       default 1                 not null,
    treasury_id         int       default 1                 not null,
    date_insert         datetime  default CURRENT_TIMESTAMP not null,
    updated_at          timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id             int       default 1                 not null,
    constraint treasury_deposit_expenses_treasury_id_fk
        foreign key (treasury_id) references treasury (id),
    constraint treasury_deposit_expenses_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists treasury_transfers
(
    id            int auto_increment
        primary key,
    treasury_from int                                 not null,
    treasury_to   int                                 not null,
    amount        double                              not null,
    transfer_date date                                not null,
    notes         longtext                            null,
    date_insert   datetime  default CURRENT_TIMESTAMP not null,
    updated_at    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id       int       default 1                 not null,
    constraint treasury_transfers_treasury_id_fk
        foreign key (treasury_from) references treasury (id),
    constraint treasury_transfers_treasury_id_fk_2
        foreign key (treasury_to) references treasury (id),
    constraint treasury_transfers_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists type_price
(
    id          int auto_increment
        primary key,
    name        varchar(50)                         not null,
    date_insert datetime  default CURRENT_TIMESTAMP not null,
    updated_at  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id     int       default 1                 not null,
    constraint items_price_pk
        unique (name),
    constraint type_price_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists custom
(
    id            int auto_increment
        primary key,
    name          varchar(100)                        not null,
    tel           varchar(50)                         null,
    address       varchar(200)                        null,
    notes         longtext                            null,
    limit_num     double                              not null,
    first_balance double    default 0                 not null,
    price_id      int                                 not null,
    table_id      int       default 1                 not null,
    created_at    timestamp default CURRENT_TIMESTAMP not null,
    updated_at    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id       int       default 1                 not null,
    area_id       int       default 1                 not null,
    constraint custom_pk
        unique (name),
    constraint custom_items_price_id_fk
        foreign key (price_id) references type_price (id),
    constraint custom_table_area_id_fk
        foreign key (area_id) references table_area (id),
    constraint custom_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists customers_accounts
(
    account_num           int                                 not null
        primary key,
    account_code          int                                 null,
    account_date          date                                null,
    paid                  double                              null,
    notes                 longtext                            null,
    treasury_id           int       default 1                 not null,
    purchase              double    default 0                 not null,
    numberInv             int                                 not null,
    table_id              int       default 2                 not null,
    created_at            timestamp default CURRENT_TIMESTAMP not null,
    updated_at            timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id               int       default 1                 not null,
    invoice_number_return int       default 0                 not null comment 'This column for number invoice for returns',
    constraint customers_accounts_custom_id_fk
        foreign key (account_code) references custom (id),
    constraint customers_accounts_treasury_id_fk
        foreign key (treasury_id) references treasury (id),
    constraint customers_accounts_users_id_fk
        foreign key (user_id) references users (id)
);

create index customers_accounts_total_sales_invoice_number_fk
    on customers_accounts (numberInv);

create table if not exists total_sales
(
    invoice_number int                                 not null
        primary key,
    sup_code       int                                 not null,
    invoice_type   int       default 1                 not null,
    invoice_date   date                                not null,
    total          double                              not null,
    discount       double                              not null,
    paid_up        double                              not null comment 'paid to the treasury مدفوع نقدا الى الخزينة',
    stock_id       int       default 1                 not null,
    delegate_id    int                                 not null,
    treasury_id    int       default 1                 not null,
    notes          longtext                            null,
    table_id       int       default 3                 not null,
    date_insert    datetime  default CURRENT_TIMESTAMP not null,
    updated_at     timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id        int       default 1                 not null,
    constraint total_sales_custom_sup_id_fk
        foreign key (sup_code) references custom (id),
    constraint total_sales_employees_id_fk
        foreign key (delegate_id) references employees (id),
    constraint total_sales_stocks_stock_id_fk
        foreign key (stock_id) references stocks (stock_id),
    constraint total_sales_treasury_id_fk
        foreign key (treasury_id) references treasury (id),
    constraint total_sales_users_id_fk
        foreign key (user_id) references users (id)
);

create index total_sales_sup_code_fk
    on total_sales (sup_code);

create index total_sales_users_id_fk2
    on total_sales (delegate_id);

create table if not exists total_sales_re
(
    id                 bigint                              not null
        primary key,
    sup_id             int                                 not null,
    invoice_date       date                                not null,
    invoice_type       int       default 1                 not null,
    total              double                              not null,
    discount           double                              not null,
    paid_from_treasury double                              not null comment 'paid from the treasury مدفوع نقدا من الخزينة',
    stock_id           int       default 1                 not null,
    delegate_id        int                                 not null,
    treasury_id        int       default 1                 not null,
    notes              longtext                            null,
    table_id           int       default 4                 not null,
    date_insert        datetime  default CURRENT_TIMESTAMP not null,
    updated_at         timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id            int       default 1                 not null,
    constraint total_sales_re_custom_id_fk
        foreign key (sup_id) references custom (id),
    constraint total_sales_re_employees_id_fk
        foreign key (delegate_id) references employees (id),
    constraint total_sales_re_stocks_stock_id_fk
        foreign key (stock_id) references stocks (stock_id),
    constraint total_sales_re_treasury_id_fk
        foreign key (treasury_id) references treasury (id),
    constraint total_sales_re_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists units
(
    unit_id     int auto_increment
        primary key,
    unit_name   varchar(50)                         not null,
    value_d     double    default 1                 not null,
    date_insert datetime  default CURRENT_TIMESTAMP not null,
    updated_at  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id     int       default 1                 not null,
    constraint units_pk
        unique (unit_name),
    constraint units_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists items
(
    id                       int auto_increment
        primary key,
    barcode                  varchar(200)                         not null,
    nameItem                 varchar(200)                         not null,
    sub_num                  int                                  not null,
    buy_price                double     default 0                 not null,
    sel_price1               double     default 0                 not null,
    sel_price2               double     default 0                 not null,
    sel_price3               double     default 0                 not null,
    unit_id                  int                                  not null,
    mini_quantity            double     default 1                 not null,
    first_balance            double     default 0                 not null,
    item_image               longblob                             null,
    item_active              tinyint(1) default 1                 not null,
    item_has_validity        tinyint(1) default 0                 not null,
    number_validity_days     int        default 0                 not null,
    alert_days_before_expire int        default 0                 not null,
    item_has_package         tinyint(1) default 0                 not null,
    created_at               timestamp  default CURRENT_TIMESTAMP not null,
    updated_at               timestamp  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id                  int        default 1                 not null,
    constraint items_barcode_uindex
        unique (barcode),
    constraint items_pk
        unique (nameItem),
    constraint items_sub_group_id_fk
        foreign key (sub_num) references sub_group (id),
    constraint items_units_unit_id_fk
        foreign key (unit_id) references units (unit_id),
    constraint items_users_id_fk
        foreign key (user_id) references users (id)
);

create table if not exists items_package
(
    id         int auto_increment
        primary key,
    item_id    int                                not null,
    package_id int                                not null,
    quantity   double                             not null,
    created_at datetime default CURRENT_TIMESTAMP null,
    updated_at datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint items_package_items_id_fk
        foreign key (package_id) references items (id)
            on update cascade on delete cascade
);

create table if not exists items_stock
(
    id            int auto_increment
        primary key,
    item_id       int           not null,
    stock_id      int           not null,
    first_balance int default 0 not null,
    constraint items_stock_items_id_fk
        foreign key (item_id) references items (id)
            on update cascade on delete cascade,
    constraint items_stock_stocks_stock_id_fk
        foreign key (stock_id) references stocks (stock_id)
);

create table if not exists items_units
(
    id            int auto_increment
        primary key,
    items_id      int                                 not null,
    items_barcode varchar(50)                         not null,
    unit          int                                 not null,
    quantity      double                              not null,
    buy_price     double                              not null,
    sel_price     double                              not null,
    date_insert   datetime  default CURRENT_TIMESTAMP not null,
    updated_at    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    user_id       int       default 1                 not null,
    constraint items_units_pk
        unique (items_barcode),
    constraint items_units_items_id_fk
        foreign key (items_id) references items (id)
            on update cascade on delete cascade,
    constraint items_units_units_unit_id_fk
        foreign key (unit) references units (unit_id)
);

create index items_units_items_num_fk
    on items_units (items_id);

create table if not exists purchase
(
    id              int auto_increment
        primary key,
    invoice_number  int              not null,
    num             int              not null,
    type            int    default 1 not null,
    quantity        double           not null,
    price           double           not null,
    discount        int    default 0 not null,
    type_value      double default 1 not null,
    expiration_date date             null,
    constraint purchase_items_id_fk
        foreign key (num) references items (id),
    constraint purchase_total_buy_invoice_number_fk
        foreign key (invoice_number) references total_buy (invoice_number)
            on update cascade on delete cascade,
    constraint purchase_units_unit_id_fk
        foreign key (type) references units (unit_id)
);

create table if not exists purchase_re
(
    id              int auto_increment
        primary key,
    invoice_number  bigint           not null,
    item_id         int              not null,
    type            int    default 1 not null,
    quantity        double           not null,
    price           double           not null,
    discount        double default 0 not null,
    type_value      double default 1 not null,
    expiration_date date             null,
    constraint purchase_re_items_id_fk
        foreign key (item_id) references items (id),
    constraint purchase_re_total_buy_re_id_fk
        foreign key (invoice_number) references total_buy_re (id)
            on update cascade on delete cascade,
    constraint purchase_re_units_unit_id_fk
        foreign key (type) references units (unit_id)
);

create table if not exists sales
(
    id               int auto_increment
        primary key,
    invoice_number   int                  not null,
    num              int                  not null,
    type             int        default 1 not null,
    quantity         double               not null,
    price            double               not null,
    buy_price        double               not null,
    total_sel_price  double     default 0 not null,
    total_buy_price  double     default 0 not null,
    total_profit     double     default 0 not null,
    discount         double     default 0 not null,
    type_value       double     default 1 not null,
    expiration_date  date                 null,
    item_has_package tinyint(1) default 0 not null,
    constraint sales_items_id_fk
        foreign key (num) references items (id),
    constraint sales_total_invoice_number_fk
        foreign key (invoice_number) references total_sales (invoice_number)
            on update cascade on delete cascade,
    constraint sales_units_unit_id_fk
        foreign key (type) references units (unit_id)
);

create table if not exists sales_package
(
    id              int auto_increment
        primary key,
    sales_id        int              not null,
    item_id         int              not null,
    unit_id         int    default 1 not null,
    quantity        double           not null,
    price           double           not null,
    buy_price       double           not null,
    total_sel_price double default 0 not null,
    total_buy_price double default 0 not null,
    total_profit    double default 0 not null,
    discount        double default 0 not null,
    unit_value      double default 1 not null,
    expiration_date date             null,
    constraint sales_package_items_id_fk
        foreign key (item_id) references items (id),
    constraint sales_package_sales_id_fk
        foreign key (sales_id) references sales (id)
            on update cascade on delete cascade,
    constraint sales_package_units_unit_id_fk
        foreign key (unit_id) references units (unit_id)
);

create index sales_items_id_fk
    on sales_package (item_id);

create index sales_total_invoice_number_fk
    on sales_package (sales_id);

create index sales_units_unit_id_fk
    on sales_package (unit_id);

create table if not exists sales_re
(
    id              int auto_increment
        primary key,
    invoice_number  bigint           not null,
    item_id         int              not null,
    type            int    default 1 not null,
    quantity        double           not null,
    price           double           not null,
    buy_price       double default 0 not null,
    total_sel_price double default 0 not null,
    total_buy_price double default 0 not null,
    total_profit    double default 0 not null,
    discount        double default 0 not null,
    type_value      double default 1 not null,
    expiration_date date             null,
    constraint sales_re_items_id_fk
        foreign key (item_id) references items (id),
    constraint sales_re_total_sales_re_id_fk
        foreign key (invoice_number) references total_sales_re (id)
            on update cascade on delete cascade,
    constraint sales_re_units_unit_id_fk
        foreign key (type) references units (unit_id)
);

create table if not exists user_permission
(
    id            int auto_increment
        primary key,
    permission_id int                                 not null,
    user_id       int                                 not null,
    check_status  int       default 0                 not null,
    updated_at    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint user_permission_permission_id_fk
        foreign key (permission_id) references permission (id)
            on update cascade on delete cascade,
    constraint user_permission_users_id_fk
        foreign key (user_id) references users (id)
            on update cascade on delete cascade
);

CREATE TABLE user_shifts (
                             id INT AUTO_INCREMENT PRIMARY KEY,
                             user_id INT NOT NULL,
                             open_time DATETIME NOT NULL,
                             close_time DATETIME NULL,
                             open_balance DOUBLE DEFAULT 0.0,
                             close_balance DOUBLE DEFAULT 0.0,
                             is_open BOOLEAN DEFAULT TRUE,
                             notes TEXT NULL,
                             FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);


