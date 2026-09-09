package com.hamza.account.features.totals;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Runs the grouped report statements {@link DocumentTableSpec} builds. Read-only. */
public final class JdbcTotalsReportRepository extends AbstractDao<TotalsReportRow>
        implements TotalsReportRepository {

    @Override
    public List<TotalsReportRow> run(DocumentTableSpec spec, DocumentTableSpec.Report report,
                                     TotalsSearchCriteria criteria) throws DaoException {
        List<Object> params = new ArrayList<>();
        String sql = spec.reportSql(report, criteria, params);
        boolean perItem = report == DocumentTableSpec.Report.BY_ITEM;
        return queryForObjects(sql, rs -> mapRow(rs, perItem), params.toArray());
    }

    /**
     * The per-item statement selects a quantity and no cash settled, so the columns are
     * read by which report asked rather than by probing the result set - a missing column
     * is an SQLException, not a zero.
     */
    private TotalsReportRow mapRow(ResultSet rs, boolean perItem) throws DaoException {
        try {
            return new TotalsReportRow(
                    rs.getString("label"),
                    rs.getInt("row_count"),
                    perItem ? value(rs, "sum_quantity") : BigDecimal.ZERO,
                    value(rs, "sum_total"),
                    value(rs, "sum_discount"),
                    perItem ? BigDecimal.ZERO : value(rs, "sum_paid"),
                    perItem ? BigDecimal.ZERO : value(rs, "sum_profit"));
        } catch (SQLException e) {
            throw new DaoException("Could not map a totals report row", e);
        }
    }

    private static BigDecimal value(ResultSet rs, String column) throws SQLException {
        BigDecimal read = rs.getBigDecimal(column);
        return read == null ? BigDecimal.ZERO : read;
    }

    @Override public TotalsReportRow map(ResultSet rs) { throw new UnsupportedOperationException(); }
    @Override public List<TotalsReportRow> loadAll() { throw new UnsupportedOperationException(); }
    @Override public int insert(TotalsReportRow value) { throw new UnsupportedOperationException(); }
    @Override public int update(TotalsReportRow value) { throw new UnsupportedOperationException(); }
    @Override public int deleteById(int id) { throw new UnsupportedOperationException(); }
    @Override public TotalsReportRow getDataById(int id) { throw new UnsupportedOperationException(); }
    @Override public Object[] getData(TotalsReportRow value) { throw new UnsupportedOperationException(); }
}
