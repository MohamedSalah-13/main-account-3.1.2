-- Immutable cash journal for shifts. Business rows may be edited or deleted, but
-- their effect on a shift is corrected by a new delta/reversal row here.
CREATE TABLE shift_cash_ledger (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    shift_id       INT           NOT NULL,
    treasury_id    INT           NOT NULL,
    actor_user_id  INT           NOT NULL,
    source_type    SMALLINT       NOT NULL,
    source_id      INT           NOT NULL,
    action_type    VARCHAR(16)    NOT NULL,
    movement_label VARCHAR(32)    NOT NULL,
    reason         VARCHAR(500)   NULL,
    income_delta   DECIMAL(19, 4) NOT NULL DEFAULT 0,
    output_delta   DECIMAL(19, 4) NOT NULL DEFAULT 0,
    occurred_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_shift_cash_ledger_action
        CHECK (action_type IN ('CREATE', 'UPDATE', 'DELETE')),
    CONSTRAINT chk_shift_cash_ledger_correction_reason
        CHECK (action_type = 'CREATE' OR CHAR_LENGTH(TRIM(COALESCE(reason, ''))) > 0),
    CONSTRAINT fk_shift_cash_ledger_shift
        FOREIGN KEY (shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_cash_ledger_treasury
        FOREIGN KEY (treasury_id) REFERENCES treasury(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_cash_ledger_actor
        FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_shift_cash_ledger_shift (shift_id, id),
    INDEX idx_shift_cash_ledger_source (source_type, source_id, treasury_id, id)
);

-- Defence in depth: application code deliberately has no mutating repository
-- methods, and the database refuses ad-hoc changes too. The existing controlled
-- bulk-wipe session flag is the sole deletion escape hatch.
DELIMITER |
CREATE TRIGGER prevent_shift_cash_ledger_update
BEFORE UPDATE ON shift_cash_ledger FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift cash journal is append-only';
END|

CREATE TRIGGER prevent_shift_cash_ledger_delete
BEFORE DELETE ON shift_cash_ledger FOR EACH ROW
BEGIN
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift cash journal is append-only';
    END IF;
END|
DELIMITER ;
