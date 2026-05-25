-- =====================================================================
-- items procedures
-- =====================================================================

DROP TRIGGER IF EXISTS after_items_update;
DROP TRIGGER IF EXISTS before_items_stock_insert;
DROP TRIGGER IF EXISTS before_items_units_insert;
DROP PROCEDURE IF EXISTS max_item_id;


-- =====================================================================
-- max_id
-- =====================================================================

DELIMITER |

CREATE DEFINER = root@localhost PROCEDURE max_item_id(OUT itemId INT)
BEGIN
    SET itemId = (
        SELECT COALESCE(MAX(id), 0)
        FROM items
    );
END;
|

DELIMITER ;