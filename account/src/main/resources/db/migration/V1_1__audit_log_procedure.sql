-- =====================================================================
-- V1.1 - the audit log procedure, as a versioned migration.
--
-- `write_audit_log` was defined only in R__procedures.sql. Flyway runs every
-- repeatable migration *after* every versioned one, so on a brand new database
-- the procedure did not exist for the whole of V1..V21 - while V1 and V2 create
-- the audit triggers that call it. Nothing noticed for nineteen migrations
-- because none of them wrote to an audited table. V20 does:
--
--     UPDATE treasury SET opening_date = DATE(date_insert) WHERE opening_date IS NULL
--
-- and a fresh install died there with "PROCEDURE write_audit_log does not
-- exist", leaving the schema half-built. Every existing client was fine - they
-- are stamped at V1 and already hold the procedure from the manual script
-- bundle - so this only ever broke the one case nobody runs twice: a first
-- install.
--
-- It is numbered 1.1 rather than 22 because 22 would run after V20 and fix
-- nothing. The procedure is a prerequisite of the triggers V1 itself creates,
-- so it belongs immediately after the baseline. A client already past it sees a
-- pending migration below its current version and ignores it, which is correct:
-- it has the procedure.
--
-- The definition stays in R__procedures.sql as well, and that copy remains the
-- one to edit. This file is a historical snapshot - what a database needed at
-- version 1.1 - and the repeatable, running afterwards, always has the last
-- word. That is the ordinary relationship between the two kinds of migration,
-- and it is why the duplication is not a fork.
--
-- Obvious in hindsight: anything a *versioned* migration can reach must be
-- created by a versioned migration. A repeatable cannot be a prerequisite of
-- something that runs before it. AuditProcedureMigrationTest pins that.
-- =====================================================================

DROP PROCEDURE IF EXISTS write_audit_log;

DELIMITER |
CREATE PROCEDURE write_audit_log(
    IN p_table_name VARCHAR(100),
    IN p_record_id VARCHAR(100),
    IN p_action_type VARCHAR(20),
    IN p_user_id INT,
    IN p_old_data JSON,
    IN p_new_data JSON,
    IN p_notes TEXT
)
BEGIN
    -- A wipe empties whole tables, and the DELETE triggers would copy every row it
    -- removes into audit_log - the database written into itself, inside the wipe's
    -- own transaction, only to be erased again if the log is one of the things
    -- being wiped. WipeService sets @app_bulk_wipe for the length of that work and
    -- clears it before the connection goes back to the pool. Nothing else sets it,
    -- so ordinary deletes are audited exactly as before.
    IF @app_bulk_wipe IS NULL THEN
        INSERT INTO audit_log
        (
            table_name,
            record_id,
            action_type,
            user_id,
            old_data,
            new_data,
            notes
        )
        VALUES
            (
                UPPER(p_table_name),
                p_record_id,
                UPPER(p_action_type),
                p_user_id,
                p_old_data,
                p_new_data,
                p_notes
            );
    END IF;
END;
|
DELIMITER ;
