DROP DATABASE IF EXISTS account_system_db;
CREATE DATABASE account_system_db;
USE account_system_db;
create table if not exists chart_class
(
    id         int primary key,
    chart_name varchar(50) not null,
    constraint financial_statements_pk_2
        unique (chart_name)
);

create table if not exists natural_side
(
    id        int primary key,
    side_name varchar(50) not null,
    constraint natural_side_pk_2
        unique (side_name)
);

create table if not exists main_accounts
(
    id              int                   not null
        primary key,
    name1           varchar(50)           not null,
    name2           varchar(50)           null,
    natural_side_id int                   null,
    chart_class_id  int                   null,
    isLeaf          boolean default false NOT NULL,
    parent          int,

    constraint main_accounts_pk_2
        unique (name1),
    constraint main_accounts_pk_3
        unique (name2),
    constraint main_accounts_natural_side_id_fk
        foreign key (natural_side_id) references natural_side (id),
    constraint main_accounts_chart_class_id_fk
        foreign key (chart_class_id) references chart_class (id)
);

insert into chart_class(id, chart_name)
values (1, 'BalanceSheet'),
       (2, 'IncomeStatement'),
       (3, 'Others');

insert into natural_side(id, side_name)
values (1, 'Credit'),
       (2, 'Debit');

insert into main_accounts (id, name1, name2, natural_side_id, chart_class_id, isLeaf, parent)
values (1, 'الأصول', 'Assets', 2, 1, 0, 0),
       (2, 'حقوق الملكية والالتزامات', 'Equity and Liabilities', 1, 1, 0, 0),
       (3, 'الإيرادات', 'Revenues', 1, 2, 0, 0),
       (4, 'المصروفات', 'Expenses', 2, 2, 0, 0),
       (5, 'حسابات وسيطة', 'Intermediary Accounts', 2, 3, 1, 0),
       (11, 'الأصول المتداولة ( قصيرة ألأجل )', 'Current Assets (Short-term)', 2, 1, 1, 1),
       (12, 'الأصول الغير متداولة ( طويلة ألأجل )', 'Non-Current Assets (Long-term)', 2, 1, 1, 1),
       (21, 'حقوق الملكية', 'Equity', 1, 1, 1, 2),
       (22, 'الالتزامات الغير متداولة ( طويلة ألأجل )', 'Non-Current Liabilities (Long-term)', 1, 1, 1, 2),
       (23, 'الالتزامات المتداولة (قصيرة ألأجل)', 'Current Liabilities (Short-term)', 1, 1, 1, 2),
       (31, 'إيرادات النشاط', 'Operating Revenues', 1, 2, 1, 3),
       (32, 'إيرادات متنوعة', 'Miscellaneous Revenues', 1, 2, 1, 3),
       (41, 'تكلفة الإيرادات', 'Cost of Revenues', 2, 2, 1, 4),
       (42, 'مصاريف عمومية', 'General Expenses', 2, 2, 1, 4),
       (43, 'مصاريف تسويقية', 'Marketing Expenses', 2, 2, 1, 4);
