-- drop all triggers
# SELECT TRIGGER_SCHEMA     AS Database_Name,
#        TRIGGER_NAME,
#        EVENT_MANIPULATION AS Event,
#        EVENT_OBJECT_TABLE AS Target_Table,
#        ACTION_STATEMENT   AS Trigger_Body,
#        ACTION_TIMING      AS Timing
# FROM INFORMATION_SCHEMA.TRIGGERS
# WHERE TRIGGER_SCHEMA = DATABASE();

-- Generate DROP TRIGGER statements for all triggers in the current database
# SELECT CONCAT('DROP TRIGGER IF EXISTS ', TRIGGER_NAME, ';') AS drop_statement
# FROM INFORMATION_SCHEMA.TRIGGERS
# WHERE TRIGGER_SCHEMA = DATABASE();


-- Drop all existing triggers
# DROP PROCEDURE IF EXISTS drop_all_triggers_in_schema;
#
# DELIMITER $$
#
# CREATE PROCEDURE drop_all_triggers_in_schema()
# BEGIN
#     DECLARE v_trigger_name VARCHAR(64);
#     DECLARE v_table_name VARCHAR(64);
#     DECLARE v_done INT DEFAULT FALSE;
#     DECLARE v_current_schema VARCHAR(64);
#
#     DECLARE trigger_cursor CURSOR FOR
#         SELECT TRIGGER_NAME, EVENT_OBJECT_TABLE
#         FROM INFORMATION_SCHEMA.TRIGGERS
#         WHERE TRIGGER_SCHEMA = v_current_schema;
#
#     DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = TRUE;
#
#     SET v_current_schema = DATABASE();
#
#     OPEN trigger_cursor;
#
#     drop_triggers_loop: LOOP
#         FETCH trigger_cursor INTO v_trigger_name, v_table_name;
#
#         IF v_done THEN
#             LEAVE drop_triggers_loop;
#         END IF;
#
#         SET @drop_sql = CONCAT('DROP TRIGGER IF EXISTS ', v_trigger_name);
#         PREPARE stmt FROM @drop_sql;
#         EXECUTE stmt;
#         DEALLOCATE PREPARE stmt;
#     END LOOP;
#
#     CLOSE trigger_cursor;
# END$$
#
# DELIMITER ;
#
# CALL drop_all_triggers_in_schema();

-- company
DROP TRIGGER IF EXISTS after_company_update;

-- user permission
DROP TRIGGER IF EXISTS after_users_insert;
DROP TRIGGER IF EXISTS before_users_delete;

-- insert permission for new user
DELIMITER |
create trigger after_users_insert
    after insert
    on users
    for each row
begin
    declare maxPermissions int unsigned default (SELECT count(*) FROM permission);
    declare currentPermissionId int unsigned default 1;
    IF (NEW.id > 1) THEN
        while currentPermissionId <= maxPermissions
            do
                set @permissionId = (SELECT p.id FROM permission p WHERE p.id = currentPermissionId);
                insert into user_permission (permission_id, user_id)
                VALUES (@permissionId, NEW.id);
                set currentPermissionId = currentPermissionId + 1;
            end while;
    end if;

end;
|
DELIMITER ;

-- prevent deleting root user
DELIMITER |
create trigger before_users_delete
    before delete
    on users
    for each row
begin
    if (OLD.id = 1) Then
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot delete or update a parent row';
    end if;

end;
|
DELIMITER ;

-- permission
DROP TRIGGER IF EXISTS after_permission_insert;

DELIMITER |
create trigger after_permission_insert
    after insert
    on permission
    for each row
begin
    INSERT INTO user_permission (permission_id, user_id, check_status)
    SELECT NEW.id, users.id, 0
    FROM users
    WHERE users.id != 1;
end;
|
DELIMITER ;