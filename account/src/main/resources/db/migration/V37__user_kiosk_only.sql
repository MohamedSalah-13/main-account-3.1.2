-- An account that exists to run the price-check screen on a device hanging in the shop,
-- and nothing else: signing in with it goes straight to that screen and the main window is
-- never built. Not merely hidden - a user holding only 'items.price.check' still lands on
-- the main screen, whose sidebar is deliberately disabled rather than hidden, and whose home
-- screen can carry the day's totals. Both would be on a wall in front of customers.
--
-- Deliberately a column and not a permission key. A permission grants an ability; this one
-- withholds every other, and the permission system cannot express that:
-- JdbcRbacRepository.synchronizeCatalog grants every new key to SYSTEM_ADMIN on startup, and
-- UserSessionContext.isSystemAdministrator() answers true to every key for user 1 - so a key
-- would have locked the administrator into the kiosk with no way back.
ALTER TABLE users
    ADD COLUMN kiosk_only TINYINT DEFAULT 0 NOT NULL
        COMMENT 'Signing in opens the price-check screen alone; the main window is never built';

ALTER TABLE users
    ADD CONSTRAINT users_kiosk_only_chk CHECK (kiosk_only IN (0, 1));

-- User 1 is the way back onto a device someone has flagged by mistake, so it is never a
-- kiosk account. KioskRouting refuses to route it there whatever this column says; this is
-- the same rule written where the data is.
UPDATE users SET kiosk_only = 0 WHERE id = 1;
