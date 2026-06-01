USE account_system_db;

-- =====================================================================
-- 008_item_procedures.sql
-- Item procedures
-- =====================================================================

DROP PROCEDURE IF EXISTS max_item_id;

DELIMITER $$

CREATE PROCEDURE max_item_id(OUT itemId INT)
BEGIN
    SET itemId = (
        SELECT COALESCE(MAX(id), 0)
        FROM items
    );
END$$

DELIMITER ;

SELECT '008_item_procedures.sql executed successfully' AS status;