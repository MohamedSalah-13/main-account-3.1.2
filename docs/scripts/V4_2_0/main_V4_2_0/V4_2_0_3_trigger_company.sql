DROP TRIGGER IF EXISTS after_users_insert;

DELIMITER |

CREATE TRIGGER after_users_insert
    AFTER INSERT ON users
    FOR EACH ROW
BEGIN
    IF (NEW.id > 1) THEN
        INSERT INTO user_permission (permission_id, user_id, check_status)
        SELECT p.id, NEW.id, 0
        FROM permission p
        WHERE p.active = 1;
    END IF;
END;

|

DELIMITER ;


DROP TRIGGER IF EXISTS after_permission_insert;

DELIMITER |

CREATE TRIGGER after_permission_insert
    AFTER INSERT ON permission
    FOR EACH ROW
BEGIN
    INSERT INTO user_permission (permission_id, user_id, check_status)
    SELECT NEW.id, u.id, 0
    FROM users u
    WHERE u.id != 1;
END;

|

DELIMITER ;