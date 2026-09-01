package com.hamza.account.model.dao;

import com.hamza.account.features.shift.ShiftCashMovement;
import com.hamza.account.features.shift.ShiftCashSummary;
import com.hamza.account.model.domain.ShiftSummary;
import com.hamza.account.treasury.MovementLabel;
import com.hamza.account.model.domain.UserShift;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.SqlStatements;
import lombok.extern.log4j.Log4j2;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final String TREASURY_ID = "treasury_id";
    private static final String TOTAL_CASH_IN = "total_cash_in";
    private static final String TOTAL_CASH_OUT = "total_cash_out";
    private static final String TREASURY_NAME = "treasury_name";

    /**
     * Every read of a shift goes through this, so the till it was opened on is
     * named wherever a shift is shown. LEFT JOIN: a shift whose treasury row has
     * gone must still appear - the cashier's record of the day does not depend on
     * the till still existing.
     */
    private static final String SELECT_SHIFTS =
            "SELECT us.*, t.t_name AS " + TREASURY_NAME +
            " FROM " + TABLE_NAME + " us LEFT JOIN treasury t ON t.id = us." + TREASURY_ID;

    UserShiftDao() {
        super();
    }

    @Override
    public List<UserShift> loadAll() throws DaoException {
        return queryForObjects(SELECT_SHIFTS, this::map);
    }

    @Override
    public int insert(UserShift shift) throws DaoException {
        Object[] objects = {
                shift.getUserId(),
                shift.getTreasuryId(),
                shift.getOpenTime() != null ? Timestamp.valueOf(shift.getOpenTime()) : null,
                shift.getOpenBalance(),
                shift.isOpen(),
                shift.getNotes()
        };
        return executeUpdate(
                SqlStatements.insertStatement(TABLE_NAME, USER_ID, TREASURY_ID, OPEN_TIME, OPEN_BALANCE, IS_OPEN, NOTES),
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
                TOTAL_CASH_IN, TOTAL_CASH_OUT,
                EXPECTED_BALANCE, DIFFERENCE, INVOICES_COUNT);
        return executeUpdate(sql, getData(shift));
    }

    @Override
    public int deleteById(int id) throws DaoException {
        return executeUpdate(SqlStatements.deleteStatement(TABLE_NAME, ID), id);
    }

    @Override
    public UserShift getDataById(int id) throws DaoException {
        return queryForObject(SELECT_SHIFTS + " WHERE us." + ID + " = ?", this::map, id);
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
                shift.getTotalCashIn(),
                shift.getTotalCashOut(),
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
            shift.setTreasuryId(getIntSafe(rs, TREASURY_ID));
            shift.setTreasuryName(getStringSafe(rs, TREASURY_NAME));

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
            shift.setTotalCashIn(getDoubleSafe(rs, TOTAL_CASH_IN));
            shift.setTotalCashOut(getDoubleSafe(rs, TOTAL_CASH_OUT));
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

    private String getStringSafe(ResultSet rs, String col) {
        try {
            return rs.getString(col);
        } catch (SQLException e) {
            return null;
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
        String sql = SELECT_SHIFTS +
                " WHERE us." + USER_ID + " = ? AND us." + IS_OPEN + " = TRUE" +
                " ORDER BY us." + OPEN_TIME + " DESC LIMIT 1";
        return queryForObject(sql, this::map, userId);
    }

    public List<UserShift> getShiftsByUserId(int userId) throws DaoException {
        String sql = SELECT_SHIFTS +
                " WHERE us." + USER_ID + " = ? ORDER BY us." + OPEN_TIME + " DESC";
        return queryForObjects(sql, this::map, userId);
    }

    public boolean hasOpenShift(int userId) throws DaoException {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME +
                " WHERE " + USER_ID + " = ? AND " + IS_OPEN + " = TRUE";
        return withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            } catch (SQLException e) {
                log.error("Error checking open shift for user ID: {}", userId, e);
                throw new DaoException(e);
            }
        });
    }

    // =========================================================
    // حساب ملخص الوردية من الجداول الأخرى (Time-Based)
    // =========================================================

    /**
     * The shift's cash movements, summed per heading, out of {@code treasury_balance}.
     * <p>
     * That view is the one definition of what a cash movement is - ten headings over
     * ten tables, each carrying its own {@code treasury_id} and {@code user_id}. This
     * method used to write four of them out by hand (sales, sales returns, expenses,
     * deposits and withdrawals) and mention no treasury at all, which is finding ن-٣
     * of the audit: a cashier who collected from a customer's account finished the day
     * over by what they collected, one who paid a supplier in cash finished short by
     * what they paid, and in a business with a wallet beside the drawer the two tills'
     * movements were added together into one expected balance.
     * <p>
     * The opening line is excluded here rather than in Java: it belongs to the till,
     * not to the day, exactly as {@code treasury_current_balance} excludes it.
     *
     * @param userId     the cashier
     * @param treasuryId the till the shift was opened on
     * @param from       the shift's open time
     * @param to         now (X-Report) or the close time (Z-Report)
     */
    public ShiftSummary calculateShiftSummary(int userId, int treasuryId, LocalDateTime from, LocalDateTime to)
            throws DaoException {

        Timestamp tsFrom = Timestamp.valueOf(from);
        Timestamp tsTo = Timestamp.valueOf(to);

        String movementsSql = """
                SELECT information,
                       COALESCE(SUM(income), 0) AS income,
                       COALESCE(SUM(output), 0) AS output
                FROM treasury_balance
                WHERE user_id = ? AND treasury_id = ?
                  AND date_insert BETWEEN ? AND ?
                  AND information <> ?
                GROUP BY information
                """;

        List<ShiftCashMovement> movements = readMovements(movementsSql,
                userId, treasuryId, tsFrom, tsTo, MovementLabel.OPENING.text());

        int invoicesCount = countInt(
                "SELECT COUNT(*) FROM total_sales " +
                        " WHERE user_id = ? AND treasury_id = ? AND date_insert BETWEEN ? AND ?",
                userId, treasuryId, tsFrom, tsTo);

        // The opening balance is filled in by the service, which is what holds the shift.
        return ShiftCashSummary.summarize(movements, 0, invoicesCount);
    }

    /**
     * The grouped rows. {@code AbstractDao}'s helpers are typed to the DAO's own
     * model, and this reads a different shape, so the connection is borrowed
     * directly - the same route {@code hasOpenShift} takes.
     * <p>
     * A heading this code does not know is dropped rather than added to a total
     * under the wrong sign. {@code MovementLabelTest} fails the build when the view
     * writes one that {@link MovementLabel} has never heard of, so this is the
     * second line of that defence and not the first.
     */
    private List<ShiftCashMovement> readMovements(String sql, Object... params) throws DaoException {
        return withConnection(connection -> {
            List<ShiftCashMovement> movements = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bindParams(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String information = rs.getString("information");
                        MovementLabel label = Arrays.stream(MovementLabel.values())
                                .filter(value -> value.text().equals(information))
                                .findFirst()
                                .orElse(null);
                        if (label == null) {
                            log.warn("Unknown treasury_balance heading in a shift summary, ignored: {}", information);
                            continue;
                        }
                        movements.add(new ShiftCashMovement(label, rs.getDouble("income"), rs.getDouble("output")));
                    }
                }
                return movements;
            } catch (SQLException e) {
                log.error("readMovements failed: {}", sql, e);
                throw new DaoException(e);
            }
        });
    }

    private int countInt(String sql, Object... params) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bindParams(ps, params);
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
        });
    }

    private void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    public List<UserShift> getShiftsBetween(LocalDateTime from, LocalDateTime to, Integer userId) throws DaoException {
        StringBuilder sql = new StringBuilder(SELECT_SHIFTS)
                .append(" WHERE us.").append(OPEN_TIME).append(" BETWEEN ? AND ?");

        if (userId != null && userId > 0) {
            sql.append(" AND us.").append(USER_ID).append(" = ?");
        }

        sql.append(" ORDER BY us.").append(OPEN_TIME).append(" DESC");

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