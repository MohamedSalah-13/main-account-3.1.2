-- Reconcile the till's book balance to the cashier's physical count without
-- rewriting the immutable close snapshot. The linked cash movement is protected
-- by this row's RESTRICT foreign key and the row itself is append-only.
CREATE TABLE shift_cash_variance_adjustments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    shift_id            INT           NOT NULL,
    treasury_id         INT           NOT NULL,
    expected_balance    DECIMAL(19,4) NOT NULL,
    actual_balance      DECIMAL(19,4) NOT NULL,
    difference_amount   DECIMAL(19,4) NOT NULL,
    cash_movement_id    INT           NOT NULL,
    adjusted_by_user_id INT           NOT NULL,
    adjusted_at         DATETIME      NOT NULL,
    CONSTRAINT uq_shift_cash_variance_shift UNIQUE (shift_id),
    CONSTRAINT uq_shift_cash_variance_movement UNIQUE (cash_movement_id),
    CONSTRAINT fk_shift_cash_variance_shift
        FOREIGN KEY (shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_cash_variance_treasury
        FOREIGN KEY (treasury_id) REFERENCES treasury(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_cash_variance_movement
        FOREIGN KEY (cash_movement_id) REFERENCES treasury_deposit_expenses(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_cash_variance_actor
        FOREIGN KEY (adjusted_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_shift_cash_variance_amount
        CHECK (actual_balance >= 0 AND difference_amount <> 0
               AND difference_amount = actual_balance - expected_balance)
) ENGINE=InnoDB;

-- A supervisor may explicitly accept the operational risk of opening the till
-- before its previous cash handover is received. The approval is immutable and
-- tied to that exact handover, so it cannot silently authorize later handovers.
CREATE TABLE shift_cash_handover_open_overrides (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    handover_id         BIGINT       NOT NULL,
    approved_by_user_id INT          NOT NULL,
    approval_reason     VARCHAR(500) NOT NULL,
    approved_at         DATETIME     NOT NULL,
    CONSTRAINT uq_shift_cash_handover_open_override UNIQUE (handover_id),
    CONSTRAINT fk_shift_handover_override_request
        FOREIGN KEY (handover_id) REFERENCES shift_cash_handovers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_handover_override_actor
        FOREIGN KEY (approved_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_shift_handover_override_reason
        CHECK (CHAR_LENGTH(TRIM(approval_reason)) > 0)
) ENGINE=InnoDB;
