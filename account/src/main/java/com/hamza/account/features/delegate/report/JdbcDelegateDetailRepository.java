package com.hamza.account.features.delegate.report;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class JdbcDelegateDetailRepository extends AbstractDao<DelegateDetailRow>
        implements DelegateDetailRepository {

    @Override
    public List<DelegateDetailRow> breakdown(DelegateBreakdown breakdown, DelegateDetailFilter filter)
            throws DaoException {
        return queryForObjects(breakdown.sql(), this::map, filter.breakdownParameters());
    }

    @Override
    public BigDecimal[] headerDiscounts(DelegateDetailFilter filter) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DelegateDetailQuery.HEADER_DISCOUNT_SQL)) {
                bind(statement, filter.breakdownParameters());
                try (ResultSet rs = statement.executeQuery()) {
                    rs.next();
                    return new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2)};
                }
            }
        });
    }

    @Override
    public List<DelegateCollectionRow> collections(DelegateDetailFilter filter) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DelegateDetailQuery.COLLECTIONS_SQL)) {
                bind(statement, filter.collectionParameters());
                try (ResultSet rs = statement.executeQuery()) {
                    List<DelegateCollectionRow> rows = new ArrayList<>();
                    while (rs.next()) {
                        rows.add(new DelegateCollectionRow(rs.getLong("account_num"),
                                rs.getDate("account_date").toLocalDate(), rs.getString("name"),
                                rs.getLong("numberInv"), rs.getBigDecimal("paid"),
                                rs.getString("treasury_name")));
                    }
                    return rows;
                }
            }
        });
    }

    private static void bind(PreparedStatement statement, Object[] parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
    }

    @Override
    public DelegateDetailRow map(ResultSet rs) throws DaoException {
        try {
            return new DelegateDetailRow(rs.getInt("key_id"), rs.getString("key_name"),
                    rs.getBigDecimal("measure"), rs.getBigDecimal("sales"), rs.getBigDecimal("sales_returns"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
