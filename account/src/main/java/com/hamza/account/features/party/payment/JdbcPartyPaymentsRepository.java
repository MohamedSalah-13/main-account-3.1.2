package com.hamza.account.features.party.payment;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** The read-only JDBC side of the payments report. The parameter order is {@link PartyPaymentsQuery}'s. */
public final class JdbcPartyPaymentsRepository extends AbstractDao<PartyPaymentRow>
        implements PartyPaymentsRepository {

    @Override
    public List<PartyPaymentRow> between(PartyKind kind, LocalDate from, LocalDate to) throws DaoException {
        return queryForObjects(PartyPaymentsQuery.betweenSql(kind), this::map,
                Date.valueOf(from), Date.valueOf(to));
    }

    @Override
    public PartyPaymentRow map(ResultSet rs) throws DaoException {
        try {
            return new PartyPaymentRow(
                    rs.getLong("movement_id"),
                    rs.getDate("movement_date").toLocalDate(),
                    rs.getInt("party_id"),
                    rs.getString("party_name"),
                    rs.getBigDecimal("paid"),
                    rs.getString("treasury_name"),
                    rs.getInt("invoice_number"),
                    rs.getString("notes"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
