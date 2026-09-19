package com.hamza.account.features.delegate;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcCommissionRunRepository extends AbstractDao<CommissionRun>
        implements CommissionRunRepository {

    /** {@code active_key} and the payroll's period bound: one number for a month. */
    static int key(YearMonth period) {
        return period.getYear() * 100 + period.getMonthValue();
    }

    @Override
    public Optional<Integer> activeRunId(YearMonth period) throws DaoException {
        int id = scalar(CommissionRunQuery.ACTIVE_RUN_ID_SQL, key(period)).intValue();
        return id == 0 ? Optional.empty() : Optional.of(id);
    }

    @Override
    public int insertRun(YearMonth period, String notes, int userId) throws DaoException {
        return insertReturningId(CommissionRunQuery.INSERT_RUN_SQL,
                period.getYear(), period.getMonthValue(), notes, userId);
    }

    @Override
    public int insertLine(int runId, CommissionLine line) throws DaoException {
        return executeUpdate(CommissionRunQuery.INSERT_LINE_SQL,
                runId, line.employeeId(), line.ruleId(), line.basis().name(), line.tierMode().name(),
                line.target(), line.tiersSnapshot(), line.sales(), line.salesReturns(), line.collected(),
                line.baseAmount(), line.achievementPercent(), line.tier(), line.ratePercent(), line.amount());
    }

    @Override
    public List<CommissionRun> runs() throws DaoException {
        return queryForObjects(CommissionRunQuery.RUNS_SQL, this::map);
    }

    @Override
    public Optional<CommissionRun> run(int runId) throws DaoException {
        return queryForObjects(CommissionRunQuery.RUN_SQL, this::map, runId).stream().findFirst();
    }

    @Override
    public List<CommissionLine> linesOf(int runId) throws DaoException {
        return withConnection(connection -> {
            List<CommissionLine> lines = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(CommissionRunQuery.LINES_SQL)) {
                statement.setInt(1, runId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        lines.add(line(rs));
                    }
                }
            }
            return lines;
        });
    }

    @Override
    public int cancel(int runId, int userId, String reason) throws DaoException {
        return executeUpdate(CommissionRunQuery.CANCEL_RUN_SQL, userId, reason, runId);
    }

    @Override
    public List<Unposted> unpostedLinesForUpdate(int runId) throws DaoException {
        return withConnection(connection -> {
            List<Unposted> lines = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(CommissionRunQuery.UNPOSTED_LINES_SQL)) {
                statement.setInt(1, runId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        lines.add(new Unposted(rs.getInt("id"), rs.getInt("employee_id"), rs.getBigDecimal("amount")));
                    }
                }
            }
            return lines;
        });
    }

    @Override
    public int insertLedgerCommission(int employeeId, LocalDate date, BigDecimal amount, String notes, int userId)
            throws DaoException {
        return insertReturningId(CommissionRunQuery.INSERT_LEDGER_COMMISSION_SQL,
                employeeId, Date.valueOf(date), amount, notes, userId);
    }

    @Override
    public int insertAccountPosting(int lineId, int ledgerEntryId, int userId) throws DaoException {
        return executeUpdate(CommissionRunQuery.INSERT_ACCOUNT_POSTING_SQL, lineId, ledgerEntryId, userId);
    }

    @Override
    public BigDecimal payrollDue(int employeeId, YearMonth period) throws DaoException {
        return scalar(CommissionRunQuery.PAYROLL_DUE_SQL, employeeId, key(period));
    }

    @Override
    public int insertPayrollPostings(int payrollRunId, int userId, int employeeId, YearMonth period)
            throws DaoException {
        return executeUpdate(CommissionRunQuery.INSERT_PAYROLL_POSTINGS_SQL,
                payrollRunId, userId, employeeId, key(period));
    }

    @Override
    public boolean ruleOfDayUsed(int employeeId, LocalDate effectiveFrom) throws DaoException {
        return scalar(CommissionRunQuery.RULE_OF_DAY_USED_SQL, employeeId, Date.valueOf(effectiveFrom)).signum() > 0;
    }

    @Override
    public boolean ruleUsed(int ruleId) throws DaoException {
        return scalar(CommissionRunQuery.RULE_USED_SQL, ruleId).signum() > 0;
    }

    @Override
    public CommissionRun map(ResultSet rs) throws DaoException {
        try {
            Timestamp approved = rs.getTimestamp("approved_at");
            return new CommissionRun(rs.getInt("id"),
                    YearMonth.of(rs.getInt("period_year"), rs.getInt("period_month")),
                    CommissionRun.Status.of(rs.getString("status")), rs.getString("notes"),
                    approved == null ? null : approved.toLocalDateTime(), rs.getString("user_name"),
                    rs.getString("cancel_reason"), rs.getInt("line_count"), rs.getBigDecimal("total"),
                    rs.getInt("posted_lines"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    private static CommissionLine line(ResultSet rs) throws SQLException {
        rs.getInt("payroll_run_id");
        boolean byPayroll = !rs.wasNull();
        rs.getInt("ledger_entry_id");
        boolean byAccount = !rs.wasNull();
        CommissionLine.Posting posting = byPayroll ? CommissionLine.Posting.PAYROLL
                : byAccount ? CommissionLine.Posting.ACCOUNT : CommissionLine.Posting.NONE;
        return new CommissionLine(rs.getInt("id"), rs.getInt("run_id"), rs.getInt("employee_id"),
                rs.getString("column_name"), rs.getInt("rule_id"), CommissionBasis.of(rs.getString("basis")),
                TierMode.of(rs.getString("tier_mode")), rs.getBigDecimal("target"), rs.getString("tiers_snapshot"),
                rs.getBigDecimal("sales"), rs.getBigDecimal("sales_returns"), rs.getBigDecimal("collected"),
                rs.getBigDecimal("base_amount"), rs.getBigDecimal("achievement_percent"), rs.getInt("tier"),
                rs.getBigDecimal("rate_percent"), rs.getBigDecimal("amount"), posting);
    }

    /** The first column of the first row as a number; zero when there is no row. */
    private BigDecimal scalar(String sql, Object... parameters) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setData(statement, parameters);
                try (ResultSet rs = statement.executeQuery()) {
                    BigDecimal value = rs.next() ? rs.getBigDecimal(1) : null;
                    return value == null ? BigDecimal.ZERO : value;
                }
            }
        });
    }
}
