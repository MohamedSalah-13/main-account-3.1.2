-- Closing an accounting period (إغلاق فترة محاسبية).
--
-- Until now the nearest thing to this was a check in TotalsController: a user without
-- update_data_before_month could not open an invoice dated outside the current calendar
-- month. That is not a period close. It locks yesterday's invoice on the first of the
-- month whether or not anything was reported, it leaves everything in the current month
-- editable no matter what has been closed, it guards the button that opens an invoice
-- and not the save or the delete, and it is a rule nobody can see or set.
--
-- A close is a decision someone makes on a date, so it is stored as one.
--
-- The table is append-only: closing to a later date adds a row, and re-opening adds
-- another. The effective lock is the newest row, not the highest date, so moving the
-- line backwards is possible and - unlike editing a single value - leaves a record of
-- who moved it and when. That record is the whole point: a period that can be quietly
-- re-opened is not closed.

CREATE TABLE IF NOT EXISTS accounting_lock
(
    id           INT AUTO_INCREMENT PRIMARY KEY,
    -- Everything dated on or before this is closed. NULL is "nothing is closed",
    -- which is what re-opening writes.
    locked_until DATE                                NULL,
    notes        VARCHAR(255)                        NULL,
    date_insert  DATETIME  DEFAULT CURRENT_TIMESTAMP NOT NULL,
    user_id      INT       DEFAULT 1                 NOT NULL,
    CONSTRAINT accounting_lock_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Two permissions, and they are deliberately not the same one. Moving the line is an
-- owner's decision; being allowed to post into a closed period is an exception granted
-- to whoever has to correct something, and handing out the second should not hand out
-- the first. Ids continue from 89, the last one in UserPermissionType.
INSERT INTO permission (id, name_permission, description)
VALUES (90, 'accounting_lock_manage', 'إغلاق وفتح الفترات المحاسبية'),
       (91, 'accounting_lock_bypass', 'التعديل داخل فترة مغلقة')
ON DUPLICATE KEY UPDATE description = VALUES(description);
