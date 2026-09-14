-- Operational close remains possible after its accounting date is locked. The
-- variance is not back-dated; this immutable request waits for an open period.
CREATE TABLE shift_variance_settlement_requests (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    shift_id              INT           NOT NULL,
    treasury_id           INT           NOT NULL,
    expected_balance      DECIMAL(19,4) NOT NULL,
    actual_balance        DECIMAL(19,4) NOT NULL,
    difference_amount     DECIMAL(19,4) NOT NULL,
    original_shift_date   DATE          NOT NULL,
    requested_by_user_id  INT           NOT NULL,
    requested_at          DATETIME      NOT NULL,
    CONSTRAINT uq_shift_variance_settlement_shift UNIQUE (shift_id),
    CONSTRAINT fk_shift_variance_settlement_shift FOREIGN KEY (shift_id)
        REFERENCES user_shifts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_variance_settlement_treasury FOREIGN KEY (treasury_id)
        REFERENCES treasury(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_variance_settlement_actor FOREIGN KEY (requested_by_user_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_shift_variance_settlement_amount
        CHECK (actual_balance >= 0 AND difference_amount <> 0
               AND difference_amount = actual_balance - expected_balance),
    INDEX idx_shift_variance_settlement_pending (requested_at, id)
) ENGINE=InnoDB;

-- Charging an investigated shortage is an explicit supervisor decision. The
-- employee ledger remains the accounting source; this row is its immutable link.
CREATE TABLE shift_employee_shortage_charges (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    shift_id              INT           NOT NULL,
    employee_id           INT           NOT NULL,
    employee_ledger_id    INT           NOT NULL,
    amount                DECIMAL(14,2) NOT NULL,
    approval_reason       VARCHAR(255)  NOT NULL,
    approved_by_user_id   INT           NOT NULL,
    approved_at           DATETIME      NOT NULL,
    CONSTRAINT uq_shift_employee_shortage_shift UNIQUE (shift_id),
    CONSTRAINT uq_shift_employee_shortage_ledger UNIQUE (employee_ledger_id),
    CONSTRAINT fk_shift_employee_shortage_shift FOREIGN KEY (shift_id)
        REFERENCES user_shifts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_employee_shortage_employee FOREIGN KEY (employee_id)
        REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_employee_shortage_ledger FOREIGN KEY (employee_ledger_id)
        REFERENCES employee_ledger(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_employee_shortage_actor FOREIGN KEY (approved_by_user_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_shift_employee_shortage_amount CHECK (amount > 0),
    CONSTRAINT chk_shift_employee_shortage_reason CHECK (CHAR_LENGTH(TRIM(approval_reason)) > 0)
) ENGINE=InnoDB;
