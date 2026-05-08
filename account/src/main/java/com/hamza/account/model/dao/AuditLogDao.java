package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Audit_log;
import com.hamza.account.model.domain.Users;
import com.hamza.account.type.ProcessesDataType;
import com.hamza.account.type.TableType;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AuditLogDao extends AbstractDao<Audit_log> {

    private final String TABLE_NAME = "audit_log";
    private final String ID = "id";
    private final String USER_ID = "user_id";
    private final String action_type = "action_type";
    private final String TABLE_NAME_DATA = "table_name";
    private final String record_id = "record_id";
    private final String action_time = "action_time";
    private final String NOTES = "notes";

    public AuditLogDao(Connection connection) {
        super(connection);
    }

    @Override
    public Audit_log map(ResultSet rs) throws DaoException {
        Audit_log auditLog = new Audit_log();
        try {
            auditLog.setId(rs.getInt(ID));
            auditLog.setUsersObject(new Users(rs.getInt(USER_ID), rs.getString(UsersDao.USER_NAME)));
            auditLog.setProcessesDataType(ProcessesDataType.valueOf(rs.getString(action_type)));

            var tableType = TableType.valueOf(rs.getString(TABLE_NAME_DATA));
            auditLog.setTableType(tableType);
            auditLog.setCode(rs.getLong(record_id));

            String string = rs.getString(action_time);
            auditLog.setCreated_at(LocalDateTime.parse(string, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            auditLog.setNotes(rs.getString(NOTES));
            auditLog.setRecord_id(rs.getString(record_id));
            auditLog.setOld_data(rs.getString("old_data"));
            auditLog.setNew_data(rs.getString("new_data"));

        } catch (Exception e) {
            throw new DaoException(e);
        }
        return auditLog;
    }

    public int deleteRangeIds(Integer... rangeIds) throws DaoException {
        return executeUpdate(SqlStatements.deleteInRangeId(TABLE_NAME, ID, rangeIds));
    }

    public List<Audit_log> getAuditLogsBetweenDates(LocalDate startDate, LocalDate endDate) throws DaoException {
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
                         join users u on u.id = audit_log.user_id
                WHERE DATE(action_time) BETWEEN ? AND ?
                ORDER BY action_time DESC""";
        return queryForObjects(query, this::map, startDate, endDate);
    }
}
