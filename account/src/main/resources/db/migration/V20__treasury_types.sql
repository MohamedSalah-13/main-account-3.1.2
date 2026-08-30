-- =====================================================================
-- V20 - What a treasury is, and what its `amount` means.
--
-- The schema has carried several treasuries since the baseline, and every cash
-- document carries a `treasury_id`. What it never carried is what kind of vessel
-- a treasury is: a drawer of notes, an e-wallet (فودافون كاش، انستاباي) or a bank
-- account. They behave differently - a wallet charges a fee, a closed drawer must
-- stop appearing in pickers without being deleted - and nothing could tell them
-- apart.
--
-- `amount` is the second half of this file, and the more important one. It was
-- written once at insert and never again: TreasuryDao.update sets t_name and
-- user_id only, and increaseAmount/decreaseAmount are called by nothing reachable.
-- Meanwhile treasury_balance sums the documents and ignores `amount` entirely,
-- and treasury_balance_after_convert adds `amount` to the transfers and ignores
-- the documents. Three numbers, no definition.
--
-- This migration fixes the meaning rather than the number: `amount` is the
-- **opening balance**, the value the vessel held before this system knew about it,
-- and R__views.sql makes it the first line of every statement. Nothing is
-- recomputed here - the column keeps whatever it holds - but a client whose row
-- was filled in with the intended *current* balance will see that figure move to
-- the top of the statement and be counted once. That is documented in
-- docs/treasury-plan.md §13 and is why the opening balance is editable on screen.
--
-- See docs/treasury-plan.md §2 and §6.
-- =====================================================================

DELIMITER $$

-- Adds a column only when it is missing, so a database that has already been
-- through this - or one built by a future baseline that includes it - lands in
-- the same place instead of failing.
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

DELIMITER ;

CALL add_column_if_missing('treasury', 'treasury_type',
                           "VARCHAR(20) DEFAULT 'CASH' NOT NULL COMMENT 'CASH | WALLET | BANK'");

CALL add_column_if_missing('treasury', 'is_active',
                           "TINYINT DEFAULT 1 NOT NULL COMMENT 'an inactive treasury keeps its history and leaves the pickers'");

CALL add_column_if_missing('treasury', 'sort_order',
                           'INT DEFAULT 0 NOT NULL');

CALL add_column_if_missing('treasury', 'opening_date',
                           "DATE NULL COMMENT 'the date the opening line carries in the statement'");

-- Read by the wallet fee step (docs/treasury-plan.md §5.2), which is a later phase.
-- The column arrives now so a client upgrades once.
CALL add_column_if_missing('treasury', 'fee_percent',
                           'DECIMAL(5, 2) DEFAULT 0 NOT NULL');

CALL add_constraint_if_missing('treasury', 'treasury_type_chk',
                               "CHECK (treasury_type IN ('CASH', 'WALLET', 'BANK'))");

CALL add_constraint_if_missing('treasury', 'treasury_fee_percent_chk',
                               'CHECK (fee_percent >= 0 AND fee_percent <= 100)');

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_constraint_if_missing;

-- The opening line has to be dated, and the row's own creation date is the only
-- honest answer for treasuries that already exist.
UPDATE treasury
SET opening_date = DATE(date_insert)
WHERE opening_date IS NULL;

-- `amount` now has one meaning, and it is written in the schema so nobody has to
-- read a plan to find it.
ALTER TABLE treasury
    MODIFY COLUMN amount DECIMAL(14, 2) DEFAULT 0 NOT NULL
        COMMENT 'الرصيد الافتتاحي - opening balance, not the current one (treasury_current_balance)';
