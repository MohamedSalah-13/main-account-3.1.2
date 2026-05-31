# this use after create table and triggers
INSERT INTO users (id, user_name, user_pass, user_activity, user_available) VALUES
    (1, 'admin', 'admin', 1, 1)
ON DUPLICATE KEY UPDATE
                     user_name = VALUES(user_name),
                     user_pass = VALUES(user_pass),
                     user_activity = VALUES(user_activity),
                     user_available = VALUES(user_available);

INSERT INTO type_price (name)
VALUES ('سعر بيع 1'),
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
