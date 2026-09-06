-- =====================================================================
-- V40 - Settings that are the shop's, kept where the shop can see them.
--
-- Every setting in this program lives in Java `Preferences`, which is a registry
-- key under the Windows profile of the person sitting at that computer. For a
-- printer, a window size or which warehouse the wall-mounted price screen answers
-- for, that is exactly right - those are facts about a machine.
--
-- For a business rule it is wrong, and quietly so. The scale-barcode layout is the
-- clearest case: a label printed by the shop's scale is read at whichever till the
-- customer walks up to, and the digits it means are a property of the scale, not of
-- the till. Set them on one machine and the other two read the same sticker as a
-- different weight, or as no scale barcode at all. The same goes for whether a
-- return needs its original invoice, and whether an item may be sold below zero:
-- those are decisions a business makes once, and a per-machine copy of them is a
-- policy that disagrees with itself.
--
-- So: one row per shared key, and `SharedSettingKeys` in the Java is the list of
-- which keys those are. Everything not on that list stays in Preferences, where it
-- belongs.
--
-- The table is deliberately dumb - a key and a string. Types are the reader's
-- problem, exactly as they are with Preferences, so moving a key from one store to
-- the other is a line in a list rather than a schema change.
-- =====================================================================

CREATE TABLE IF NOT EXISTS app_setting
(
    setting_key   VARCHAR(120)  NOT NULL PRIMARY KEY
        COMMENT 'the same key Preferences used, so a value can move between the two stores unchanged',
    setting_value VARCHAR(1000) NULL,
    updated_by    INT           NULL,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_app_setting_updated_by
        FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB;
