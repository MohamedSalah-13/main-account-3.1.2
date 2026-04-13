-- main_group
DROP TRIGGER IF EXISTS before_main_group_insert;
DROP TRIGGER IF EXISTS before_main_group_delete;

DELIMITER |
create trigger before_main_group_insert
    before insert
    on main_group
    for each row
begin
    IF NOT EXISTS (SELECT 1
                   FROM users
                   WHERE users.id = NEW.user_id) THEN
        set NEW.user_id = 1;
    END IF;
end;
|
DELIMITER ;

DELIMITER |
create trigger before_main_group_delete
    before delete
    on main_group
    for each row
begin
    if (OLD.id = 1) Then
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot delete or update a parent row';
    end if;

end;
|
DELIMITER ;

-- sub_group
DROP TRIGGER IF EXISTS before_sub_group_insert;
DROP TRIGGER IF EXISTS before_sub_group_delete;

DELIMITER |
create trigger before_sub_group_insert
    before insert
    on sub_group
    for each row
begin
    IF NOT EXISTS (SELECT 1
                   FROM users
                   WHERE users.id = NEW.user_id) THEN
        set NEW.user_id = 1;
    END IF;
end;
|
DELIMITER ;

DELIMITER |
create trigger before_sub_group_delete
    before delete
    on sub_group
    for each row
begin
    if (OLD.id = 1) Then
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot delete or update a parent row';
    end if;

end;
|
DELIMITER ;