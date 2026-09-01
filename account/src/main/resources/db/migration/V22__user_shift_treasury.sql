-- =====================================================================
-- V22 - A shift belongs to a till, and records what actually passed through it.
--
-- user_shifts stores the number a cashier is answerable for: `difference`, the
-- gap between the cash counted at close and the cash the system expected. That
-- number was computed by UserShiftDao.calculateShiftSummary, which read four of
-- the ten cash sources and never mentioned treasury_id at all.
--
-- Both halves of that are fixed around this migration:
--
--   * `treasury_id` - a shift is opened on one till. Without it there is no
--     question to ask: in a business with a drawer, an e-wallet and a bank
--     account - the case the whole treasury was built for - the wallet's
--     collections were being counted into the cash expected in the drawer.
--     Existing rows get 1, which is what they were already computed as: the
--     summary summed every treasury together, and every expense was written
--     against treasury 1 regardless (see V22's sibling fix, finding ن-٢).
--
--   * `total_cash_in` / `total_cash_out` - the expected balance is now
--     opening + everything in - everything out, over treasury_balance, which is
--     the one place that knows what a cash movement is. The five existing total_*
--     columns stay as the report's breakdown, but they are no longer what the
--     expectation is derived from: they name five sources and there are ten, so
--     a customer collection or a supplier payment had nowhere to land.
--
-- Old rows keep their stored figures untouched. They were computed under the old
-- rule and cannot be recomputed - the expectation depends on which till the shift
-- was on, and for a shift already closed nobody recorded that. A row from before
-- this migration says what it said on the day.
--
-- See docs/audit-2026-08-31.html finding ن-٣.
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

CALL add_column_if_missing('user_shifts', 'treasury_id',
                           "INT DEFAULT 1 NOT NULL COMMENT 'the till this shift is opened on - the summary is filtered by it'");

CALL add_column_if_missing('user_shifts', 'total_cash_in',
                           'DECIMAL(14, 2) DEFAULT 0 NOT NULL');

CALL add_column_if_missing('user_shifts', 'total_cash_out',
                           'DECIMAL(14, 2) DEFAULT 0 NOT NULL');

-- No ON DELETE CASCADE: a treasury that has held a shift is one DeleteRegistry
-- already refuses to delete, and a cascade here would take the cashier's record
-- of the day with it if that guard were ever removed.
CALL add_constraint_if_missing('user_shifts', 'user_shifts_treasury_id_fk',
                               'FOREIGN KEY (treasury_id) REFERENCES treasury (id)');

-- The open shift is looked up per user; per till is the new question the summary
-- and the admin screen ask.
CALL add_index_if_missing_local('user_shifts', 'idx_user_shifts_treasury_open', 'treasury_id, is_open');

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS add_constraint_if_missing;
DROP PROCEDURE IF EXISTS add_index_if_missing_local;
