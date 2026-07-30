-- user permission
DROP TRIGGER IF EXISTS after_users_insert;

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