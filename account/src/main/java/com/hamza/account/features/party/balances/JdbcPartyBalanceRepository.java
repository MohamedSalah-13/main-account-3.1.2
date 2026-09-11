package com.hamza.account.features.party.balances;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The read-only JDBC side of the balances list.
 * <p>
 * The parameter order is the one {@link PartyBalanceQuery} documents, and the two statements share
 * {@link #bindFilter} so they cannot drift apart - a page and a footer bound differently is the
 * defect that makes a pagination control describe rows nobody can see.
 */
public final class JdbcPartyBalanceRepository extends AbstractDao<PartyBalanceRow>
        implements PartyBalanceRepository {

    @Override
    public List<PartyBalanceRow> search(PartyBalanceFilter filter) throws DaoException {
        List<Object> values = new ArrayList<>();
        bindFilter(values, filter);
        values.add(filter.queryLimit());
        values.add(filter.offset());
        return queryForObjects(PartyBalanceQuery.pageSql(filter), this::map, values.toArray());
    }

    @Override
    public PartyBalanceSummary summarize(PartyBalanceFilter filter) throws DaoException {
        List<Object> values = new ArrayList<>();
        bindFilter(values, filter);
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(PartyBalanceQuery.summarySql(filter))) {
                setData(statement, values.toArray());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return PartyBalanceSummary.EMPTY;
                    }
                    return new PartyBalanceSummary(rs.getInt("parties"),
                            rs.getBigDecimal("total_balance"), rs.getBigDecimal("total_owed"),
                            rs.getBigDecimal("total_in_credit"), rs.getInt("over_limit"));
                }
            }
        });
    }

    @Override
    public List<PartyAreaOption> areas(PartyKind kind) throws DaoException {
        return withConnection(connection -> {
            List<PartyAreaOption> areas = new ArrayList<>();
            try (var statement = connection.prepareStatement(PartyBalanceQuery.areasSql(kind));
                 var rs = statement.executeQuery()) {
                while (rs.next()) {
                    areas.add(new PartyAreaOption(rs.getInt(1), rs.getString(2)));
                }
            }
            return areas;
        });
    }

    /**
     * Everything before the limit and the offset, in the statement's order.
     * <p>
     * The aggregate conditions come last because {@link PartyBalanceQuery} only writes the ones the
     * filter actually sets - a {@code HAVING} on an alias it defines cannot take the
     * {@code ? IS NULL OR} form - so what is bound here follows the same {@code if}s in the same
     * order. The two staying in step is what {@code PartyBalanceQueryTest}'s parameter counts check.
     */
    private static void bindFilter(List<Object> values, PartyBalanceFilter filter) {
        Date asOf = Date.valueOf(filter.asOf());
        Date from = Date.valueOf(filter.movementFrom());

        values.add(asOf);                      // the balance
        values.add(from);                      // period debit
        values.add(asOf);
        values.add(from);                      // period credit
        values.add(asOf);
        values.add(asOf);                      // last movement, never in the future

        values.add(filter.areaId());
        values.add(filter.areaId());
        values.add(filter.priceTierId());
        values.add(filter.priceTierId());
        String text = filter.text().isEmpty() ? null : filter.text();
        values.add(text);
        values.add(text == null ? null : PartyBalanceQuery.pattern(text));
        values.add(text == null ? null : PartyBalanceQuery.pattern(text));

        if (filter.minBalance() != null) {
            values.add(filter.minBalance());
        }
        if (filter.maxBalance() != null) {
            values.add(filter.maxBalance());
        }
        if (filter.idleDays() != null) {
            values.add(Date.valueOf(filter.asOf().minusDays(filter.idleDays())));
        }
    }

    @Override
    public PartyBalanceRow map(ResultSet rs) throws DaoException {
        try {
            Date lastMovement = rs.getDate("last_movement");
            return new PartyBalanceRow(
                    rs.getInt("party_id"),
                    rs.getString("party_name"),
                    rs.getString("party_phone"),
                    rs.getInt("area_id"),
                    rs.getString("area_name"),
                    rs.getBigDecimal("credit_limit"),
                    rs.getInt("price_tier_id"),
                    rs.getBigDecimal("balance"),
                    rs.getBigDecimal("period_debit"),
                    rs.getBigDecimal("period_credit"),
                    lastMovement == null ? null : lastMovement.toLocalDate());
        } catch (SQLException e) {
            throw new DaoException("Could not map a party balance row", e);
        }
    }

    @Override
    public List<PartyBalanceRow> loadAll() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int insert(PartyBalanceRow value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(PartyBalanceRow value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int deleteById(int id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PartyBalanceRow getDataById(int id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] getData(PartyBalanceRow value) {
        throw new UnsupportedOperationException();
    }
}
