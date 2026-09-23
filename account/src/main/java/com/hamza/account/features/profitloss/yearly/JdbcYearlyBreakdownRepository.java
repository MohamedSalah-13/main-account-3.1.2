package com.hamza.account.features.profitloss.yearly;

import com.hamza.account.features.profitloss.DailyProfitSource;
import com.hamza.account.document.DocumentTableSpec;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Reads {@code view_yearly_monthly_report} for the columns that explain a month's net sales. The view's
 * profit and expenses are not read: those are the statement's, through {@link DailyProfitSource}.
 */
public final class JdbcYearlyBreakdownRepository extends AbstractDao<MonthBreakdown>
        implements YearlyBreakdownRepository {

    static final String BREAKDOWN_SQL = """
            SELECT report_month, sales, sales_discount, sales_return, sales_return_discount,
                   purchases, purchases_discount, purchases_return, purchases_return_discount
            FROM view_yearly_monthly_report
            WHERE report_year = ?
            ORDER BY report_month""";

    @Override
    public List<MonthBreakdown> breakdown(int year) throws DaoException {
        return queryForObjects(BREAKDOWN_SQL, this::map, year);
    }

    @Override
    public List<Integer> years() {
        return queryForIntList(DocumentTableSpec.yearsSql());
    }

    @Override
    public MonthBreakdown map(ResultSet rs) throws DaoException {
        try {
            return MonthBreakdown.fromView(rs.getInt("report_month"),
                    rs.getBigDecimal("sales"), rs.getBigDecimal("sales_discount"),
                    rs.getBigDecimal("sales_return"), rs.getBigDecimal("sales_return_discount"),
                    rs.getBigDecimal("purchases"), rs.getBigDecimal("purchases_discount"),
                    rs.getBigDecimal("purchases_return"), rs.getBigDecimal("purchases_return_discount"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
