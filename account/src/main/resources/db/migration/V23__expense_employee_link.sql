-- =====================================================================
-- V23 - The employee on an expense, read from the column that holds it.
--
-- Finding ن-١ of the 2026-08-31 audit, and it is a loop that was broken in
-- silence. ExpensesDetailsDao writes the employee into expenses_details.emp_id.
-- expenses_details_view reads the name through `LEFT JOIN expense_salary es ON
-- ed.id = es.expenses_details_id`, and **no line of code in the project writes
-- to expense_salary at all**. So:
--
--   * the employee name is '' on every salary and every advance ever entered,
--     except three legacy rows that predate this system;
--   * searching the expenses list by an employee's name can never match;
--   * DeleteRegistry.EMPLOYEES declares expense_salary.employee_id, so it blocks
--     the deletion of exactly the one employee those three rows name, while every
--     other employee with years of salaries behind them deletes cleanly. A guard
--     on the wrong row is worse than a missing one: it looks present.
--
-- This migration makes emp_id the real link, which it already was in the writing:
--
--   1. Anything expense_salary knows and emp_id does not is copied across, so the
--      three legacy rows keep their employee.
--   2. emp_id becomes NULL where it is 0 - the sentinel the screen writes for "no
--      employee" - and where it names an employee that no longer exists. NULL is
--      what a nullable foreign key needs, and 0 was never an employee: the seeded
--      delegate is id 1.
--   3. A foreign key, so from here on the database refuses what DeleteRegistry
--      refuses. Nothing enforced this before, and rows pointing at deleted
--      employees are exactly how the name went missing in more places than the
--      view.
--
-- expense_salary itself is kept, empty of meaning but not of rows, the way
-- user_permission is kept: it is the evidence of what the schema used to intend,
-- and dropping it would take three real rows out of every install that has them.
-- Nothing reads it for a decision any more.
-- =====================================================================

-- 1. What only expense_salary knows.
UPDATE expenses_details ed
    JOIN expense_salary es ON es.expenses_details_id = ed.id
SET ed.emp_id = es.employee_id
WHERE ed.emp_id = 0
  AND es.employee_id IS NOT NULL;

-- 2. The sentinel, and any employee that has since been deleted.
ALTER TABLE expenses_details
    MODIFY COLUMN emp_id INT NULL
        COMMENT 'the employee a salary or advance belongs to - NULL for every other kind of expense';

UPDATE expenses_details
SET emp_id = NULL
WHERE emp_id = 0;

UPDATE expenses_details
SET emp_id = NULL
WHERE emp_id IS NOT NULL
  AND emp_id NOT IN (SELECT id FROM employees);

-- 3. The key the column never had.
DELIMITER $$

DROP PROCEDURE IF EXISTS add_constraint_if_missing$$
CREATE PROCEDURE add_constraint_if_missing(IN t_name VARCHAR(64), IN k_name VARCHAR(64),
                                           IN k_def TEXT)
BEGIN
    DECLARE key_exists INT;

    SELECT COUNT(*)
    INTO key_exists
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = t_name
      AND CONSTRAINT_NAME = k_name;

    IF key_exists = 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' ADD CONSTRAINT ', k_name, ' ', k_def);
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS add_index_if_missing_local$$
CREATE PROCEDURE add_index_if_missing_local(IN t_name VARCHAR(64), IN i_name VARCHAR(64),
                                            IN i_columns TEXT)
BEGIN
    DECLARE index_exists INT;

    SELECT COUNT(*)
    INTO index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = t_name
      AND INDEX_NAME = i_name;

    IF index_exists = 0 THEN
        SET @query = CONCAT('CREATE INDEX ', i_name, ' ON ', t_name, ' (', i_columns, ')');
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_index_if_missing_local('expenses_details', 'expenses_details_emp_idx', 'emp_id');

-- Deliberately not ON DELETE CASCADE: an employee's salary is an expense the
-- business paid, and it does not stop being one when the employee leaves. The
-- delete is refused instead, by DeleteRegistry.EMPLOYEES and now by the schema.
CALL add_constraint_if_missing('expenses_details', 'expenses_details_employees_id_fk',
                               'FOREIGN KEY (emp_id) REFERENCES employees (id)');

DROP PROCEDURE IF EXISTS add_constraint_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing_local;
