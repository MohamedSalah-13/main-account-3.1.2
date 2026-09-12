package com.hamza.account.features.employee.statement;

import com.hamza.account.features.employee.EmployeeMovementSource;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The JDBC side of an employee's statement.
 * <p>
 * The statements are {@link EmployeeStatementQuery}'s and every value is bound in the order that
 * class writes its conditions. {@link #bindRowFilter} is called from both the page and the
 * summary, which is what keeps the two describing one set of rows - and
 * {@code EmployeeStatementQueryTest} counts the parameters of each statement against what is bound
 * here, because a statement bound in a different order than it is written still runs; it just
 * answers a different question.
 */
public final class JdbcEmployeeStatementRepository extends AbstractDao<EmployeeStatementRow>
        implements EmployeeStatementRepository {

    @Override
    public List<EmployeeStatementRow> page(EmployeeStatementFilter filter) throws DaoException {
        List<Object> values = new ArrayList<>();
        values.add(filter.employeeId());
        values.add(Date.valueOf(filter.from()));
        values.add(filter.employeeId());
        values.add(Date.valueOf(filter.from()));
        values.add(Date.valueOf(filter.to()));
        bindRowFilter(values, filter);
        values.add(filter.queryLimit());
        values.add(filter.offset());
        return queryForObjects(EmployeeStatementQuery.pageSql(), this::map, values.toArray());
    }

    @Override
    public EmployeeStatementSummary summarize(EmployeeStatementFilter filter) throws DaoException {
        List<Object> values = new ArrayList<>();
        values.add(Date.valueOf(filter.from()));
        // Three times: the debit total, the credit total and the count each carry the same
        // shown condition, and the statement writes it three times because SQL has no way to
        // name it once.
        bindShown(values, filter);
        bindShown(values, filter);
        bindShown(values, filter);
        values.add(Date.valueOf(filter.to()));
        values.add(filter.employeeId());

        return withConnection(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(EmployeeStatementQuery.summarySql())) {
                setData(statement, values.toArray());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return EmployeeStatementSummary.EMPTY;
                    }
                    return new EmployeeStatementSummary(rs.getBigDecimal("opening_balance"),
                            rs.getBigDecimal("total_debit"), rs.getBigDecimal("total_credit"),
                            rs.getInt("shown_count"), rs.getBigDecimal("closing_balance"));
                }
            }
        });
    }

    @Override
    public LocalDate earliestMovement(int employeeId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(EmployeeStatementQuery.EARLIEST_MOVEMENT_SQL)) {
                statement.setInt(1, employeeId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    Date earliest = rs.getDate("earliest");
                    return earliest == null ? null : earliest.toLocalDate();
                }
            }
        });
    }

    @Override
    public BigDecimal currentBalance(int employeeId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(EmployeeStatementQuery.CURRENT_BALANCE_SQL)) {
                statement.setInt(1, employeeId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getBigDecimal("balance") : BigDecimal.ZERO;
                }
            }
        });
    }

    @Override
    public List<EmployeeStatementUserOption> usersWhoEntered(int employeeId) throws DaoException {
        return withConnection(connection -> {
            List<EmployeeStatementUserOption> users = new ArrayList<>();
            try (PreparedStatement statement =
                         connection.prepareStatement(EmployeeStatementQuery.USERS_SQL)) {
                statement.setInt(1, employeeId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        users.add(new EmployeeStatementUserOption(rs.getInt(1), rs.getString(2)));
                    }
                }
            }
            return users;
        });
    }

    @Override
    public int insertLedgerEntry(int employeeId, LocalDate date, String kind, BigDecimal amount,
                                 String notes, int userId) throws DaoException {
        return insertReturningId(EmployeeStatementQuery.INSERT_LEDGER_SQL,
                employeeId, Date.valueOf(date), kind, amount, notes, userId);
    }

    @Override
    public Integer ledgerRunOf(int employeeId, int entryId) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement =
                         connection.prepareStatement(EmployeeStatementQuery.LEDGER_RUN_SQL)) {
                statement.setInt(1, entryId);
                statement.setInt(2, employeeId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    int run = rs.getInt(1);
                    return rs.wasNull() ? null : run;
                }
            }
        });
    }

    @Override
    public int deleteLedgerEntry(int employeeId, int entryId) throws DaoException {
        return executeUpdate(EmployeeStatementQuery.DELETE_LEDGER_SQL, entryId, employeeId);
    }

    @Override
    public int insertPurpose(int expenseId, String purpose, Integer payrollRunId, int userId)
            throws DaoException {
        return executeUpdate(EmployeeStatementQuery.INSERT_PURPOSE_SQL,
                expenseId, purpose, payrollRunId, userId);
    }

    /** The period and then the row filters, which is what the summary's two totals each take. */
    private static void bindShown(List<Object> values, EmployeeStatementFilter filter) {
        values.add(Date.valueOf(filter.from()));
        values.add(Date.valueOf(filter.to()));
        bindRowFilter(values, filter);
    }

    /**
     * The twelve of {@link EmployeeStatementQuery#rowFilterSql}, in the order it writes them.
     * <p>
     * Each pair is the same value twice: the first answers the {@code ? IS NULL} probe and the
     * second is what the comparison uses. The text pair is the exception - the probe is the raw
     * text and the comparison takes the escaped pattern, because a blank search must add no
     * condition rather than match on {@code '%%'}.
     */
    private static void bindRowFilter(List<Object> values, EmployeeStatementFilter filter) {
        String kinds = filter.kindList();
        values.add(kinds);
        values.add(kinds);

        String source = filter.sourceCode();
        values.add(source);
        values.add(source);

        values.add(filter.userId());
        values.add(filter.userId());

        values.add(filter.minAmount());
        values.add(filter.minAmount());

        values.add(filter.maxAmount());
        values.add(filter.maxAmount());

        values.add(filter.hasText() ? filter.text() : null);
        values.add(EmployeeStatementQuery.pattern(filter.text()));
    }

    @Override
    public EmployeeStatementRow map(ResultSet rs) throws DaoException {
        try {
            Timestamp entered = rs.getTimestamp("entered_at");
            return new EmployeeStatementRow(
                    rs.getDate("movement_date").toLocalDate(),
                    EmployeeMovementSource.of(rs.getString("source")),
                    rs.getString("entry_kind"),
                    rs.getInt("source_id"),
                    rs.getBigDecimal("debit"),
                    rs.getBigDecimal("credit"),
                    rs.getString("notes"),
                    entered == null ? null : entered.toLocalDateTime(),
                    rs.getInt("user_id"),
                    rs.getString("user_name"),
                    rs.getBigDecimal("running_balance"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
