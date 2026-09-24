package com.hamza.account.features.pricing;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/** JDBC for the two reports - every statement is {@link TierReportQuery}'s, every value bound. */
public final class JdbcTierReportRepository extends AbstractDao<Object> implements TierReportRepository {

    @Override
    public TierReports.MissingPage missing(List<Integer> activeTiers, String text, int limit, int offset)
            throws DaoException {
        TierReportQuery.Statement page = TierReportQuery.missingPage(activeTiers, text, limit, offset);
        List<TierReports.MissingPrice> rows = read(page, rs -> {
            List<TierReports.MissingPrice> found = new ArrayList<>();
            while (rs.next()) {
                List<Double> prices = List.of(rs.getDouble("sel_price1"), rs.getDouble("sel_price2"),
                        rs.getDouble("sel_price3"));
                List<Integer> missing = activeTiers.stream()
                        .filter(tier -> prices.get(tier - 1) <= 0).toList();
                found.add(new TierReports.MissingPrice(rs.getInt("id"), rs.getString("barcode"),
                        rs.getString("nameItem"), prices, missing));
            }
            return found;
        });
        int total = read(TierReportQuery.missingCount(activeTiers, text), rs -> rs.next() ? rs.getInt(1) : 0);
        return new TierReports.MissingPage(rows, total);
    }

    @Override
    public TierReports.BelowListPage belowList(java.time.LocalDate from, java.time.LocalDate to, String text,
                                               int limit, int offset) throws DaoException {
        List<TierReports.BelowList> rows = read(TierReportQuery.belowListPage(from, to, text, limit, offset), rs -> {
            List<TierReports.BelowList> found = new ArrayList<>();
            while (rs.next()) {
                int tier = rs.getInt("price_tier_id");
                Integer tierId = rs.wasNull() ? null : tier;
                found.add(new TierReports.BelowList(rs.getInt("invoice_number"),
                        rs.getDate("invoice_date").toLocalDate(), rs.getString("customer"),
                        rs.getString("user_name"), rs.getString("nameItem"), rs.getString("unit_name"),
                        rs.getDouble("quantity"), rs.getBigDecimal("list_price"), rs.getBigDecimal("price"),
                        rs.getBigDecimal("given"), tierId));
            }
            return found;
        });
        TierReports.BelowListSummary summary = read(TierReportQuery.belowListSummary(from, to, text),
                rs -> rs.next()
                        ? new TierReports.BelowListSummary(rs.getInt(1), rs.getInt(2),
                                rs.getBigDecimal(3) == null ? BigDecimal.ZERO : rs.getBigDecimal(3))
                        : TierReports.BelowListSummary.NONE);
        return new TierReports.BelowListPage(rows, summary);
    }

    private <T> T read(TierReportQuery.Statement statement, Rows<T> rows) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement prepared = connection.prepareStatement(statement.sql())) {
                int index = 1;
                for (Object value : statement.parameters()) {
                    if (value instanceof java.time.LocalDate day) {
                        prepared.setDate(index++, Date.valueOf(day));
                    } else {
                        prepared.setObject(index++, value);
                    }
                }
                try (ResultSet rs = prepared.executeQuery()) {
                    return rows.read(rs);
                }
            }
        });
    }

    @FunctionalInterface
    private interface Rows<T> {
        T read(ResultSet rs) throws SQLException;
    }
}
