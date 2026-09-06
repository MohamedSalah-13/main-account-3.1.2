-- =====================================================================
-- V42 - "Something changed over here", so the other tills can catch up.
--
-- Every list screen in this program already refreshes itself when the data
-- behind it changes: `EventBus` carries an `ItemsChanged`, and whichever screens
-- are open reload. That has always been in-process, which was the whole truth
-- when there was one process.
--
-- With three tills it is a third of the truth. A price changed on the manager's
-- computer reaches the two cashiers when they next reopen the screen, and until
-- then they are selling from a list that is quietly out of date.
--
-- One row per topic, holding when it last changed and which machine changed it.
-- Not a queue and not a log: a machine that was switched off for an hour does not
-- want the hour's worth of "the items changed" notices, it wants to know that they
-- did. Which is one row.
--
-- The topics themselves are declared in `RemoteChangeTopics`, and only events that
-- carry no data a listener needs are relayed - see that class for why an event
-- carrying an item cannot be.
-- =====================================================================

CREATE TABLE IF NOT EXISTS data_change
(
    topic      VARCHAR(60)  NOT NULL PRIMARY KEY
        COMMENT 'declared in RemoteChangeTopics; an unknown topic is ignored rather than guessed at',
    changed_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        COMMENT 'milliseconds, because two changes in one second are ordinary at a till',
    changed_by VARCHAR(128) NULL
        COMMENT 'the machine that made the change, so it does not act on its own announcement'
) ENGINE = InnoDB;
