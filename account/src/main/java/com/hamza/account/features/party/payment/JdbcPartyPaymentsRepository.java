package com.hamza.account.features.party.payment;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The read-only JDBC side of the payments report. The parameter order is {@link PartyPaymentsQuery}'s. */
public final class JdbcPartyPaymentsRepository extends AbstractDao<PartyPaymentRow>
        implements PartyPaymentsRepository {

    @Override
    public List<PartyPaymentRow> page(PartyPaymentsFilter filter) throws DaoException {
        return queryForObjects(PartyPaymentsQuery.pageSql(filter), this::map,
                PartyPaymentsQuery.values(filter).toArray());
    }

    @Override
    public List<TreasuryOption> treasuries() throws DaoException {
        return withConnection(connection -> {
            List<TreasuryOption> treasuries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(PartyPaymentsQuery.TREASURIES_SQL);
                 ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    treasuries.add(new TreasuryOption(row.getInt("id"), row.getString("t_name")));
                }
            }
            return treasuries;
        });
    }

    @Override
    public PartyPaymentRow map(ResultSet rs) throws DaoException {
        try {
            return new PartyPaymentRow(
                    rs.getLong("movement_id"),
                    rs.getObject("movement_date", LocalDate.class),
                    rs.getInt("party_id"),
                    rs.getString("party_name"),
                    rs.getBigDecimal("paid"),
                    rs.getString("treasury_name"),
                    rs.getInt("invoice_number"),
                    rs.getString("notes"),
                    rs.getString("user_name"));
        } catch (SQLException e) {
            throw new DaoException(e);
        }
    }
}
