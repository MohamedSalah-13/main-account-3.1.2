package com.hamza.account.model.dao;

import com.hamza.account.features.shift.ShiftCashMovement;
import com.hamza.account.features.shift.ShiftCashSummary;
import com.hamza.account.features.shift.ShiftStatus;
import com.hamza.account.features.shift.ShiftCashSource;
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
import java.math.BigDecimal;
import java.sql.Statement;
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
    private static final String SHIFT_STATUS = "shift_status";
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
    private static final String USERNAME = "username";

    /**
     * Every read of a shift goes through this, so the till it was opened on is
     * named wherever a shift is shown. LEFT JOIN: a shift whose treasury row has
     * gone must still appear - the cashier's record of the day does not depend on
     * the till still existing.
     */
    private static final String SELECT_SHIFTS =
            "SELECT us.*, t.t_name AS " + TREASURY_NAME + ", u.user_name AS " + USERNAME +
            " FROM " + TABLE_NAME + " us LEFT JOIN treasury t ON t.id = us." + TREASURY_ID +
            " LEFT JOIN users u ON u.id = us." + USER_ID;

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

    /** Inserts and returns the real generated shift id, never the affected-row count. */
    public int insertReturningId(UserShift shift) throws DaoException {
        String sql = SqlStatements.insertStatement(TABLE_NAME, USER_ID, TREASURY_ID,
                OPEN_TIME, OPEN_BALANCE, IS_OPEN, SHIFT_STATUS, NOTES);
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setInt(1, shift.getUserId());
                statement.setInt(2, shift.getTreasuryId());
                statement.setTimestamp(3, Timestamp.valueOf(shift.getOpenTime()));
                statement.setBigDecimal(4, shift.getOpenBalance());
                statement.setBoolean(5, shift.isOpen());
                statement.setString(6, shift.getStatus().name());
                statement.setString(7, shift.getNotes());
                if (statement.executeUpdate() != 1) throw new DaoException("Shift was not opened");
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
                throw new DaoException("Shift id was not generated");
            } catch (SQLException e) {
                throw new DaoException("Could not open shift", e);
            }
        });
    }

    /**
     * تحديث كامل للوردية بما فيها حقول الملخص (يُستخدم عند الغلق).
     */
    @Override
    public int update(UserShift shift) throws DaoException {
        String sql = SqlStatements.updateStatement(TABLE_NAME, ID,
                CLOSE_TIME, CLOSE_BALANCE, IS_OPEN, SHIFT_STATUS, NOTES,
                TOTAL_SALES, TOTAL_SALES_RETURNS, TOTAL_EXPENSES,
                TOTAL_DEPOSITS, TOTAL_WITHDRAWALS,
                TOTAL_CASH_IN, TOTAL_CASH_OUT,
                EXPECTED_BALANCE, DIFFERENCE, INVOICES_COUNT);
        return executeUpdate(sql, getData(shift));
    }

    /** Atomic close: a second concurrent closer affects zero rows. */
    public int close(UserShift shift) throws DaoException {
        String sql = "UPDATE " + TABLE_NAME + " SET "
                + CLOSE_TIME + "=?, " + CLOSE_BALANCE + "=?, " + IS_OPEN + "=?, " + SHIFT_STATUS + "=?, "
                + NOTES + "=?, " + TOTAL_SALES + "=?, " + TOTAL_SALES_RETURNS + "=?, "
                + TOTAL_EXPENSES + "=?, " + TOTAL_DEPOSITS + "=?, " + TOTAL_WITHDRAWALS + "=?, "
                + TOTAL_CASH_IN + "=?, " + TOTAL_CASH_OUT + "=?, " + EXPECTED_BALANCE + "=?, "
                + DIFFERENCE + "=?, " + INVOICES_COUNT + "=? WHERE " + ID + "=? AND " + IS_OPEN + "=TRUE";
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
                shift.getStatus().name(),
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
            shift.setUsername(getStringSafe(rs, USERNAME));

            Timestamp openTs = rs.getTimestamp(OPEN_TIME);
            if (openTs != null) shift.setOpenTime(openTs.toLocalDateTime());

            Timestamp closeTs = rs.getTimestamp(CLOSE_TIME);
            if (closeTs != null) shift.setCloseTime(closeTs.toLocalDateTime());

            shift.setOpenBalance(rs.getBigDecimal(OPEN_BALANCE));
            shift.setCloseBalance(rs.getBigDecimal(CLOSE_BALANCE));
            shift.setOpen(rs.getBoolean(IS_OPEN));
            shift.setNotes(rs.getString(NOTES));
            shift.setStatus(getStatusSafe(rs, shift.isOpen()));

            // حقول المرحلة 2 (قد لا تكون موجودة لو لم يُشغَّل الـ migration بعد)
            shift.setTotalSales(getBigDecimalSafe(rs, TOTAL_SALES));
            shift.setTotalSalesReturns(getBigDecimalSafe(rs, TOTAL_SALES_RETURNS));
            shift.setTotalExpenses(getBigDecimalSafe(rs, TOTAL_EXPENSES));
            shift.setTotalDeposits(getBigDecimalSafe(rs, TOTAL_DEPOSITS));
            shift.setTotalWithdrawals(getBigDecimalSafe(rs, TOTAL_WITHDRAWALS));
            shift.setTotalCashIn(getBigDecimalSafe(rs, TOTAL_CASH_IN));
            shift.setTotalCashOut(getBigDecimalSafe(rs, TOTAL_CASH_OUT));
            shift.setExpectedBalance(getBigDecimalSafe(rs, EXPECTED_BALANCE));
            shift.setDifference(getBigDecimalSafe(rs, DIFFERENCE));
            shift.setInvoicesCount(getIntSafe(rs, INVOICES_COUNT));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
        return shift;
    }

    private BigDecimal getBigDecimalSafe(ResultSet rs, String col) {
        try {
            BigDecimal value = rs.getBigDecimal(col);
            return value == null ? BigDecimal.ZERO : value;
        } catch (SQLException e) {
            return BigDecimal.ZERO;
        }
    }

    private ShiftStatus getStatusSafe(ResultSet rs, boolean open) {
        try {
            String value = rs.getString(SHIFT_STATUS);
            return value == null ? (open ? ShiftStatus.OPEN : ShiftStatus.CLOSED) : ShiftStatus.valueOf(value);
        } catch (SQLException | IllegalArgumentException e) {
            return open ? ShiftStatus.OPEN : ShiftStatus.CLOSED;
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

    public UserShift getOpenShiftByUserIdForUpdate(int userId) throws DaoException {
        String sql = SELECT_SHIFTS + " WHERE us." + USER_ID + " = ? AND us." + IS_OPEN + " = TRUE"
                + " AND us." + SHIFT_STATUS + " = 'OPEN'"
                + " ORDER BY us." + OPEN_TIME + " DESC LIMIT 1 FOR UPDATE";
        return queryForObject(sql, this::map, userId);
    }

    public UserShift getOpenShiftByIdForUpdate(int shiftId) throws DaoException {
        return queryForObject(SELECT_SHIFTS + " WHERE us." + ID + " = ? AND us." + IS_OPEN + " = TRUE FOR UPDATE",
                this::map, shiftId);
    }

    public UserShift getOpenShiftByTreasuryIdForUpdate(int treasuryId) throws DaoException {
        String sql = SELECT_SHIFTS + " WHERE us." + TREASURY_ID + " = ? AND us." + IS_OPEN + " = TRUE"
                + " AND us." + SHIFT_STATUS + " = 'OPEN'"
                + " ORDER BY us." + OPEN_TIME + " DESC LIMIT 1 FOR UPDATE";
        return queryForObject(sql, this::map, treasuryId);
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

    public boolean hasOpenShiftForTreasury(int treasuryId) throws DaoException {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME
                + " WHERE " + TREASURY_ID + " = ? AND " + IS_OPEN + " = TRUE";
        return countInt(sql, treasuryId) > 0;
    }

    /** Freezes an open shift while a second user reviews its captured close totals. */
    public int markPendingClose(int shiftId) throws DaoException {
        return executeUpdate("UPDATE " + TABLE_NAME + " SET " + SHIFT_STATUS
                + "='PENDING_CLOSE' WHERE " + ID + "=? AND " + IS_OPEN
                + "=TRUE AND " + SHIFT_STATUS + "='OPEN'", shiftId);
    }

    /** Resumes cash activity after a supervisor rejects the immutable close request. */
    public int resumeOpen(int shiftId) throws DaoException {
        return executeUpdate("UPDATE " + TABLE_NAME + " SET " + SHIFT_STATUS
                + "='OPEN' WHERE " + ID + "=? AND " + IS_OPEN
                + "=TRUE AND " + SHIFT_STATUS + "='PENDING_CLOSE'", shiftId);
    }

    public boolean hasAttributedCashMovements(int shiftId) throws DaoException {
        String sql = """
                SELECT EXISTS(
                    SELECT 1 FROM total_buy WHERE shift_id=?
                    UNION ALL SELECT 1 FROM total_buy_re WHERE shift_id=?
                    UNION ALL SELECT 1 FROM total_sales WHERE shift_id=?
                    UNION ALL SELECT 1 FROM total_sales_re WHERE shift_id=?
                    UNION ALL SELECT 1 FROM customers_accounts WHERE shift_id=?
                    UNION ALL SELECT 1 FROM suppliers_accounts WHERE shift_id=?
                    UNION ALL SELECT 1 FROM expenses_details WHERE shift_id=?
                    UNION ALL SELECT 1 FROM treasury_deposit_expenses WHERE shift_id=?
                    UNION ALL SELECT 1 FROM treasury_transfers WHERE source_shift_id=?
                    UNION ALL SELECT 1 FROM treasury_transfers WHERE destination_shift_id=?
                    UNION ALL SELECT 1 FROM shift_cash_ledger WHERE shift_id=?
                )
                """;
        return countInt(sql, shiftId, shiftId, shiftId, shiftId, shiftId,
                shiftId, shiftId, shiftId, shiftId, shiftId, shiftId) > 0;
    }

    /** Serializes competing opens for the same user and till inside the service transaction. */
    public void lockUserAndTreasury(int userId, int treasuryId) throws DaoException {
        lockRow("SELECT id FROM users WHERE id = ? FOR UPDATE", userId);
        lockRow("SELECT id FROM treasury WHERE id = ? FOR UPDATE", treasuryId);
    }

    private void lockRow(String sql, int id) throws DaoException {
        withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new DaoException("Shift owner or treasury does not exist");
                }
                return null;
            } catch (SQLException e) {
                throw new DaoException(e);
            }
        });
    }

    // =========================================================
    // حساب ملخص الوردية بالربط المباشر، مع fallback زمني للبيانات السابقة لـ V25
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
        return calculateShiftSummary(0, userId, treasuryId, from, to);
    }

    public ShiftSummary calculateShiftSummary(int shiftId, int userId, int treasuryId,
                                              LocalDateTime from, LocalDateTime to)
            throws DaoException {

        Timestamp tsFrom = Timestamp.valueOf(from);
        Timestamp tsTo = Timestamp.valueOf(to);

        String movementsSql = """
                SELECT movement_label AS information,
                       COALESCE(SUM(income_delta), 0) AS income,
                       COALESCE(SUM(output_delta), 0) AS output
                FROM shift_cash_ledger
                WHERE shift_id = ?
                GROUP BY movement_label
                UNION ALL
                SELECT tb.information,
                       COALESCE(SUM(tb.income), 0) AS income,
                       COALESCE(SUM(tb.output), 0) AS output
                FROM treasury_balance tb
                WHERE tb.treasury_id = ?
                  AND (tb.shift_id = ? OR (tb.shift_id IS NULL AND tb.user_id = ?
                       AND tb.date_insert >= ? AND tb.date_insert < ?))
                  AND tb.information <> ?
                  AND NOT EXISTS (
                      SELECT 1 FROM shift_cash_ledger l
                      WHERE l.source_type = tb.source_type
                        AND l.source_id = tb.id_no
                        AND l.treasury_id = tb.treasury_id
                        AND l.action_type = 'CREATE'
                  )
                GROUP BY tb.information
                """;

        List<ShiftCashMovement> movements = readMovements(movementsSql,
                shiftId, treasuryId, shiftId, userId, tsFrom, tsTo, MovementLabel.OPENING.text());

        int invoicesCount = countInt(
                """
                SELECT
                    (SELECT COUNT(*) FROM shift_cash_ledger
                     WHERE shift_id=? AND source_type=%d AND action_type='CREATE')
                  + (SELECT COUNT(*) FROM total_sales s
                     WHERE s.treasury_id=?
                       AND (s.shift_id=? OR (s.shift_id IS NULL AND s.user_id=?
                            AND s.date_insert>=? AND s.date_insert<?))
                       AND NOT EXISTS (SELECT 1 FROM shift_cash_ledger l
                           WHERE l.source_type=%d AND l.source_id=s.invoice_number
                             AND l.treasury_id=s.treasury_id AND l.action_type='CREATE'))
                """.formatted(ShiftCashSource.SALES.code(), ShiftCashSource.SALES.code()),
                shiftId, treasuryId, shiftId, userId, tsFrom, tsTo);

        // The opening balance is filled in by the service, which is what holds the shift.
        return ShiftCashSummary.summarize(movements, BigDecimal.ZERO, invoicesCount);
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
                                .filter(value -> value.text().equals(information)
                                        || value.name().equals(information))
                                .findFirst()
                                .orElse(null);
                        if (label == null) {
                            log.warn("Unknown treasury_balance heading in a shift summary, ignored: {}", information);
                            continue;
                        }
                        movements.add(new ShiftCashMovement(label, rs.getBigDecimal("income"), rs.getBigDecimal("output")));
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
