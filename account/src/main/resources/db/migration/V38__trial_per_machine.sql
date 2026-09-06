-- =====================================================================
-- V38 - The trial stops being one row that every machine writes to.
--
-- The trial state lived in the `company` row: `trial_machine` naming the one
-- machine allowed to run, `trial_fail_count` counting tampering, and
-- `TrialManager.MAX_FAILS = 1`. In a one-computer shop that is coherent. In a
-- shop with a second till pointed at the same MySQL - which is the whole point
-- of a shared database - it is a trap with no way out:
--
--   1. the second machine reads `trial_machine` and finds another machine's id;
--   2. that is a mismatch, so it calls `failAndExit`;
--   3. `failAndExit` increments `company.trial_fail_count` - in the SHARED row;
--   4. `MAX_FAILS` is 1, so the FIRST machine, which was licensed and working,
--      refuses to start on its next launch, permanently, with no screen in the
--      application that can clear the counter.
--
-- One launch of a second computer therefore ends the shop's day. Nothing about
-- that is a licensing decision anybody made; it is a single-machine assumption
-- surviving into a multi-machine install.
--
-- So the state becomes one row per machine. A machine can only fail itself, and
-- a machine that has never been seen simply gets a row of its own.
--
-- The trial period is NOT restarted per machine: a new row inherits the earliest
-- installation date already recorded, so adding computers cannot extend a seven
-- day trial to seven more. That is `TrialManager.earliestInstallationDate`, and
-- the backfill below is what makes the first such row the existing install's.
--
-- The `company.trial_*` columns are left exactly where they are. They were never
-- created by a migration - `TrialManager` added them itself with ALTER TABLE on
-- first run - and dropping them here would take the evidence of an install's own
-- history out of every database that has it. Nothing reads them for a decision
-- from now on.
-- =====================================================================

CREATE TABLE IF NOT EXISTS trial_machine_state
(
    machine_id        VARCHAR(128) NOT NULL PRIMARY KEY
        COMMENT 'the Windows MachineGuid - the same value the licence file is bound to',
    installation_date DATE         NOT NULL
        COMMENT 'when the trial started for the shop, not for this machine: a new machine inherits the earliest',
    trial_hash        VARCHAR(256) NULL,
    trial_last_check  DATE         NULL,
    trial_fail_count  INT          NOT NULL DEFAULT 0
        COMMENT 'tamper failures charged to THIS machine - one machine can no longer block another',
    trial_fail_last   DATE         NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB;

-- The existing install's own trial, moved across so it keeps its installation date
-- and does not look like a machine that has never run.
--
-- Written through information_schema and PREPARE because the source columns are not
-- part of any migration: a database that has only ever run this build has no
-- company.trial_machine to select, and a plain INSERT ... SELECT would fail on it.
SET @has_trial_columns := (SELECT COUNT(*)
                           FROM information_schema.columns
                           WHERE table_schema = DATABASE()
                             AND table_name = 'company'
                             AND column_name IN ('trial_machine', 'installation_date'));

SET @backfill_sql := IF(@has_trial_columns = 2,
    'INSERT IGNORE INTO trial_machine_state
         (machine_id, installation_date, trial_hash, trial_last_check, trial_fail_count, trial_fail_last)
     SELECT trial_machine, installation_date, trial_hash, trial_last_check,
            COALESCE(trial_fail_count, 0), trial_fail_last
     FROM company
     WHERE trial_machine IS NOT NULL
       AND trial_machine <> ''''
       AND installation_date IS NOT NULL',
    'DO 0');
PREPARE backfill_statement FROM @backfill_sql;
EXECUTE backfill_statement;
DEALLOCATE PREPARE backfill_statement;

-- A machine that had already been locked out by the shared counter is unlocked by the
-- move: the count it carries is its own from here on, and the one that mattered was
-- charged to a row that no longer decides anything. Anything else would migrate the
-- defect along with the data.
UPDATE trial_machine_state
SET trial_fail_count = 0,
    trial_fail_last  = NULL
WHERE trial_fail_count > 0;
