package com.hamza.account.features.currency.difference;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Reads {@link ExchangeDifferenceQuery}'s statements. */
public final class JdbcExchangeDifferenceRepository extends AbstractDao<Object>
        implements ExchangeDifferenceRepository {

    @Override
    public List<ExchangeAccount> accounts() throws DaoException {
        return withConnection(connection -> {
            List<ExchangeAccount> accounts = new ArrayList<>();
            readAccounts(connection, ExchangeDifferenceQuery.TREASURIES_SQL, ExchangeAccountKind.TREASURY, accounts);
            readAccounts(connection, ExchangeDifferenceQuery.partiesSql(PartyKind.CUSTOMER),
                    ExchangeAccountKind.CUSTOMER, accounts);
            readAccounts(connection, ExchangeDifferenceQuery.partiesSql(PartyKind.SUPPLIER),
                    ExchangeAccountKind.SUPPLIER, accounts);
            return accounts;
        });
    }

    @Override
    public List<ExchangeMovement> movements(LocalDate through) throws DaoException {
        return withConnection(connection -> {
            List<ExchangeMovement> movements = new ArrayList<>();
            readMovements(connection, ExchangeDifferenceQuery.TREASURY_MOVEMENTS_SQL, ExchangeAccountKind.TREASURY,
                    through, movements);
            readMovements(connection, ExchangeDifferenceQuery.partyMovementsSql(PartyKind.CUSTOMER),
                    ExchangeAccountKind.CUSTOMER, through, movements);
            readMovements(connection, ExchangeDifferenceQuery.partyMovementsSql(PartyKind.SUPPLIER),
                    ExchangeAccountKind.SUPPLIER, through, movements);
            return movements;
        });
    }

    private static void readAccounts(Connection connection, String sql, ExchangeAccountKind kind,
                                     List<ExchangeAccount> into) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet row = statement.executeQuery()) {
            while (row.next()) {
                into.add(new ExchangeAccount(kind, row.getInt("id"), row.getString("name"), row.getInt("currency_id")));
            }
        }
    }

    private static void readMovements(Connection connection, String sql, ExchangeAccountKind kind,
                                      LocalDate through, List<ExchangeMovement> into) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(through));
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    into.add(new ExchangeMovement(kind, row.getInt("account_id"),
                            row.getDate("movement_date").toLocalDate(), row.getInt("source"),
                            row.getLong("reference"), row.getBigDecimal("own"), row.getBigDecimal("book")));
                }
            }
        }
    }
}
