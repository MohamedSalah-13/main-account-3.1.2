-- One signed product edition per client database. Every workstation reads the same
-- envelope, and the application verifies it before trusting any feature flag.
CREATE TABLE product_profile
(
    profile_id       TINYINT      NOT NULL PRIMARY KEY,
    signed_envelope  LONGTEXT     NOT NULL,
    profile_version  INT          NOT NULL,
    customer_name    VARCHAR(200) NOT NULL,
    profile_name     VARCHAR(120) NOT NULL,
    issued_at        VARCHAR(40)  NOT NULL,
    applied_by       VARCHAR(160) NOT NULL,
    applied_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_product_profile_singleton CHECK (profile_id = 1)
) ENGINE = InnoDB;

-- Applying a new edition never destroys the evidence of what was installed before it.
CREATE TABLE product_profile_history
(
    history_id       BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    signed_envelope  LONGTEXT     NOT NULL,
    profile_version  INT          NOT NULL,
    customer_name    VARCHAR(200) NOT NULL,
    profile_name     VARCHAR(120) NOT NULL,
    issued_at        VARCHAR(40)  NOT NULL,
    applied_by       VARCHAR(160) NOT NULL,
    applied_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product_profile_history_applied_at (applied_at)
) ENGINE = InnoDB;
