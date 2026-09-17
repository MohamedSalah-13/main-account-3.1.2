package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.ExpenseQuery;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The JDBC side of the expense reports. Every statement is {@link ExpenseReportQuery}'s and every filter is
 * bound through {@link ExpenseQuery#whereValues} - the list's own binder - so the order a condition is
 * written and the order it is bound cannot drift apart between the list and a report.
 */
public final class JdbcExpenseReportRepository extends AbstractDao<ExpenseReportRows.Day>
        implements ExpenseReportRepository {

    @Override
    public List<ExpenseReportRows.HeadingTotal> headingTotals(ExpenseFilter filter) throws DaoException {
        return list(ExpenseReportQuery.headingTotalsSql(filter),
                rs -> (new ExpenseReportRows.HeadingTotal(rs.getInt("heading_id"),
                        rs.getInt("expense_count"), rs.getBigDecimal("total"))),
                ExpenseQuery.whereValues(filter).toArray());
    }

    @Override
    public List<ExpenseReportRows.HeadingDay> headingDays(ExpenseFilter filter) throws DaoException {
        return list(ExpenseReportQuery.headingDaysSql(filter),
                rs -> (new ExpenseReportRows.HeadingDay(rs.getInt("heading_id"),
                        rs.getDate("expense_date").toLocalDate(), rs.getBigDecimal("total"))),
                ExpenseQuery.whereValues(filter).toArray());
    }

    @Override
    public List<ExpenseReportRows.Day> days(ExpenseFilter filter) throws DaoException {
        return list(ExpenseReportQuery.daysSql(filter),
                rs -> (new ExpenseReportRows.Day(rs.getDate("expense_date").toLocalDate(),
                        rs.getBigDecimal("total"))),
                ExpenseQuery.whereValues(filter).toArray());
    }

    @Override
    public List<ExpenseReportRows.DimensionTotal> dimension(ExpenseFilter filter, ExpenseDimension dimension)
            throws DaoException {
        return list(ExpenseReportQuery.dimensionSql(filter, dimension),
                rs -> (new ExpenseReportRows.DimensionTotal(rs.getString("dimension_key"),
                        rs.getString("dimension_label"), rs.getInt("expense_count"), rs.getBigDecimal("total"))),
                ExpenseQuery.whereValues(filter).toArray());
    }

    @Override
    public List<ExpenseReportRows.Day> netSalesDays(LocalDate from, LocalDate to) throws DaoException {
        Date start = Date.valueOf(from);
        Date end = Date.valueOf(to);
        return list(ExpenseReportQuery.NET_SALES_DAYS_SQL,
                rs -> (new ExpenseReportRows.Day(rs.getDate("sale_date").toLocalDate(),
                        rs.getBigDecimal("net_sales"))),
                start, end, start, end);
    }

    @Override
    public ExpenseReportRows.Day map(ResultSet rs) throws DaoException {
        try {
            return new ExpenseReportRows.Day(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }

    /** One row of a result, read on the spot. */
    private interface RowReader<R> {
        R read(ResultSet rs) throws SQLException;
    }

    /**
     * Every row of a statement, through the pooled connection {@code withConnection} lends - the base
     * class's own list helper is typed to one row class, and these statements answer four.
     */
    private <R> List<R> list(String sql, RowReader<R> reader, Object... parameters) throws DaoException {
        return withConnection(connection -> {
            List<R> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setData(statement, parameters);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        rows.add(reader.read(rs));
                    }
                }
            }
            return rows;
        });
    }
}
