-- A timestamp is useful to people, but it is not a safe sequence number. Two tills can
-- announce the same topic inside one clock tick, and server clock precision/configuration
-- must not decide whether another process notices a committed sale. Every upsert now
-- advances this counter; RemoteChangeRelay compares it instead of comparing wall clocks.

ALTER TABLE data_change
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 1 AFTER topic,
    MODIFY COLUMN changed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
