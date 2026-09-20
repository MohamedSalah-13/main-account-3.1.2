-- =====================================================================
-- V75 - سطر الجرد لا يذهب مع الصنف. المرحلة ج من docs/warehouse-plan.md §10.
--
-- ### ما كان يحدث
--
-- `stock_count_lines.item_id` كان `ON DELETE CASCADE`. وورقة الجرد المرحَّلة **تصحيح جرى
-- فعلًا**: هي الشيء الوحيد الذي يقول إن أحدًا عدّ الرفّ في يوم بعينه ووجد فرقًا فرحّله.
-- فحذف صنف كان يمحو سطوره من كل ورقة جرد مرحَّلة، بلا أثر ولا رفض - والتصحيح يختفي بينما
-- الورقة التي يعود إليها تبقى، فتقرأ بفروق ناقصة.
--
-- وقاعدة هذا المستودع في `DeleteRegistry` أن المفتاح المتتالي **لا يُعلَن**، لأنه يأخذ صفوفه
-- معه: فما دام متتاليًا لم يكن لأحد أن يرفض هذا الحذف. الإصلاح ليس إعلانًا فوق المفتاح، بل
-- المفتاح نفسه.
--
-- ### ماذا يتغير للمستخدم
--
-- صنف جُرِد مرة واحدة ولم يُشترَ ولم يُبَع كان يُحذف؛ صار حذفه مرفوضًا برسالة تسمّي ورقة
-- الجرد (`delete.ref.stock_count_line`).
--
-- **ودمج الأصناف لا يتأثر**: `ItemReferenceRegistry.STOCK_COUNT_LINES` يجمع سطري الصنفين
-- في صف واحد على الهدف قبل أن يُحذف المصدر (`MergeAction.MERGE_ROW`)، فلا يبقى للمصدر سطر
-- يرفض حذفه. وهذا فحص ثانٍ مجاني على السجلّ في كل دمج.
--
-- ### كيف
--
-- المفتاح القديم يُسقَط باسمه كما يقرؤه `information_schema` لا كما تسمّيه `V8`: قاعدة مرّت
-- على `V4` قد تحمل اسمًا آخر، وهو الدرس الذي سقطت عليه `V57` حين سمّت مفتاحًا بالاسم الذي
-- أعطاه `V1`. ثم يُضاف الجديد بـ`add_constraint_if_missing` - الصيغة نفسها التي يقرؤها
-- `SchemaForeignKeys` في الاختبارات، وبدونها يبقى السجلّان يريان مفتاح `V8` المتتالي وحده.
--
-- كلا الإجراءين معرَّف هنا ومحذوف في آخر الملف (`MigrationHelperProcedureTest`).
-- =====================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS drop_item_key_of_stock_count_lines$$
CREATE PROCEDURE drop_item_key_of_stock_count_lines()
BEGIN
    DECLARE key_name VARCHAR(64);

    SELECT CONSTRAINT_NAME
    INTO key_name
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_count_lines'
      AND COLUMN_NAME = 'item_id'
      AND REFERENCED_TABLE_NAME = 'items'
    LIMIT 1;

    IF key_name IS NOT NULL THEN
        SET @drop_key = CONCAT('ALTER TABLE stock_count_lines DROP FOREIGN KEY ', key_name);
        PREPARE stmt FROM @drop_key;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DROP PROCEDURE IF EXISTS add_constraint_if_missing$$
CREATE PROCEDURE add_constraint_if_missing(IN t_name VARCHAR(64), IN c_name VARCHAR(64),
                                           IN c_definition TEXT)
BEGIN
    DECLARE constraint_exists INT;

    SELECT COUNT(*)
    INTO constraint_exists
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = t_name
      AND CONSTRAINT_NAME = c_name;

    IF constraint_exists = 0 THEN
        SET @query = CONCAT('ALTER TABLE ', t_name, ' ADD CONSTRAINT ', c_name, ' ', c_definition);
        PREPARE stmt FROM @query;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL drop_item_key_of_stock_count_lines();

-- The update cascade is kept: an item's id does not move, and if it ever did the line has
-- to follow it rather than refuse. Only the delete changes.
CALL add_constraint_if_missing('stock_count_lines', 'stock_count_lines_items_id_fk',
    'FOREIGN KEY (item_id) REFERENCES items (id) ON UPDATE CASCADE ON DELETE RESTRICT');

DROP PROCEDURE IF EXISTS drop_item_key_of_stock_count_lines;
DROP PROCEDURE IF EXISTS add_constraint_if_missing;
