-- A fresh V1 baseline temporarily seeds the historical admin/admin account.  Do not edit
-- V1: its checksum is already recorded in installed client databases.  This migration is
-- the safe forward-only replacement for both fresh and existing installations.
ALTER TABLE users
    ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0 AFTER user_pass;

-- The historical credential is replaced by bcrypt and must be changed before the main
-- application opens. The value below is a bcrypt hash of the legacy bootstrap password.
UPDATE users
SET user_pass = '$2a$12$02U/.egUJ.xfI0zTvdYTROBwVFqZMLstAfs/O4tBOJyl/n2xGwvqi',
    must_change_password = 1
WHERE id = 1
  AND user_pass = 'admin';
