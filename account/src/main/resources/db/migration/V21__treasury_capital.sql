-- =====================================================================
-- V21 - Money the owner puts in, and money the owner takes out.
--
-- Both move a treasury and neither is a business result: capital paid in is not
-- income, and the owner's drawings are not an expense. Recorded as either, the
-- treasury would come out right and the profit would be a lie - and the profit is
-- the number the owner is actually reading.
--
-- No new table. `treasury_deposit_expenses` already carries an amount, a direction,
-- a treasury, a date and a statement, which is everything capital needs; what it
-- could not say is *what kind* of movement a row is. That is this one column.
--
-- The CHECK ties the category to the direction, so 'capital withdrawn' and 'owner
-- deposit' cannot exist: capital goes in, drawings come out, and anything else is a
-- NORMAL movement. The service refuses the same pairs first, with a message; this
-- is the floor under it - see docs/treasury-plan.md §4.
--
-- Nothing is reclassified. Every existing row becomes NORMAL, which is what every
-- existing row is: there was no way to enter capital before this.
--
-- The profit and loss report reads expenses from `expenses_details` only and does
-- not touch this table at all, so a capital row cannot reach it - that is checked by
-- ProfitLossExcludesCapitalTest rather than trusted.
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS add_column_if_missing$$
CREATE PROCEDURE add_column_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64),
                                       IN col_def TEXT)
BEGIN
    DECLARE col_exists INT;

    SELECT COUNT(*)
    INTO col_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = t_name
      AND COLUMN_NAME = c_name;

    IF col_exists = 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' ADD COLUMN ', c_name, ' ', col_def);
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

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

CALL add_column_if_missing('treasury_deposit_expenses', 'category',
                           "VARCHAR(20) DEFAULT 'NORMAL' NOT NULL COMMENT 'NORMAL | CAPITAL_IN | OWNER_DRAW'");

-- deposit_or_expenses: 1 = in, 2 = out.
CALL add_constraint_if_missing('treasury_deposit_expenses', 'treasury_deposit_category_chk',
                               "CHECK ((category = 'NORMAL')
                                    OR (category = 'CAPITAL_IN' AND deposit_or_expenses = 1)
                                    OR (category = 'OWNER_DRAW' AND deposit_or_expenses = 2))");

-- The capital report filters on the category over a date range, and the table also
-- carries every ordinary deposit and withdrawal there has ever been.
CALL add_index_if_missing_local('treasury_deposit_expenses',
                                'treasury_deposit_expenses_category_idx',
                                'category, date_inter');

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_constraint_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing_local;

-- An expense heading for the wallet transfer fee. It is read by the collection screen
-- in a later phase (docs/treasury-plan.md §5.2) and seeded now so a client upgrades
-- once rather than twice. `expenses.id` is not auto-increment, hence the MAX(id) + 1.
INSERT INTO expenses (id, expenses_name)
SELECT COALESCE(MAX(id), 0) + 1, 'عمولات تحويل'
FROM expenses
-- HAVING, not WHERE: with an aggregate and no GROUP BY, a WHERE that matches nothing
-- still produces one row (MAX of nothing is NULL), so the guard has to be applied
-- after the aggregation or a second run would insert id 1 and collide with the
-- unique name. COALESCE around the SUM covers the empty table, where it is NULL.
HAVING COALESCE(SUM(expenses_name = 'عمولات تحويل'), 0) = 0;
