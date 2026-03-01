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

    private final String TABLE_NAME = "processes_data";
    private final String ID = "id";
    private final String USER_ID = "user_id";
    private final String PROCESSES_NAME = "processes_name";
    private final String TABLE_NAME_DATA = "table_name";
    private final String TABLE_ID = "table_id";
    private final String TIME_INSERT = "date_insert";
    private final String NOTES = "notes";


    public Processes_Dao(Connection connection) {
        super(connection);
    }

    @Override
    public List<Processes_Data> loadAll() throws DaoException {
        String query = "SELECT processes_data.id, user_id, processes_name, table_name, table_id, date_insert, notes, u.user_name from processes_data join users u on u.id = processes_data.user_id";
        return queryForObjects(query, this::map);
    }


    @Override
    public Processes_Data map(ResultSet rs) throws DaoException {
        Processes_Data processesData = new Processes_Data();
        try {
            processesData.setId(rs.getInt(ID));
            processesData.setUsersObject(new Users(rs.getInt(USER_ID), rs.getString(UsersDao.USER_NAME)));
            processesData.setProcessesDataType(ProcessesDataType.valueOf(rs.getString(PROCESSES_NAME)));

            var tableType = TableType.valueOf(rs.getString(TABLE_NAME_DATA));
            processesData.setTableType(tableType);
            processesData.setCode(rs.getLong(TABLE_ID));

            String string = rs.getString(TIME_INSERT);
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
