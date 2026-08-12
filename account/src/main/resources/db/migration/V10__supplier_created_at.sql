-- One name for "when this row was entered", on the supplier side too.
--
-- V4 already renamed date_insert to created_at on custom, customers_accounts and items:
--
--   CALL RenameColumnSafe('custom', 'date_insert', 'created_at', ...);
--   CALL RenameColumnSafe('customers_accounts', 'date_insert', 'created_at', ...);
--
-- and stopped there, so the two halves of the same pair ended up spelled differently -
-- custom.created_at against suppliers.date_insert, customers_accounts.created_at against
-- suppliers_accounts.date_insert. Every statement over a party or a party's account then
-- had to carry the difference as data (PartyTableSpec.createdColumn,
-- PartyLedgerSpec.createdColumn) for no reason anybody chose.
--
-- This finishes what V4 started, in the direction V4 chose. The other nineteen tables
-- keep date_insert: they are not paired with anything and renaming them would be churn
-- across every report.
--
-- The type is left alone. custom.created_at is a TIMESTAMP and suppliers.date_insert is a
-- DATETIME, and converting between them is not a rename: TIMESTAMP stores an instant in
-- UTC and re-reads it in the session's time zone, and it ends in 2038. Renaming a column
-- cannot lose a row; converting one can.

DELIMITER $$

-- Renames only when there is something to rename, so a database that has already been
-- through this - or a fresh one built from V1 and then migrated - lands in the same
-- place, and running it twice is not an error.
DROP PROCEDURE IF EXISTS rename_column_if_present$$
CREATE PROCEDURE rename_column_if_present(IN t_name VARCHAR(64), IN old_c VARCHAR(64),
                                          IN new_c VARCHAR(64), IN col_def TEXT)
BEGIN
    DECLARE old_exists INT;
    DECLARE new_exists INT;

    SELECT COUNT(*) INTO old_exists FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t_name AND COLUMN_NAME = old_c;

    SELECT COUNT(*) INTO new_exists FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t_name AND COLUMN_NAME = new_c;

    IF old_exists > 0 AND new_exists = 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' CHANGE COLUMN ', old_c, ' ', new_c, ' ', col_def);
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL rename_column_if_present('suppliers', 'date_insert', 'created_at',
                              'DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL');

CALL rename_column_if_present('suppliers_accounts', 'date_insert', 'created_at',
                              'DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL');

DROP PROCEDURE IF EXISTS rename_column_if_present;

-- The views over both tables name the column, so they are rebuilt from R__views.sql,
-- which Flyway re-runs after this file: account_suppliers_table, treasury_balance and
-- earnings_reports. Nothing else reads it - the triggers audit id, name and
-- first_balance only, and no report names the column.
