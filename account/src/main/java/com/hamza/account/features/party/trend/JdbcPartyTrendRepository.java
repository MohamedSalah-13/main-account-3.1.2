package com.hamza.account.features.party.trend;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** The read-only JDBC side of the trend chart. The parameter order is {@link PartyTrendQuery}'s. */
public final class JdbcPartyTrendRepository extends AbstractDao<PartyTrendDay>
        implements PartyTrendRepository {

    @Override
    public List<PartyTrendDay> daily(PartyKind kind, LocalDate from, LocalDate to, Integer partyId)
            throws DaoException {
        return queryForObjects(PartyTrendQuery.dailySql(kind), this::map,
                Date.valueOf(from), Date.valueOf(to), partyId, partyId);
    }

    @Override
    public PartyTrendDay map(ResultSet rs) throws DaoException {
        try {
            return new PartyTrendDay(rs.getDate("day").toLocalDate(),
                    rs.getBigDecimal("debit"), rs.getBigDecimal("credit"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
