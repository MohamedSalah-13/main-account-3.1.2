USE account_system_db;

-- =====================================================================
-- 002_seed_base_data.sql
-- بيانات أساسية لازمة لتشغيل النظام
-- =====================================================================

INSERT INTO users (id, user_name, user_pass, user_activity, user_available)
VALUES (1, 'admin', 'admin', 1, 1)
ON DUPLICATE KEY UPDATE
                     user_name = VALUES(user_name),
                     user_pass = VALUES(user_pass),
                     user_activity = VALUES(user_activity),
                     user_available = VALUES(user_available);

INSERT INTO type_price (id, name)
VALUES
    (1, 'سعر بيع 1'),
    (2, 'سعر2'),
    (3, 'سعر3')
ON DUPLICATE KEY UPDATE
    name = VALUES(name);

INSERT INTO table_area (id, area_name)
VALUES (1, 'القاهرة')
ON DUPLICATE KEY UPDATE
    area_name = VALUES(area_name);

INSERT INTO custom (id, name, limit_num, price_id)
VALUES (1, 'بيع نقدي', 5000, 1)
ON DUPLICATE KEY UPDATE
                     name = VALUES(name),
                     limit_num = VALUES(limit_num),
                     price_id = VALUES(price_id);

INSERT INTO suppliers (id, name)
VALUES (1, 'مورد عام')
ON DUPLICATE KEY UPDATE
    name = VALUES(name);

INSERT INTO units (unit_id, unit_name)
VALUES
    (1, 'قطعة'),
    (2, 'كرتونه')
ON DUPLICATE KEY UPDATE
    unit_name = VALUES(unit_name);

INSERT INTO stocks (stock_id, stock_name)
VALUES (1, 'الرئيسي')
ON DUPLICATE KEY UPDATE
    stock_name = VALUES(stock_name);

INSERT INTO main_group (id, name_g)
VALUES (1, 'عام 1')
ON DUPLICATE KEY UPDATE
    name_g = VALUES(name_g);

INSERT INTO sub_group (id, name, main_id)
VALUES (1, 'فرع 1', 1)
ON DUPLICATE KEY UPDATE
                     name = VALUES(name),
                     main_id = VALUES(main_id);

INSERT INTO jobs (id, job_name)
VALUES
    (1, 'المسئول'),
    (2, 'المدير'),
    (3, 'موظف'),
    (4, 'مندوب')
ON DUPLICATE KEY UPDATE
    job_name = VALUES(job_name);

INSERT INTO employees (id, column_name, birth_date, hire_date, salary, job)
VALUES (1, 'بيع مباشر', CURRENT_DATE(), CURRENT_DATE(), 0, 4)
ON DUPLICATE KEY UPDATE
                     column_name = VALUES(column_name),
                     birth_date = VALUES(birth_date),
                     hire_date = VALUES(hire_date),
                     salary = VALUES(salary),
                     job = VALUES(job);

INSERT INTO treasury (id, t_name, amount)
VALUES (1, 'الخزينة الرئيسية', 0)
ON DUPLICATE KEY UPDATE
                     t_name = VALUES(t_name),
                     amount = VALUES(amount);

INSERT INTO expenses (id, expenses_name)
VALUES
    (1, 'مرتبات'),
    (2, 'كهرباء'),
    (3, 'سلف'),
    (4, 'مياه'),
    (5, 'إيجارات'),
    (6, 'أخرى')
ON DUPLICATE KEY UPDATE
    expenses_name = VALUES(expenses_name);

SELECT '002_seed_base_data.sql executed successfully' AS status;