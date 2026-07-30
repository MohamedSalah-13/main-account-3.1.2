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
