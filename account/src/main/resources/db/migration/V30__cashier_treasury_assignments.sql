-- Cashier-to-till access is optional during rollout.  Businesses enable strict
-- enforcement only after assigning their cashiers, so an upgrade cannot lock
-- every existing user out of an already-required shift workflow.
-- MySQL DDL is not transactional.  Keep this first step retryable because a later
-- CREATE TABLE failure would otherwise leave the column behind and block Flyway's
-- next attempt with "duplicate column".
SET @enforcement_column := (SELECT COUNT(*)
                            FROM information_schema.columns
                            WHERE table_schema = DATABASE()
                              AND table_name = 'shift_policy'
                              AND column_name = 'enforce_treasury_assignments');
SET @enforcement_sql := IF(@enforcement_column = 0,
    'ALTER TABLE shift_policy ADD COLUMN enforce_treasury_assignments BOOLEAN NOT NULL DEFAULT FALSE AFTER require_supervisor_approval',
    'DO 0');
PREPARE enforcement_statement FROM @enforcement_sql;
EXECUTE enforcement_statement;
DEALLOCATE PREPARE enforcement_statement;

CREATE TABLE cashier_treasury_assignment (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT       NOT NULL,
    treasury_id     INT       NOT NULL,
    can_open_shift  BOOLEAN   NOT NULL DEFAULT TRUE,
    is_default      BOOLEAN   NOT NULL DEFAULT FALSE,
    active          BOOLEAN   NOT NULL DEFAULT TRUE,
    assigned_by     INT       NOT NULL,
    assigned_at     DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      INT       NOT NULL,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    default_user_id INT GENERATED ALWAYS AS (
        CASE WHEN active = TRUE AND is_default = TRUE THEN user_id ELSE NULL END
    ) STORED,
    CONSTRAINT uq_cashier_treasury_assignment UNIQUE (user_id, treasury_id),
    CONSTRAINT uq_cashier_default_treasury UNIQUE (default_user_id),
    CONSTRAINT fk_cashier_treasury_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cashier_treasury_treasury
        FOREIGN KEY (treasury_id) REFERENCES treasury(id) ON DELETE CASCADE,
    CONSTRAINT fk_cashier_treasury_assigned_by
        FOREIGN KEY (assigned_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cashier_treasury_updated_by
        FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_cashier_treasury_open CHECK (can_open_shift IN (0, 1)),
    CONSTRAINT chk_cashier_treasury_default CHECK (is_default IN (0, 1)),
    CONSTRAINT chk_cashier_treasury_active CHECK (active IN (0, 1)),
    INDEX idx_cashier_treasury_access (user_id, active, can_open_shift, treasury_id)
) ENGINE=InnoDB;
