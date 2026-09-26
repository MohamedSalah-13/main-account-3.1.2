-- Additional, invented rows for the employee-system guide.
-- Run only against the isolated account_manual_demo schema after the standard demo seed.

-- Current monthly compensation lets the payroll screens and payslips show useful sample rows.
INSERT INTO employee_compensation (employee_id, effective_from, salary_kind, rate, notes, user_id)
SELECT sample.employee_id, DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), 'MONTHLY', sample.rate,
       'راتب تجريبي لدليل الموظفين', 1
FROM (
    SELECT 2 AS employee_id, 8500.00 AS rate
    UNION ALL SELECT 3, 9200.00
    UNION ALL SELECT 4, 11500.00
) sample
WHERE EXISTS (SELECT 1 FROM employees employee WHERE employee.id = sample.employee_id)
  AND NOT EXISTS (SELECT 1 FROM employee_compensation compensation
                  WHERE compensation.employee_id = sample.employee_id
                    AND compensation.effective_from = DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'));

UPDATE employees employee
JOIN employee_compensation compensation
  ON compensation.employee_id = employee.id
 AND compensation.effective_from = DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')
 AND compensation.notes = 'راتب تجريبي لدليل الموظفين'
SET employee.salary = compensation.rate;

INSERT INTO employee_ledger (employee_id, entry_date, kind, amount, notes, user_id)
SELECT 2, CURRENT_DATE - INTERVAL 9 DAY, 'BONUS', 350.00, 'مكافأة تجريبية للدليل', 1
WHERE NOT EXISTS (SELECT 1 FROM employee_ledger
                  WHERE employee_id = 2 AND notes = 'مكافأة تجريبية للدليل');

INSERT INTO employee_ledger (employee_id, entry_date, kind, amount, notes, user_id)
SELECT 2, CURRENT_DATE - INTERVAL 4 DAY, 'DEDUCTION', 125.00, 'خصم تجريبي للدليل', 1
WHERE NOT EXISTS (SELECT 1 FROM employee_ledger
                  WHERE employee_id = 2 AND notes = 'خصم تجريبي للدليل');

-- A cash payment appears in the employee statement and is linked to the shop's employee-only
-- expense heading. The amount is fictional and is written only in the throwaway demo schema.
INSERT INTO expenses_details (type_code, date, amount, notes, emp_id, treasury_id, user_id)
SELECT (SELECT id FROM expenses WHERE employee_payment = 1 ORDER BY id LIMIT 1),
       CURRENT_DATE - INTERVAL 6 DAY, 1200.00, 'دفعة تجريبية لدليل الموظفين', 2, 1, 1
WHERE EXISTS (SELECT 1 FROM expenses WHERE employee_payment = 1)
  AND NOT EXISTS (SELECT 1 FROM expenses_details
                  WHERE emp_id = 2 AND notes = 'دفعة تجريبية لدليل الموظفين');

INSERT IGNORE INTO employee_cash_purpose (expense_id, purpose, user_id)
SELECT id, 'ADVANCE', 1
FROM expenses_details
WHERE emp_id = 2 AND notes = 'دفعة تجريبية لدليل الموظفين';

-- Give the current month's attendance grid a small, realistic sample. Friday (MySQL weekday 6)
-- is the configured weekly rest day; older dates are omitted at the month boundary.
INSERT IGNORE INTO attendance (employee_id, work_date, status, hours, leave_type_id, notes, user_id)
SELECT employee.id, dates.work_date,
       IF(DAYOFWEEK(dates.work_date) = 6, 'WEEKEND', 'PRESENT'),
       IF(DAYOFWEEK(dates.work_date) = 6, 0, 8), NULL, 'حضور تجريبي للدليل', 1
FROM employees employee
CROSS JOIN (
    SELECT CURRENT_DATE AS work_date
    UNION ALL SELECT CURRENT_DATE - INTERVAL 1 DAY
    UNION ALL SELECT CURRENT_DATE - INTERVAL 2 DAY
    UNION ALL SELECT CURRENT_DATE - INTERVAL 3 DAY
    UNION ALL SELECT CURRENT_DATE - INTERVAL 4 DAY
) dates
WHERE employee.id IN (2, 3, 4)
  AND dates.work_date >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')
  AND dates.work_date >= employee.hire_date;

INSERT INTO leave_request (employee_id, leave_type_id, date_from, date_to, status, reason, user_id)
SELECT 2, type.id, CURRENT_DATE + INTERVAL 3 DAY, CURRENT_DATE + INTERVAL 4 DAY,
       'PENDING', 'طلب إجازة تجريبي للدليل', 1
FROM leave_type type
WHERE type.type_name = 'إجازة سنوية'
  AND NOT EXISTS (SELECT 1 FROM leave_request request
                  WHERE request.employee_id = 2
                    AND request.reason = 'طلب إجازة تجريبي للدليل');
