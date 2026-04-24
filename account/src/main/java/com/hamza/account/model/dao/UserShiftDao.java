package com.hamza.account.model.dao;

import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.*;
import java.time.LocalDateTime;
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

    // أعمدة المرحلة 2
    private static final String TOTAL_SALES = "total_sales";
    private static final String TOTAL_SALES_RETURNS = "total_sales_returns";
    private static final String TOTAL_EXPENSES = "total_expenses";
    private static final String TOTAL_DEPOSITS = "total_deposits";
    private static final String TOTAL_WITHDRAWALS = "total_withdrawals";
    private static final String EXPECTED_BALANCE = "expected_balance";
    private static final String DIFFERENCE = "difference";
    private static final String INVOICES_COUNT = "invoices_count";

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

    /**
     * تحديث كامل للوردية بما فيها حقول الملخص (يُستخدم عند الغلق).
     */
    @Override
    public int update(UserShift shift) throws DaoException {
        String sql = SqlStatements.updateStatement(TABLE_NAME, ID,
                CLOSE_TIME, CLOSE_BALANCE, IS_OPEN, NOTES,
                TOTAL_SALES, TOTAL_SALES_RETURNS, TOTAL_EXPENSES,
                TOTAL_DEPOSITS, TOTAL_WITHDRAWALS,
                EXPECTED_BALANCE, DIFFERENCE, INVOICES_COUNT);
        return executeUpdate(sql, getData(shift));
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
                shift.getTotalSales(),
                shift.getTotalSalesReturns(),
                shift.getTotalExpenses(),
                shift.getTotalDeposits(),
                shift.getTotalWithdrawals(),
                shift.getExpectedBalance(),
                shift.getDifference(),
                shift.getInvoicesCount(),
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

            // حقول المرحلة 2 (قد لا تكون موجودة لو لم يُشغَّل الـ migration بعد)
            shift.setTotalSales(getDoubleSafe(rs, TOTAL_SALES));
            shift.setTotalSalesReturns(getDoubleSafe(rs, TOTAL_SALES_RETURNS));
            shift.setTotalExpenses(getDoubleSafe(rs, TOTAL_EXPENSES));
            shift.setTotalDeposits(getDoubleSafe(rs, TOTAL_DEPOSITS));
            shift.setTotalWithdrawals(getDoubleSafe(rs, TOTAL_WITHDRAWALS));
            shift.setExpectedBalance(getDoubleSafe(rs, EXPECTED_BALANCE));
            shift.setDifference(getDoubleSafe(rs, DIFFERENCE));
            shift.setInvoicesCount(getIntSafe(rs, INVOICES_COUNT));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return shift;
    }

    private double getDoubleSafe(ResultSet rs, String col) {
        try {
            return rs.getDouble(col);
        } catch (SQLException e) {
            return 0.0;
        }
    }

    private int getIntSafe(ResultSet rs, String col) {
        try {
            return rs.getInt(col);
        } catch (SQLException e) {
            return 0;
        }
    }

    public UserShift getOpenShiftByUserId(int userId) throws DaoException {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " + USER_ID + " = ? AND " + IS_OPEN + " = TRUE" +
                " ORDER BY " + OPEN_TIME + " DESC LIMIT 1";
        return queryForObject(sql, this::map, userId);
    }

    public List<UserShift> getShiftsByUserId(int userId) throws DaoException {
        String sql = "SELECT * FROM " + TABLE_NAME +
                " WHERE " + USER_ID + " = ? ORDER BY " + OPEN_TIME + " DESC";
        return queryForObjects(sql, this::map, userId);
    }

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

    // =========================================================
    // حساب ملخص الوردية من الجداول الأخرى (Time-Based)
    // =========================================================

    /**
     * يحسب الملخص المالي للوردية اعتماداً على نطاق الوقت ومعرف المستخدم.
     * يُستخدم لحظياً (X-Report) أو عند الغلق (Z-Report).
     *
     * @param userId  معرّف المستخدم
     * @param from    بداية الفترة (open_time)
     * @param to      نهاية الفترة (الآن أو close_time)
     */
    public ShiftSummary calculateShiftSummary(int userId, LocalDateTime from, LocalDateTime to)
            throws DaoException {

        Timestamp tsFrom = Timestamp.valueOf(from);
        Timestamp tsTo = Timestamp.valueOf(to);

        double totalSales = sumDouble(
                "SELECT COALESCE(SUM(paid_up), 0) FROM total_sales " +
                        "WHERE user_id = ? AND date_insert BETWEEN ? AND ?",
                userId, tsFrom, tsTo);

        double totalSalesReturns = sumDouble(
                "SELECT COALESCE(SUM(paid_from_treasury), 0) FROM total_sales_re " +
                        "WHERE user_id = ? AND date_insert BETWEEN ? AND ?",
                userId, tsFrom, tsTo);

        double totalExpenses = sumDouble(
                "SELECT COALESCE(SUM(amount), 0) FROM expenses_details " +
                        "WHERE user_id = ? AND date_insert BETWEEN ? AND ?",
                userId, tsFrom, tsTo);

        // deposit_or_expenses: 1 = إيداع, 2 = سحب (حسب التصميم الحالي)
        double totalDeposits = sumDouble(
                "SELECT COALESCE(SUM(amount), 0) FROM treasury_deposit_expenses " +
                        "WHERE user_id = ? AND deposit_or_expenses = 1 " +
                        "AND date_insert BETWEEN ? AND ?",
                userId, tsFrom, tsTo);

        double totalWithdrawals = sumDouble(
                "SELECT COALESCE(SUM(amount), 0) FROM treasury_deposit_expenses " +
                        "WHERE user_id = ? AND deposit_or_expenses = 2 " +
                        "AND date_insert BETWEEN ? AND ?",
                userId, tsFrom, tsTo);

        int invoicesCount = countInt(
                "SELECT COUNT(*) FROM total_sales " +
                        " WHERE user_id = ? AND date_insert BETWEEN ? AND ?",
                userId, tsFrom, tsTo);

        return ShiftSummary.builder()
                .totalSales(totalSales)
                .totalSalesReturns(totalSalesReturns)
                .totalExpenses(totalExpenses)
                .totalDeposits(totalDeposits)
                .totalWithdrawals(totalWithdrawals)
                .invoicesCount(invoicesCount)
                .build();
    }

    private double sumDouble(String sql, Object... params) throws DaoException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindParams(ps, params);
            System.out.println("sumDouble SQL: " + ps.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        } catch (SQLException e) {
            log.error("sumDouble failed: {}", sql, e);
            throw new DaoException(e);
        }
    }

    private int countInt(String sql, Object... params) throws DaoException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindParams(ps, params);
//            System.out.println("countInt SQL: " + ps.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        } catch (SQLException e) {
            log.error("countInt failed: {}", sql, e);
            throw new DaoException(e);
        }
    }

    private void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    public List<UserShift> getShiftsBetween(LocalDateTime from, LocalDateTime to, Integer userId) throws DaoException {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(TABLE_NAME)
                .append(" WHERE ").append(OPEN_TIME).append(" BETWEEN ? AND ?");

        if (userId != null && userId > 0) {
            sql.append(" AND ").append(USER_ID).append(" = ?");
        }

        sql.append(" ORDER BY ").append(OPEN_TIME).append(" DESC");

        try {
            if (userId != null && userId > 0) {
                return queryForObjects(sql.toString(), this::map,
                        Timestamp.valueOf(from),
                        Timestamp.valueOf(to),
                        userId);
            }
            return queryForObjects(sql.toString(), this::map,
                    Timestamp.valueOf(from),
                    Timestamp.valueOf(to));
        } catch (DaoException e) {
            throw e;
        }
    }
}