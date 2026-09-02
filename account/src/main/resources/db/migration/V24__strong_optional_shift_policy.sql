-- Shifts are opt-in. Existing warehouse/company installations remain unchanged.
CREATE TABLE IF NOT EXISTS shift_policy (
    id TINYINT NOT NULL PRIMARY KEY,
    mode VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
    blind_close BOOLEAN NOT NULL DEFAULT FALSE,
    auto_print_z BOOLEAN NOT NULL DEFAULT TRUE,
    variance_tolerance DECIMAL(19, 4) NOT NULL DEFAULT 0,
    require_variance_reason BOOLEAN NOT NULL DEFAULT TRUE,
    require_supervisor_approval BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_shift_policy_singleton CHECK (id = 1),
    CONSTRAINT chk_shift_policy_mode CHECK (mode IN ('DISABLED', 'OPTIONAL', 'REQUIRED')),
    CONSTRAINT chk_shift_variance_tolerance CHECK (variance_tolerance >= 0)
) ENGINE=InnoDB;

INSERT IGNORE INTO shift_policy(id, mode) VALUES (1, 'DISABLED');

CREATE TABLE IF NOT EXISTS shift_treasury_policy (
    treasury_id INT NOT NULL PRIMARY KEY,
    tracking_mode VARCHAR(16) NOT NULL DEFAULT 'NONE',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_shift_treasury_policy_treasury
        FOREIGN KEY (treasury_id) REFERENCES treasury(id) ON DELETE CASCADE,
    CONSTRAINT chk_shift_treasury_tracking_mode
        CHECK (tracking_mode IN ('NONE', 'TRACK_ONLY', 'RECONCILE'))
) ENGINE=InnoDB;

ALTER TABLE user_shifts
    ADD COLUMN shift_status VARCHAR(20) NOT NULL DEFAULT 'OPEN' AFTER is_open;

UPDATE user_shifts SET shift_status = IF(is_open, 'OPEN', 'CLOSED');

-- Existing self-service remains available after upgrade. No business role is assigned
-- automatically; the catalog sync grants all enabled permissions only to SYSTEM_ADMIN.
