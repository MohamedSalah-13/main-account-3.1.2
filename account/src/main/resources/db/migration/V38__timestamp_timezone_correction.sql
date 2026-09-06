-- Repairs the DATETIME values that were stored shifted by the machine's UTC offset.
--
-- The JDBC URL claimed serverTimezone=UTC over a MySQL that keeps local time. Under that
-- claim Connector/J converted in both directions, and the application wrote its timestamps
-- two different ways, so the two idioms ended up disagreeing about what a stored value means:
--
--   * MySQL wrote it (NOW(), DEFAULT CURRENT_TIMESTAMP) - stored correctly as local wall
--     clock, and read back too late by the whole offset. Nothing to repair; correcting the
--     URL fixes the read.
--   * Java wrote it as a LocalDateTime (setObject) - also stored correctly, same story.
--   * Java wrote it as setTimestamp(Timestamp.valueOf(...)) - the driver converted it to UTC
--     on the way in, so the value ON DISK is early by the offset. Reading it back with
--     getTimestamp() converted it a second time and cancelled the error, which is why these
--     columns looked right on screen while being wrong in the database, and why anything that
--     read them in SQL - a report, a BETWEEN, a view - saw the wrong hour.
--
-- Only that third group is repaired here: the seven columns below are every column the
-- application ever wrote with setTimestamp. Correcting the URL without this would make them
-- start reading back as the wrong hour they have always held.
--
-- The conversion is FROM_UNIXTIME(TO_SECONDS(v) - 62167219200), which is exact and needs no
-- timezone tables: TO_SECONDS is plain calendar arithmetic, and FROM_UNIXTIME renders an
-- instant using the server's own DST rules - so a row written in winter moves by two hours
-- and one written in summer by three. CONVERT_TZ is deliberately NOT used: it needs the
-- mysql.time_zone tables, which are not loaded on a default Windows MySQL, and it answers
-- NULL rather than failing when they are missing - which would have emptied these columns.
--
-- COALESCE keeps a value the conversion cannot express (FROM_UNIXTIME answers NULL outside
-- 1970..2038) rather than writing NULL into a NOT NULL column.
--
-- On a fresh install, and on any install that never switched shifts on, every statement here
-- matches no rows and the migration is a no-op.

-- user_shifts and stock_count are ordinary tables.
UPDATE user_shifts
SET open_time = COALESCE(FROM_UNIXTIME(TO_SECONDS(open_time) - 62167219200), open_time)
WHERE open_time IS NOT NULL;

UPDATE user_shifts
SET close_time = COALESCE(FROM_UNIXTIME(TO_SECONDS(close_time) - 62167219200), close_time)
WHERE close_time IS NOT NULL;

UPDATE stock_count
SET posted_at = COALESCE(FROM_UNIXTIME(TO_SECONDS(posted_at) - 62167219200), posted_at)
WHERE posted_at IS NOT NULL;

-- The next three tables are append-only: V27 and V28 put BEFORE UPDATE triggers on them that
-- SIGNAL unless @app_bulk_wipe is set. That flag is the schema's own documented escape hatch
-- (WipeService uses it the same way) and this is the case it is for - correcting a value the
-- rows were never meant to hold, not editing the history they record. It is cleared again
-- below so the connection cannot go back to the pool still carrying it.
SET @app_bulk_wipe = 1;

UPDATE shift_close_requests
SET requested_at = COALESCE(FROM_UNIXTIME(TO_SECONDS(requested_at) - 62167219200), requested_at)
WHERE requested_at IS NOT NULL;

UPDATE shift_close_decisions
SET decided_at = COALESCE(FROM_UNIXTIME(TO_SECONDS(decided_at) - 62167219200), decided_at)
WHERE decided_at IS NOT NULL;

UPDATE shift_close_snapshots
SET open_time = COALESCE(FROM_UNIXTIME(TO_SECONDS(open_time) - 62167219200), open_time),
    close_time = COALESCE(FROM_UNIXTIME(TO_SECONDS(close_time) - 62167219200), close_time)
WHERE open_time IS NOT NULL;

SET @app_bulk_wipe = NULL;
