package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Processes_Data;
import com.hamza.account.model.domain.Users;
import com.hamza.account.type.ProcessesDataType;
import com.hamza.account.type.TableType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class Processes_Dao extends AbstractDao<Processes_Data> {

    private final String TABLE_NAME = "audit_log";
    private final String ID = "id";
    private final String USER_ID = "user_id";
    private final String action_type = "action_type";
    private final String TABLE_NAME_DATA = "table_name";
    private final String record_id = "record_id";
    private final String action_time = "action_time";
    private final String NOTES = "notes";


    public Processes_Dao(Connection connection) {
        super(connection);
    }

    @Override
    public List<Processes_Data> loadAll() throws DaoException {
        String query = """
                SELECT audit_log.id,
                       table_name,
                       record_id,
                       action_type,
                       user_id,
                       action_time,
                       old_data,
                       new_data,
                       source,
                       notes,
                       u.id,
                       user_name,
                       user_pass,
                       user_activity,
                       user_available,
                       updated_at
                FROM audit_log
                         join users u on u.id = audit_log.user_id""";
        return queryForObjects(query, this::map);
    }


    @Override
    public Processes_Data map(ResultSet rs) throws DaoException {
        Processes_Data processesData = new Processes_Data();
        try {
            processesData.setId(rs.getInt(ID));
            processesData.setUsersObject(new Users(rs.getInt(USER_ID), rs.getString(UsersDao.USER_NAME)));
            processesData.setProcessesDataType(ProcessesDataType.valueOf(rs.getString(action_type)));

            var tableType = TableType.valueOf(rs.getString(TABLE_NAME_DATA));
            processesData.setTableType(tableType);
            processesData.setCode(rs.getLong(record_id));

            String string = rs.getString(action_time);
            processesData.setCreated_at(LocalDateTime.parse(string, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            processesData.setNotes(rs.getString(NOTES));
        } catch (Exception e) {
            throw new DaoException(e);
        }
        return processesData;
    }

    public int deleteRangeIds(Integer... rangeIds) throws DaoException {
        return executeUpdate(SqlStatements.deleteInRangeId(TABLE_NAME, ID, rangeIds));
    }
}
