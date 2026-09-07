-- =====================================================================
-- Repeatable - stored procedures.
--
-- Runs before R__triggers.sql (Flyway orders repeatables by description),
-- which matters because the audit triggers call write_audit_log.
-- Every procedure is preceded by DROP PROCEDURE IF EXISTS, so re-running
-- this file simply replaces the definitions.
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
    DECLARE v_source VARCHAR(50);
    DECLARE v_user_id INT;
    DECLARE v_actor_name VARCHAR(100);

    -- A wipe empties whole tables, and the DELETE triggers would copy every row it
    -- removes into audit_log - the database written into itself, inside the wipe's
    -- own transaction, only to be erased again if the log is one of the things
    -- being wiped. WipeService sets @app_bulk_wipe for the length of that work and
    -- clears it before the connection goes back to the pool. Nothing else sets it,
    -- so ordinary deletes are audited exactly as before.
    IF @app_bulk_wipe IS NULL THEN
        -- The application refreshes these variables whenever it borrows a pooled
        -- connection. A statement from a SQL client has no context and is deliberately
        -- identified as DATABASE instead of being attributed to user 1 or to the user
        -- stored on the changed business row.
        SET v_source = COALESCE(NULLIF(@app_audit_source, ''), 'DATABASE');
        IF v_source = 'APP' THEN
            SET v_user_id = @app_user_id;
            SET v_actor_name = NULLIF(@app_actor_name, '');
        ELSEIF v_source = 'SYSTEM' THEN
            SET v_user_id = NULL;
            SET v_actor_name = NULL;
        ELSE
            SET v_user_id = NULL;
            SET v_actor_name = CURRENT_USER();
        END IF;

        INSERT INTO audit_log
        (
            table_name,
            record_id,
            action_type,
            user_id,
            actor_name,
            old_data,
            new_data,
            source,
            workstation_id,
            workstation_name,
            notes
        )
        VALUES
            (
                UPPER(p_table_name),
                p_record_id,
                UPPER(p_action_type),
                v_user_id,
                v_actor_name,
                p_old_data,
                p_new_data,
                v_source,
                NULLIF(@app_machine_id, ''),
                NULLIF(@app_machine_name, ''),
                p_notes
            );
    END IF;
END;
|
DELIMITER ;

DROP PROCEDURE IF EXISTS write_audit_admin_event;

DELIMITER |
CREATE PROCEDURE write_audit_admin_event(
    IN p_event_type VARCHAR(40),
    IN p_reason VARCHAR(500),
    IN p_affected_rows BIGINT,
    IN p_details JSON
)
BEGIN
    DECLARE v_source VARCHAR(50);
    DECLARE v_user_id INT;
    DECLARE v_actor_name VARCHAR(100);

    SET v_source = COALESCE(NULLIF(@app_audit_source, ''), 'DATABASE');
    IF v_source = 'APP' THEN
        SET v_user_id = @app_user_id;
        SET v_actor_name = NULLIF(@app_actor_name, '');
    ELSEIF v_source = 'SYSTEM' THEN
        SET v_user_id = NULL;
        SET v_actor_name = NULL;
    ELSE
        SET v_user_id = NULL;
        SET v_actor_name = CURRENT_USER();
    END IF;

    INSERT INTO audit_admin_event
        (event_type, actor_user_id, actor_name, source, workstation_id,
         workstation_name, reason, affected_rows, details)
    VALUES
        (UPPER(p_event_type), v_user_id, v_actor_name, v_source,
         NULLIF(@app_machine_id, ''), NULLIF(@app_machine_name, ''),
         NULLIF(TRIM(p_reason), ''), GREATEST(COALESCE(p_affected_rows, 0), 0), p_details);
END;
|
DELIMITER ;

DROP PROCEDURE IF EXISTS max_item_id;

/*----------------------------------------------- max_id -----------------------------------------------*/
DELIMITER |
create
 procedure max_item_id(OUT itemId int)
begin
    SET itemId = (SELECT id
                  from items
                  ORDER BY id DESC
                  LIMIT 1);
end;
|
DELIMITER ;

-- #####################################################################
-- ## The truncate/reset procedures are gone.                         ##
-- ##                                                                 ##
-- ## truncateTableSales, truncateTablePurchase, truncateTableItems   ##
-- ## and truncateTableOthers took sixteen booleans between them and  ##
-- ## emptied whatever the flags said, in an order that only worked   ##
-- ## because each of them switched FOREIGN_KEY_CHECKS off first -    ##
-- ## a session setting, on a pooled connection, that a failure part  ##
-- ## way through left switched off for whoever borrowed it next.     ##
-- ##                                                                 ##
-- ## What they erased and what they put back is now declared in      ##
-- ## com.hamza.account.wipe.WipeCatalog and run by WipeService as    ##
-- ## ordinary DELETEs inside one transaction, with the foreign keys  ##
-- ## left on. The DROPs below clean them out of databases that ran   ##
-- ## an earlier version of this file; the helpers they called go     ##
-- ## with them.                                                      ##
-- #####################################################################

DROP PROCEDURE IF EXISTS truncateTableSales;
DROP PROCEDURE IF EXISTS truncateTablePurchase;
DROP PROCEDURE IF EXISTS truncateTableItems;
DROP PROCEDURE IF EXISTS truncateTableOthers;
DROP PROCEDURE IF EXISTS truncateAndInitializeItemsTables;
DROP PROCEDURE IF EXISTS truncateAndInitializeStocksTables;
DROP PROCEDURE IF EXISTS truncateAndInitializeSubGroupTable;
DROP PROCEDURE IF EXISTS truncateAndInitializeMainGroupTable;
