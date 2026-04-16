package com.hamza.account.model.dao;

import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

public class UserShiftDao extends AbstractDao<UserShift> {

    private final String TABLE_NAME = "user_shifts";
    private final String ID = "id";
    private final String USER_ID = "user_id";
    private final String OPEN_TIME = "open_time";
    private final String CLOSE_TIME = "close_time";
    private final String OPEN_BALANCE = "open_balance";
    private final String CLOSE_BALANCE = "close_balance";
    private final String IS_OPEN = "is_open";
    private final String NOTES = "notes";

    UserShiftDao(Connection connection) {
        super(connection);
    }

    @Override
    public List<UserShift> loadAll() throws DaoException {
        return queryForObjects(SqlStatements.selectStatement(TABLE_NAME), this::map);
    }

    @Override
    public int insert(UserShift shift) throws DaoException {
        Object[] objects = {
                shift.getUserId(),
                Timestamp.valueOf(shift.getOpenTime()),
                shift.getOpenBalance(),
                shift.isOpen(),
                shift.getNotes()
        };
        return executeUpdate(SqlStatements.insertStatement(TABLE_NAME, USER_ID, OPEN_TIME, OPEN_BALANCE, IS_OPEN, NOTES), objects);
    }

    @Override
    public int update(UserShift shift) throws DaoException {
        return executeUpdate(SqlStatements.updateStatement(TABLE_NAME, ID, CLOSE_TIME, CLOSE_BALANCE, IS_OPEN, NOTES), getData(shift));
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public UserShift getDataById(int id) throws DaoException {
        return queryForObject(SqlStatements.selectStatementByColumnWhere(TABLE_NAME, ID), this::map, id);
    }

    @Override
    public UserShift getDataByString(String s) throws DaoException {
        return null;
    }

    @Override
    public Object[] getData(UserShift shift) {
        return new Object[]{
                shift.getCloseTime() != null ? Timestamp.valueOf(shift.getCloseTime()) : null,
                shift.getCloseBalance(),
                shift.isOpen(),
                shift.getNotes(),
                shift.getId()
        };
    }

    @Override
    public UserShift map(ResultSet resultSet) throws DaoException {
        UserShift shift = new UserShift();
        try {
            shift.setId(resultSet.getInt(ID));
            shift.setUserId(resultSet.getInt(USER_ID));
            Timestamp openTs = resultSet.getTimestamp(OPEN_TIME);
            if (openTs != null) shift.setOpenTime(openTs.toLocalDateTime());
            Timestamp closeTs = resultSet.getTimestamp(CLOSE_TIME);
            if (closeTs != null) shift.setCloseTime(closeTs.toLocalDateTime());
            shift.setOpenBalance(resultSet.getDouble(OPEN_BALANCE));
            shift.setCloseBalance(resultSet.getDouble(CLOSE_BALANCE));
            shift.setOpen(resultSet.getBoolean(IS_OPEN));
            shift.setNotes(resultSet.getString(NOTES));
            shift.setStatus(shift.isOpen() ? "مفتوحة" : "مغلقة");
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return shift;
    }

    /**
     * Get open shift for a specific user
     */
    public UserShift getOpenShiftByUserId(int userId) throws DaoException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE " + USER_ID + " = ? AND " + IS_OPEN + " = true";
        return queryForObject(sql, this::map, userId);
    }

    /**
     * Get all shifts for a specific user
     */
    public List<UserShift> getShiftsByUserId(int userId) throws DaoException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE " + USER_ID + " = ? ORDER BY " + OPEN_TIME + " DESC";
        return queryForObjects(sql, this::map, userId);
    }

    /**
     * Check if user has an open shift
     */
    public boolean hasOpenShift(int userId) throws DaoException {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE " + USER_ID + " = ? AND " + IS_OPEN + " = true";
        UserShift count = queryForObject(sql, rs -> {
            try {
                return new UserShift(rs.getInt(1));
            } catch (SQLException e) {
                throw new DaoException(e);
            }
        }, userId);
        return count != null && count.isOpen();
    }
}
