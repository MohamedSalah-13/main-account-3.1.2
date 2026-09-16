-- =====================================================================
-- V63 - One collation for the three columns a barcode can live in.
--
-- A code can be `items.barcode`, a row in `item_barcodes`, or the code on a
-- unit in `items_units`, and `ItemsDao.takenBarcodesAmong` asks all three in
-- one statement - a UNION of the three columns. MySQL refuses a UNION of two
-- columns whose collations differ (error 1271, "Illegal mix of collations"),
-- so on a database where they do, saving an item and generating a free code
-- both fail outright.
--
-- They differ because no migration here has ever named a charset: a table
-- created by `CREATE TABLE` with no `CHARACTER SET` clause takes the
-- *database* default, while a table restored from a mysqldump carries the
-- `DEFAULT CHARSET=utf8mb4` written in the dump and so lands on the *server's*
-- default collation for that charset. We create a database with
-- `COLLATE utf8mb4_unicode_ci` (`DatabaseMigrationService.createDatabaseIfMissing`),
-- and MySQL 8's default for utf8mb4 is `utf8mb4_0900_ai_ci` - so an install
-- whose data was restored into a database we created has tables in one
-- collation and anything a later migration created in another. Measured on a
-- customer database on 2026-09-16: 90 tables `utf8mb4_0900_ai_ci`, and
-- `item_barcodes` - created by V3 - alone in `utf8mb4_unicode_ci`.
--
-- `items.barcode` is the reference: it is the item's own code, the column the
-- other two are compared against, and the two satellites are converted to match
-- it. Every statement is a no-op where nothing differs, and the target collation
-- is read out of this database rather than named, so a server that has never
-- heard of `utf8mb4_0900_ai_ci` is given a collation it does have.
--
-- A column is converted only when it is already in the same *character set* and
-- differs in collation alone, which is the case that produces the error and the
-- case where the conversion cannot touch a single byte. An older install whose
-- `items.barcode` is latin1 would otherwise have its utf8mb4 codes converted
-- down to latin1 and mangled, to repair a UNION that
-- `ItemsDao.takenBarcodesStatement` already converts its way out of.
-- =====================================================================

SET @barcode_charset := (SELECT CHARACTER_SET_NAME
                         FROM information_schema.COLUMNS
                         WHERE TABLE_SCHEMA = DATABASE()
                           AND TABLE_NAME = 'items'
                           AND COLUMN_NAME = 'barcode');

SET @barcode_collation := (SELECT COLLATION_NAME
                           FROM information_schema.COLUMNS
                           WHERE TABLE_SCHEMA = DATABASE()
                             AND TABLE_NAME = 'items'
                             AND COLUMN_NAME = 'barcode');

-- 1) The extra barcodes.
SET @current_charset := (SELECT CHARACTER_SET_NAME
                         FROM information_schema.COLUMNS
                         WHERE TABLE_SCHEMA = DATABASE()
                           AND TABLE_NAME = 'item_barcodes'
                           AND COLUMN_NAME = 'barcode');
SET @current_collation := (SELECT COLLATION_NAME
                           FROM information_schema.COLUMNS
                           WHERE TABLE_SCHEMA = DATABASE()
                             AND TABLE_NAME = 'item_barcodes'
                             AND COLUMN_NAME = 'barcode');
SET @sql := IF(@barcode_collation IS NOT NULL
                   AND @current_charset = @barcode_charset
                   AND @current_collation <> @barcode_collation,
               CONCAT('ALTER TABLE item_barcodes CONVERT TO CHARACTER SET ', @barcode_charset,
                      ' COLLATE ', @barcode_collation),
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) The code on a unit.
SET @current_charset := (SELECT CHARACTER_SET_NAME
                         FROM information_schema.COLUMNS
                         WHERE TABLE_SCHEMA = DATABASE()
                           AND TABLE_NAME = 'items_units'
                           AND COLUMN_NAME = 'items_barcode');
SET @current_collation := (SELECT COLLATION_NAME
                           FROM information_schema.COLUMNS
                           WHERE TABLE_SCHEMA = DATABASE()
                             AND TABLE_NAME = 'items_units'
                             AND COLUMN_NAME = 'items_barcode');
SET @sql := IF(@barcode_collation IS NOT NULL
                   AND @current_charset = @barcode_charset
                   AND @current_collation <> @barcode_collation,
               CONCAT('ALTER TABLE items_units CONVERT TO CHARACTER SET ', @barcode_charset,
                      ' COLLATE ', @barcode_collation),
               'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- The database's own default - what every future `CREATE TABLE` with no charset
-- clause inherits, and so the other half of this - is not here on purpose: MySQL
-- refuses `ALTER DATABASE` through the prepared-statement protocol (error 1295,
-- proved on 8.0.31), and naming a collation this file has to read out of
-- `information_schema` leaves nothing but dynamic SQL to say it with. It is done
-- from `DatabaseMigrationService.alignDatabaseCollationWithItsTables` instead,
-- before Flyway is called.
