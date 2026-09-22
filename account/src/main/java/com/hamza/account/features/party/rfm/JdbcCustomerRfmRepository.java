package com.hamza.account.features.party.rfm;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The read-only JDBC side of the table. The page and the summary bind the scored rows and the text
 * through {@link #bindFilter}, so the two cannot describe different sets.
 */
public final class JdbcCustomerRfmRepository extends AbstractDao<CustomerRfmRow> implements CustomerRfmRepository {

    @Override
    public List<CustomerRfmRow> search(CustomerRfmFilter filter) throws DaoException {
        List<Object> values = bindFilter(filter);
        values.add(filter.fetchSize());
        values.add(filter.offset());
        return queryForObjects(CustomerRfmQuery.pageSql(filter.order()), this::map, values.toArray());
    }

    @Override
    public CustomerRfmSummary summarize(CustomerRfmFilter filter) throws DaoException {
        Object[] values = bindFilter(filter).toArray();
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(CustomerRfmQuery.summarySql())) {
                setData(statement, values);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return CustomerRfmSummary.EMPTY;
                    }
                    return new CustomerRfmSummary(rs.getInt("parties"), rs.getInt("buying"),
                            rs.getInt("documents"), rs.getBigDecimal("net"));
                }
            }
        });
    }

    @Override
    public Optional<String> customerName(int id) throws DaoException {
        if (id <= 0) {
            return Optional.empty();
        }
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(CustomerRfmQuery.NAME_SQL)) {
                statement.setInt(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.<String>empty();
                }
            }
        });
    }

    /** The scored rows' seven and the text's two, in the statements' textual order. */
    static List<Object> bindFilter(CustomerRfmFilter filter) {
        Date from = Date.valueOf(filter.from());
        Date to = Date.valueOf(filter.to());
        List<Object> values = new ArrayList<>();
        values.add(to);                       // the last sale, on or before the period's end
        values.add(from);                     // the period's invoices
        values.add(to);
        values.add(from);                     // the period's returns
        values.add(to);
        values.add(to);                       // recency, measured to the period's end
        values.add(filter.excludedParty());   // the customer cash sales land on
        values.add(filter.text());
        values.add(filter.pattern());
        return values;
    }

    @Override
    public CustomerRfmRow map(ResultSet rs) throws DaoException {
        try {
            return new CustomerRfmRow(
                    rs.getInt("party_id"),
                    rs.getString("name"),
                    rs.getDate("last_day").toLocalDate(),
                    rs.getInt("recency_days"),
                    rs.getInt("documents"),
                    rs.getBigDecimal("sold"),
                    rs.getBigDecimal("returned"),
                    rs.getBigDecimal("net"),
                    rs.getInt("r_score"),
                    rs.getInt("f_score"),
                    rs.getInt("m_score"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
