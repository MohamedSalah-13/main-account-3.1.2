-- Emergency recovery of the administrator account, for the case where nobody can sign in.
--
-- There is deliberately no shared support key here. One was tried: its bcrypt hash was
-- seeded into app_setting, which meant it shipped inside every copy of this program and of
-- this repository. The attempt limit below never touched the attack that mattered - the
-- hash is worked on offline, unlimited and unlogged, and the right key then arrives on the
-- first try - and one key served every installation, so breaking it once opened the
-- administrator account everywhere.
--
-- What replaced it: the machine issues a challenge, and only a response signed by the
-- private key that issues licences is accepted. This repository has never held that key,
-- so there is nothing here to work on offline. See docs/users-and-recovery-plan.md §6.

-- Every attempt, including the refused ones. Recording only the successes watches the half
-- that needs no watching: a key is guessed by failing, and a failure that is neither
-- counted nor kept is a run of them nobody can see afterwards.
CREATE TABLE IF NOT EXISTS support_recovery_audit
(
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    target_user_id INT          NOT NULL,
    machine_name   VARCHAR(255) NOT NULL,
    outcome        VARCHAR(16)  NOT NULL DEFAULT 'SUCCEEDED',
    recovered_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_support_recovery_audit_user
        FOREIGN KEY (target_user_id) REFERENCES users (id),
    CONSTRAINT support_recovery_outcome_chk
        CHECK (outcome IN ('SUCCEEDED', 'FAILED', 'BLOCKED'))
) ENGINE = InnoDB;

-- The count is always "failures on this machine in the last few minutes".
CREATE INDEX support_recovery_audit_outcome_idx
    ON support_recovery_audit (outcome, recovered_at);

-- One challenge, answerable once, by one machine, for half an hour.
CREATE TABLE IF NOT EXISTS support_recovery_challenge
(
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nonce       VARCHAR(32)  NOT NULL,
    machine_id  VARCHAR(255) NOT NULL,
    issued_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Set the moment a response is accepted. Without it the same signature reopens the
    -- administrator account for as long as anyone keeps a copy of it.
    redeemed_at TIMESTAMP    NULL,
    CONSTRAINT support_recovery_challenge_nonce_uk UNIQUE (nonce)
) ENGINE = InnoDB;

-- Redeeming looks a challenge up by its nonce; expiry is asked of the issue time.
CREATE INDEX support_recovery_challenge_issued_idx
    ON support_recovery_challenge (issued_at);

-- Only a development database that ran the first draft of this file has such a row - it was
-- never shipped. Kept for the same reason R__views.sql keeps the DROP for a view it no
-- longer creates: the statement costs nothing where the row was never written, and leaving
-- a live shared secret behind on the one machine that has it costs a great deal.
DELETE FROM app_setting WHERE setting_key = 'support.recovery.key.hash';
