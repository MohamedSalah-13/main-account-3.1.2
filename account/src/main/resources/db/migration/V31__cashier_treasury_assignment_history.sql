-- Every change to cashier/till access is security-sensitive.  The current row
-- remains convenient for authorization, while this journal preserves who changed
-- what and the exact before/after state.
CREATE TABLE cashier_treasury_assignment_events (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id            INT          NOT NULL,
    user_id                  INT          NOT NULL,
    user_name_snapshot       VARCHAR(30)  NOT NULL,
    treasury_id              INT          NOT NULL,
    treasury_name_snapshot   VARCHAR(50)  NOT NULL,
    action_type              VARCHAR(20)  NOT NULL,
    before_can_open_shift    BOOLEAN      NULL,
    after_can_open_shift     BOOLEAN      NOT NULL,
    before_is_default        BOOLEAN      NULL,
    after_is_default         BOOLEAN      NOT NULL,
    before_active            BOOLEAN      NULL,
    after_active             BOOLEAN      NOT NULL,
    actor_user_id            INT          NOT NULL,
    actor_name_snapshot      VARCHAR(30)  NOT NULL,
    occurred_at              DATETIME     NOT NULL,
    -- Deliberately no foreign keys to the assignment, cashier, treasury or actor:
    -- deleting master data must not delete the evidence or make that delete
    -- impossible.  The ids and names are immutable historical snapshots.
    CONSTRAINT chk_cashier_assignment_event_action
        CHECK (action_type IN ('MIGRATED', 'ASSIGNED', 'REACTIVATED',
                              'DEFAULT_CHANGED', 'DEACTIVATED', 'UPDATED')),
    INDEX idx_cashier_assignment_event_assignment (assignment_id, id),
    INDEX idx_cashier_assignment_event_user (user_id, id),
    INDEX idx_cashier_assignment_event_time (occurred_at, id)
) ENGINE=InnoDB;

-- Existing rows receive an explicit migration event instead of appearing to have
-- no history.  Snapshot names keep the evidence readable after later renames.
INSERT INTO cashier_treasury_assignment_events (
    assignment_id, user_id, user_name_snapshot, treasury_id, treasury_name_snapshot,
    action_type, after_can_open_shift, after_is_default, after_active,
    actor_user_id, actor_name_snapshot, occurred_at)
SELECT a.id, a.user_id, u.user_name, a.treasury_id, t.t_name,
       'MIGRATED', a.can_open_shift, a.is_default, a.active,
       a.assigned_by, actor.user_name, a.assigned_at
FROM cashier_treasury_assignment a
JOIN users u ON u.id=a.user_id
JOIN treasury t ON t.id=a.treasury_id
JOIN users actor ON actor.id=a.assigned_by;

DELIMITER |
CREATE TRIGGER journal_cashier_treasury_assignment_insert
AFTER INSERT ON cashier_treasury_assignment FOR EACH ROW
BEGIN
    INSERT INTO cashier_treasury_assignment_events (
        assignment_id, user_id, user_name_snapshot, treasury_id, treasury_name_snapshot,
        action_type, after_can_open_shift, after_is_default, after_active,
        actor_user_id, actor_name_snapshot, occurred_at)
    SELECT NEW.id, NEW.user_id, u.user_name, NEW.treasury_id, t.t_name,
           'ASSIGNED', NEW.can_open_shift, NEW.is_default, NEW.active,
           NEW.assigned_by, actor.user_name, NEW.assigned_at
    FROM users u
    JOIN treasury t ON t.id=NEW.treasury_id
    JOIN users actor ON actor.id=NEW.assigned_by
    WHERE u.id=NEW.user_id;
END|

CREATE TRIGGER journal_cashier_treasury_assignment_update
AFTER UPDATE ON cashier_treasury_assignment FOR EACH ROW
BEGIN
    IF NOT (OLD.can_open_shift <=> NEW.can_open_shift)
       OR NOT (OLD.is_default <=> NEW.is_default)
       OR NOT (OLD.active <=> NEW.active) THEN
        INSERT INTO cashier_treasury_assignment_events (
            assignment_id, user_id, user_name_snapshot, treasury_id, treasury_name_snapshot,
            action_type, before_can_open_shift, after_can_open_shift,
            before_is_default, after_is_default, before_active, after_active,
            actor_user_id, actor_name_snapshot, occurred_at)
        SELECT NEW.id, NEW.user_id, u.user_name, NEW.treasury_id, t.t_name,
               CASE
                   WHEN OLD.active=TRUE AND NEW.active=FALSE THEN 'DEACTIVATED'
                   WHEN OLD.active=FALSE AND NEW.active=TRUE THEN 'REACTIVATED'
                   WHEN NOT (OLD.is_default <=> NEW.is_default) THEN 'DEFAULT_CHANGED'
                   ELSE 'UPDATED'
               END,
               OLD.can_open_shift, NEW.can_open_shift,
               OLD.is_default, NEW.is_default, OLD.active, NEW.active,
               NEW.updated_by, actor.user_name, NEW.updated_at
        FROM users u
        JOIN treasury t ON t.id=NEW.treasury_id
        JOIN users actor ON actor.id=NEW.updated_by
        WHERE u.id=NEW.user_id;
    END IF;
END|

CREATE TRIGGER prevent_cashier_assignment_event_update
BEFORE UPDATE ON cashier_treasury_assignment_events FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cashier treasury assignment history is immutable';
END|

CREATE TRIGGER prevent_cashier_assignment_event_delete
BEFORE DELETE ON cashier_treasury_assignment_events FOR EACH ROW
BEGIN
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cashier treasury assignment history is immutable';
    END IF;
END|
DELIMITER ;
