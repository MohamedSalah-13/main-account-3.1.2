-- A correction is posted to the currently open shift, while this link preserves
-- the closed shift/document history it corrects.
ALTER TABLE shift_cash_ledger
    ADD COLUMN origin_shift_id INT NULL AFTER shift_id,
    ADD CONSTRAINT fk_shift_cash_ledger_origin_shift
        FOREIGN KEY (origin_shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT,
    ADD INDEX idx_shift_cash_ledger_origin (origin_shift_id, id);

-- Existing correction rows predate the explicit origin link. Their own shift is
-- the safest historical attribution available.
UPDATE shift_cash_ledger
SET origin_shift_id = shift_id
WHERE action_type IN ('UPDATE', 'DELETE') AND origin_shift_id IS NULL;

-- Immutable Z-report facts. user_shifts remains the operational row; this table is
-- the accounting snapshot and the ledger watermark says exactly which journal rows
-- were included when the drawer was closed.
CREATE TABLE shift_close_snapshots (
    shift_id          INT PRIMARY KEY,
    closed_by_user_id INT           NOT NULL,
    shift_status      VARCHAR(20)   NOT NULL,
    open_time         DATETIME      NOT NULL,
    close_time        DATETIME      NOT NULL,
    open_balance      DECIMAL(19,4) NOT NULL,
    actual_balance    DECIMAL(19,4) NOT NULL,
    expected_balance  DECIMAL(19,4) NOT NULL,
    difference_amount DECIMAL(19,4) NOT NULL,
    total_sales       DECIMAL(19,4) NOT NULL,
    total_sales_returns DECIMAL(19,4) NOT NULL,
    total_expenses    DECIMAL(19,4) NOT NULL,
    total_deposits    DECIMAL(19,4) NOT NULL,
    total_withdrawals DECIMAL(19,4) NOT NULL,
    total_cash_in     DECIMAL(19,4) NOT NULL,
    total_cash_out    DECIMAL(19,4) NOT NULL,
    invoices_count    INT           NOT NULL,
    ledger_last_id    BIGINT        NOT NULL DEFAULT 0,
    ledger_complete   BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_shift_snapshot_status CHECK (shift_status IN ('CLOSED', 'FORCE_CLOSED')),
    CONSTRAINT fk_shift_snapshot_shift
        FOREIGN KEY (shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_snapshot_actor
        FOREIGN KEY (closed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- Upgrade snapshots use the already persisted close totals. They are explicitly
-- marked incomplete because the append-only ledger did not exist for their lifetime.
INSERT IGNORE INTO shift_close_snapshots (
    shift_id, closed_by_user_id, shift_status, open_time, close_time,
    open_balance, actual_balance, expected_balance, difference_amount,
    total_sales, total_sales_returns, total_expenses, total_deposits,
    total_withdrawals, total_cash_in, total_cash_out, invoices_count,
    ledger_last_id, ledger_complete)
SELECT us.id, us.user_id, us.shift_status, us.open_time, us.close_time,
       COALESCE(us.open_balance,0), COALESCE(us.close_balance,0),
       COALESCE(us.expected_balance,0), COALESCE(us.difference,0),
       COALESCE(us.total_sales,0), COALESCE(us.total_sales_returns,0),
       COALESCE(us.total_expenses,0), COALESCE(us.total_deposits,0),
       COALESCE(us.total_withdrawals,0), COALESCE(us.total_cash_in,0),
       COALESCE(us.total_cash_out,0), COALESCE(us.invoices_count,0),
       COALESCE((SELECT MAX(l.id) FROM shift_cash_ledger l WHERE l.shift_id=us.id),0), FALSE
FROM user_shifts us
WHERE us.is_open=FALSE AND us.close_time IS NOT NULL;

DELIMITER |
CREATE TRIGGER prevent_shift_close_snapshot_update
BEFORE UPDATE ON shift_close_snapshots FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift close snapshot is immutable';
END|

CREATE TRIGGER prevent_shift_close_snapshot_delete
BEFORE DELETE ON shift_close_snapshots FOR EACH ROW
BEGIN
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift close snapshot is immutable';
    END IF;
END|
DELIMITER ;
