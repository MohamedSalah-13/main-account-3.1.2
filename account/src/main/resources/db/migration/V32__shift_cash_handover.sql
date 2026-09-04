-- Optional per-till handover policy.  A warehouse/company installation keeps no
-- rows here and its shift behaviour remains unchanged.
CREATE TABLE shift_cash_handover_policy (
    source_treasury_id INT PRIMARY KEY,
    enabled            BOOLEAN       NOT NULL DEFAULT FALSE,
    target_treasury_id INT           NOT NULL,
    retained_float     DECIMAL(19,4) NOT NULL DEFAULT 0,
    updated_by_user_id INT           NOT NULL,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_shift_handover_policy_source
        FOREIGN KEY (source_treasury_id) REFERENCES treasury(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_handover_policy_target
        FOREIGN KEY (target_treasury_id) REFERENCES treasury(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_handover_policy_actor
        FOREIGN KEY (updated_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_shift_handover_policy_different
        CHECK (source_treasury_id <> target_treasury_id),
    CONSTRAINT chk_shift_handover_policy_float CHECK (retained_float >= 0)
) ENGINE=InnoDB;

-- The request is the cashier's declaration at close.  It never changes; receipt
-- is a second immutable fact so pending state is derived rather than overwritten.
CREATE TABLE shift_cash_handovers (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    shift_id           INT           NOT NULL,
    source_treasury_id INT           NOT NULL,
    target_treasury_id INT           NOT NULL,
    actual_balance     DECIMAL(19,4) NOT NULL,
    retained_float     DECIMAL(19,4) NOT NULL,
    handover_amount    DECIMAL(19,4) NOT NULL,
    handed_by_user_id  INT           NOT NULL,
    requested_at       DATETIME      NOT NULL,
    CONSTRAINT uq_shift_cash_handover_shift UNIQUE (shift_id),
    CONSTRAINT fk_shift_cash_handover_shift
        FOREIGN KEY (shift_id) REFERENCES user_shifts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_cash_handover_source
        FOREIGN KEY (source_treasury_id) REFERENCES treasury(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_cash_handover_target
        FOREIGN KEY (target_treasury_id) REFERENCES treasury(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_cash_handover_cashier
        FOREIGN KEY (handed_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_shift_cash_handover_different
        CHECK (source_treasury_id <> target_treasury_id),
    CONSTRAINT chk_shift_cash_handover_amounts
        CHECK (actual_balance >= 0 AND retained_float >= 0 AND handover_amount > 0
               AND handover_amount = GREATEST(actual_balance - retained_float, 0)),
    INDEX idx_shift_cash_handover_pending (requested_at, id)
) ENGINE=InnoDB;

CREATE TABLE shift_cash_handover_receipts (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    handover_id         BIGINT       NOT NULL,
    received_by_user_id INT          NOT NULL,
    received_at         DATETIME     NOT NULL,
    treasury_transfer_id INT         NOT NULL,
    receipt_note        VARCHAR(500) NULL,
    CONSTRAINT uq_shift_cash_handover_receipt UNIQUE (handover_id),
    CONSTRAINT uq_shift_cash_handover_transfer UNIQUE (treasury_transfer_id),
    CONSTRAINT fk_shift_cash_handover_receipt_request
        FOREIGN KEY (handover_id) REFERENCES shift_cash_handovers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_cash_handover_receipt_user
        FOREIGN KEY (received_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_shift_cash_handover_receipt_transfer
        FOREIGN KEY (treasury_transfer_id) REFERENCES treasury_transfers(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
