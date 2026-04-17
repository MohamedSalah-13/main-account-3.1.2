package com.hamza.account.model.dao;

import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.*;
import java.util.List;

@Log4j2
public class UserShiftDao extends AbstractDao<UserShift> {

    private static final String TABLE_NAME = "user_shifts";
    private static final String ID = "id";
    private static final String USER_ID = "user_id";
    private static final String OPEN_TIME = "open_time";
    private static final String CLOSE_TIME = "close_time";
    private static final String OPEN_BALANCE = "open_balance";
    private static final String CLOSE_BALANCE = "close_balance";
    private static final String IS_OPEN = "is_open";
    private static final String NOTES = "notes";

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
                shift.getOpenTime() != null ? Timestamp.valueOf(shift.getOpenTime()) : null,
                shift.getOpenBalance(),
                shift.isOpen(),
                shift.getNotes()
        };
        return executeUpdate(
                SqlStatements.insertStatement(TABLE_NAME, USER_ID, OPEN_TIME, OPEN_BALANCE, IS_OPEN, NOTES),
                objects);
    }

    @Override
    public int update(UserShift shift) throws DaoException {
        return executeUpdate(
                SqlStatements.updateStatement(TABLE_NAME, ID, CLOSE_TIME, CLOSE_BALANCE, IS_OPEN, NOTES),
                getData(shift));
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
    public UserShift map(ResultSet rs) throws DaoException {
        UserShift shift = new UserShift();
        try {
            shift.setId(rs.getInt(ID));
            shift.setUserId(rs.getInt(USER_ID));

            Timestamp openTs = rs.getTimestamp(OPEN_TIME);
            if (openTs != null) shift.setOpenTime(openTs.toLocalDateTime());

            Timestamp closeTs = rs.getTimestamp(CLOSE_TIME);
            if (closeTs != null) shift.setCloseTime(closeTs.toLocalDateTime());

            shift.setOpenBalance(rs.getDouble(OPEN_BALANCE));
            shift.setCloseBalance(rs.getDouble(CLOSE_BALANCE));
            shift.setOpen(rs.getBoolean(IS_OPEN));
            shift.setNotes(rs.getString(NOTES));
            shift.setStatus(shift.isOpen() ? "مفتوحة" : "مغلقة");
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return shift;
    }

    /**
     * الوردية المفتوحة لمستخدم معيّن (إن وجدت).
     */
    public UserShift getOpenShiftByUserId(int userId) throws DaoException {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " + USER_ID + " = ? AND " + IS_OPEN + " = TRUE" +
                " ORDER BY " + OPEN_TIME + " DESC LIMIT 1";
        return queryForObject(sql, this::map, userId);
    }

    /**
     * جميع ورديات المستخدم مرتبة من الأحدث.
     */
    public List<UserShift> getShiftsByUserId(int userId) throws DaoException {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " + USER_ID + " = ? ORDER BY " + OPEN_TIME + " DESC";
        return queryForObjects(sql, this::map, userId);
    }

    /**
     * التحقق من وجود وردية مفتوحة (عبر PreparedStatement بدون SQL Injection).
     */
    public boolean hasOpenShift(int userId) throws DaoException {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME +
                " WHERE " + USER_ID + " = ? AND " + IS_OPEN + " = TRUE";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            log.error("Error checking open shift for user ID: {}", userId, e);
            throw new DaoException(e);
        }
    }
}