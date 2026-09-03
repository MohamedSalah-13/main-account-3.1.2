-- A variance close that requires supervision is a two-person workflow. The
-- request and its eventual decision are separate immutable facts.
CREATE TABLE shift_close_requests (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    shift_id            INT           NOT NULL,
    requested_by_user_id INT          NOT NULL,
    requested_at        DATETIME      NOT NULL,
    actual_balance      DECIMAL(19,4) NOT NULL,
    expected_balance    DECIMAL(19,4) NOT NULL,
    difference_amount   DECIMAL(19,4) NOT NULL,
    total_sales         DECIMAL(19,4) NOT NULL,
    total_sales_returns DECIMAL(19,4) NOT NULL,
    total_expenses      DECIMAL(19,4) NOT NULL,
    total_deposits      DECIMAL(19,4) NOT NULL,
    total_withdrawals   DECIMAL(19,4) NOT NULL,
    total_cash_in       DECIMAL(19,4) NOT NULL,
    total_cash_out      DECIMAL(19,4) NOT NULL,
    invoices_count      INT           NOT NULL,
    ledger_last_id      BIGINT        NOT NULL DEFAULT 0,
    reason              VARCHAR(500)  NOT NULL,
    CONSTRAINT fk_shift_close_request_shift
        FOREIGN KEY (shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_close_request_user
        FOREIGN KEY (requested_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_shift_close_request_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0),
    INDEX idx_shift_close_request_shift (shift_id, id),
    INDEX idx_shift_close_request_time (requested_at, id)
) ENGINE=InnoDB;

CREATE TABLE shift_close_decisions (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id         BIGINT       NOT NULL,
    decided_by_user_id INT          NOT NULL,
    decision_type      VARCHAR(16)  NOT NULL,
    decision_note      VARCHAR(500) NULL,
    decided_at         DATETIME     NOT NULL,
    CONSTRAINT uq_shift_close_decision UNIQUE (request_id),
    CONSTRAINT fk_shift_close_decision_request
        FOREIGN KEY (request_id) REFERENCES shift_close_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_close_decision_user
        FOREIGN KEY (decided_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_shift_close_decision_type
        CHECK (decision_type IN ('APPROVED', 'REJECTED', 'CANCELLED'))
) ENGINE=InnoDB;

DELIMITER |
CREATE TRIGGER validate_shift_close_decision_actor
BEFORE INSERT ON shift_close_decisions FOR EACH ROW
BEGIN
    IF NEW.decision_type <> 'CANCELLED' AND NEW.decided_by_user_id =
       (SELECT requested_by_user_id FROM shift_close_requests WHERE id=NEW.request_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A second user must decide the close request';
    END IF;
END|

CREATE TRIGGER prevent_shift_close_request_update
BEFORE UPDATE ON shift_close_requests FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift close request is immutable';
END|

CREATE TRIGGER prevent_shift_close_request_delete
BEFORE DELETE ON shift_close_requests FOR EACH ROW
BEGIN
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift close request is immutable';
    END IF;
END|

CREATE TRIGGER prevent_shift_close_decision_update
BEFORE UPDATE ON shift_close_decisions FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift close decision is immutable';
END|

CREATE TRIGGER prevent_shift_close_decision_delete
BEFORE DELETE ON shift_close_decisions FOR EACH ROW
BEGIN
    IF COALESCE(@app_bulk_wipe, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Shift close decision is immutable';
    END IF;
END|
DELIMITER ;
