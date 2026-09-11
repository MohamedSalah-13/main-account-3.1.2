package com.hamza.account.features.party.ageing;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The read-only JDBC side of the ageing report.
 * <p>
 * The parameter order is the one {@link PartyAgeingQuery} documents, and the two statements share
 * {@link #bindFilter} so they cannot drift apart - a page and a footer bound differently is the
 * defect that makes a footer describe rows nobody can see.
 * <p>
 * <b>{@code asOf} is bound seven times and that is not an accident.</b> Once for the balance, once
 * for the allocations, once for the invoices, and once for each of the five band conditions -
 * except the bands take it as the first argument of a {@code DATEDIFF} that is written once, so it
 * is bound once for the sub-select as a whole. Every one of those has to be the same day or the
 * report stops reconciling; binding them from one field is what guarantees it.
 */
public final class JdbcPartyAgeingRepository extends AbstractDao<PartyAgeingRow>
        implements PartyAgeingRepository {

    @Override
    public List<PartyAgeingRow> search(PartyAgeingFilter filter) throws DaoException {
        List<Object> values = new ArrayList<>();
        bindFilter(values, filter);
        values.add(filter.fetchSize());
        values.add(filter.offset());
        return queryForObjects(PartyAgeingQuery.pageSql(filter), this::map, values.toArray());
    }

    @Override
    public PartyAgeingSummary summarize(PartyAgeingFilter filter) throws DaoException {
        List<Object> values = new ArrayList<>();
        bindFilter(values, filter);
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(PartyAgeingQuery.summarySql(filter))) {
                setData(statement, values.toArray());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return PartyAgeingSummary.EMPTY;
                    }
                    Map<AgeingBucket, BigDecimal> buckets = new EnumMap<>(AgeingBucket.class);
                    for (AgeingBucket bucket : AgeingBucket.values()) {
                        buckets.put(bucket, rs.getBigDecimal(PartyAgeingQuery.column(bucket)));
                    }
                    return new PartyAgeingSummary(rs.getInt("parties"), buckets,
                            rs.getBigDecimal("unallocated"), rs.getBigDecimal("balance"));
                }
            }
        });
    }

    /**
     * Everything before the limit and the offset, in the statement's textual order.
     * <p>
     * The aggregate conditions come last because {@link PartyAgeingQuery} only writes the ones the
     * filter actually sets, so what is bound here follows the same {@code if}s in the same order.
     */
    private void bindFilter(List<Object> values, PartyAgeingFilter filter) {
        Date asOf = Date.valueOf(filter.asOf());

        // Five, in the statement's textual order, and the count is not obvious - which is why
        // PartyAgeingQueryTest pins it. The balance expression appears TWICE in the select: once
        // as `balance`, and once inside the `unallocated` derivation, because MySQL will not let
        // one select-list expression refer to another's alias. Then the aged sub-select takes
        // three of its own.
        values.add(asOf);   // balance
        values.add(asOf);   // the same balance, inside unallocated
        values.add(asOf);   // DATEDIFF - the day every band is measured from
        values.add(asOf);   // allocations counted up to asOf
        values.add(asOf);   // invoices written up to asOf

        if (filter.areaId() != null) {
            values.add(filter.areaId());
        }
        if (filter.hasText()) {
            values.add(filter.pattern());
            values.add(filter.pattern());
            values.add(filter.textAsId());
        }
        if (filter.minimumBalance() != null) {
            values.add(filter.minimumBalance());
        }
    }

    @Override
    public PartyAgeingRow map(ResultSet rs) throws DaoException {
        try {
            Map<AgeingBucket, BigDecimal> buckets = new EnumMap<>(AgeingBucket.class);
            for (AgeingBucket bucket : AgeingBucket.values()) {
                buckets.put(bucket, rs.getBigDecimal(PartyAgeingQuery.column(bucket)));
            }
            return new PartyAgeingRow(
                    rs.getInt("party_id"),
                    rs.getString("party_name"),
                    rs.getString("party_phone"),
                    rs.getString("area_name"),
                    rs.getInt("payment_terms_days"),
                    buckets,
                    rs.getBigDecimal("unallocated"),
                    rs.getBigDecimal("balance"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
