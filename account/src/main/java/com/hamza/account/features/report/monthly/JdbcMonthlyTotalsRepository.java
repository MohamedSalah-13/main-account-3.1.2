package com.hamza.account.features.report.monthly;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** The read-only JDBC side of the monthly totals - {@link MonthlyTotalsQuery#daysSql}, row by row. */
public final class JdbcMonthlyTotalsRepository extends AbstractDao<DayFigures> implements MonthlyTotalsRepository {

    @Override
    public List<DayFigures> days(MonthlySide side) throws DaoException {
        return queryForObjects(MonthlyTotalsQuery.daysSql(side), this::map);
    }

    /** The days of the side between two dates, both included - {@link MonthlyTotalsQuery#daysBetweenSql}. */
    public List<DayFigures> daysBetween(MonthlySide side, LocalDate from, LocalDate to) throws DaoException {
        java.sql.Date start = java.sql.Date.valueOf(from);
        java.sql.Date end = java.sql.Date.valueOf(to);
        return queryForObjects(MonthlyTotalsQuery.daysBetweenSql(side), this::map, start, end, start, end);
    }

    @Override
    public DayFigures map(ResultSet rs) throws DaoException {
        try {
            return new DayFigures(rs.getObject("day", LocalDate.class), new MonthFigures(
                    rs.getInt("invoices"),
                    rs.getBigDecimal("gross"),
                    rs.getBigDecimal("discount"),
                    rs.getInt("return_documents"),
                    rs.getBigDecimal("returns_gross"),
                    rs.getBigDecimal("returns_discount")));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
