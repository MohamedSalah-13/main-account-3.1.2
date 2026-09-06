-- =====================================================================
-- V41 - Which computers are this shop's, and what each of them is running.
--
-- The database already knows who is *connected* - `information_schema.processlist`
-- answers that, and the restore guard reads it there rather than here, because a
-- till that was switched off mid-sale has no thread a second later while a row
-- would keep claiming otherwise. What the process list cannot say is which machine
-- an address belongs to, what version it is running, or who was signed in at it.
--
-- That is this table, and there are two questions in the shop that need it:
--
--   * "the backup ran, but where is the file?" - the owner is a machine id, and a
--     machine id on its own is a GUID nobody can place;
--   * "one of the tills was never updated" - which is the defect V38 and the
--     version gate in DatabaseMigrationService exist to survive, and this is where
--     it becomes visible before it bites.
--
-- One row per machine, rewritten by its own heartbeat. It is not a log: nothing
-- reads its history, and keeping one would grow without bound on a shop that
-- opens the program twenty times a day.
-- =====================================================================

CREATE TABLE IF NOT EXISTS workstation_session
(
    machine_id       VARCHAR(128) NOT NULL PRIMARY KEY
        COMMENT 'the Windows MachineGuid - the same identity the licence and the trial use',
    machine_name     VARCHAR(100) NULL
        COMMENT 'COMPUTERNAME, for a person to recognise; never used to tell machines apart',
    app_version      VARCHAR(50)  NULL,
    database_version VARCHAR(50)  NULL
        COMMENT 'the schema version this machine last migrated to - an old one here is a machine to update',
    last_user_id     INT          NULL,
    first_seen       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_workstation_session_user
        FOREIGN KEY (last_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB;
