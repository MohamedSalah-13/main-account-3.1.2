DROP PROCEDURE IF EXISTS handle_processes_data;

DELIMITER |
CREATE PROCEDURE handle_processes_data(
    IN user_insert INT,
    IN processType VARCHAR(10),
    IN tableName VARCHAR(50),
    IN id_of INT,
    IN note VARCHAR(255)
)
BEGIN
    INSERT INTO processes_data(user_id, processes_name, table_name, table_id, notes)
    VALUES (user_insert, processType, tableName, id_of, note);
    # delete before 30 day
    DELETE
    FROM processes_data
    WHERE date_insert < DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY)
       OR id NOT IN (SELECT id
                     FROM (SELECT id
                           FROM processes_data
                           ORDER BY id DESC
                           LIMIT 500) t);
END;
|

DELIMITER ;

