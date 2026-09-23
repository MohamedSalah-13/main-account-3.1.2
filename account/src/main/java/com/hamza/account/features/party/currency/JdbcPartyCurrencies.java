package com.hamza.account.features.party.currency;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.treasury.TreasuryCurrencies;
import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * {@link PartyCurrencies} over JDBC. Every helper borrows the connection a transaction already holds on
 * this thread, so the writes commit or roll back with the row they sit beside.
 */
final class JdbcPartyCurrencies extends AbstractDao<Object> implements PartyCurrencies {

    private final TreasuryCurrencies currencies;

    JdbcPartyCurrencies(TreasuryCurrencies currencies) {
        this.currencies = currencies;
    }

    @Override
    public Currency ofParty(PartyKind kind, int partyId) throws DaoException {
        return currencies.find(currencyId(PartyCurrencyQuery.partyCurrencySql(kind), partyId));
    }

    @Override
    public Currency ofTreasury(int treasuryId) throws DaoException {
        return currencies.find(currencyId(PartyCurrencyQuery.TREASURY_CURRENCY_SQL, treasuryId));
    }

    @Override
    public BigDecimal rateOn(int currencyId, LocalDate day) throws DaoException {
        return currencies.rateOn(currencyId, day);
    }

    @Override
    public void writeMovement(PartyKind kind, long movementId, PartyMovementFigures figures) throws DaoException {
        boolean foreign = figures != null && figures.isForeign();
        executeUpdate(PartyCurrencyQuery.writeMovementSql(kind),
                foreign ? figures.paidForeign() : null,
                foreign ? figures.purchaseForeign() : null,
                foreign ? figures.rate() : null,
                movementId);
    }

    @Override
    public StoredDocument storedDocument(DocumentType type, long number) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    PartyCurrencyQuery.storedDocumentSql(type))) {
                statement.setLong(1, number);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next()
                            ? new StoredDocument(rs.getInt("party_id"),
                                    rs.getDate("document_date").toLocalDate(), rs.getBigDecimal("exchange_rate"),
                                    (Integer) rs.getObject("currency_id", Integer.class))
                            : null;
                }
            } catch (SQLException e) {
                throw new DaoException("Could not read a document's translation", e);
            }
        });
    }

    @Override
    public ForeignHeader foreignHeader(DocumentType type, long number) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    PartyCurrencyQuery.foreignHeaderSql(type))) {
                statement.setLong(1, number);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next()
                            ? new ForeignHeader((Integer) rs.getObject("currency_id", Integer.class),
                                    rs.getBigDecimal("exchange_rate"), rs.getBigDecimal("total_foreign"),
                                    rs.getBigDecimal("discount_foreign"), rs.getBigDecimal("paid_foreign"))
                            : null;
                }
            } catch (SQLException e) {
                throw new DaoException("Could not read a document's figures in its party's currency", e);
            }
        });
    }

    @Override
    public java.util.Map<Integer, WrittenLine> writtenLines(DocumentType type, long number) throws DaoException {
        return withConnection(connection -> {
            java.util.Map<Integer, WrittenLine> lines = new java.util.LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    PartyCurrencyQuery.writtenLinesSql(type))) {
                statement.setLong(1, number);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        lines.put(rs.getInt(1), new WrittenLine(rs.getBigDecimal("price_foreign"),
                                rs.getBigDecimal("discount_foreign")));
                    }
                }
            } catch (SQLException e) {
                throw new DaoException("Could not read how a document's lines were typed", e);
            }
            return lines;
        });
    }

    @Override
    public DocumentAmounts documentAmounts(DocumentType type, long number) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    PartyCurrencyQuery.documentAmountsSql(type))) {
                statement.setLong(1, number);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        throw new DaoException("No document " + number + " to translate");
                    }
                    return new DocumentAmounts(rs.getBigDecimal("total"), rs.getBigDecimal("discount"),
                            rs.getBigDecimal("paid"));
                }
            } catch (SQLException e) {
                throw new DaoException("Could not read a document's figures", e);
            }
        });
    }

    @Override
    public void writeDocument(DocumentType type, long number, Integer currencyId,
                              DocumentTranslation translation) throws DaoException {
        executeUpdate(PartyCurrencyQuery.writeDocumentSql(type),
                translation == null ? null : currencyId,
                translation == null ? null : translation.rate(),
                translation == null ? null : translation.total(),
                translation == null ? null : translation.discount(),
                translation == null ? null : translation.paid(),
                number);
    }

    private Integer currencyId(String sql, int id) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    int value = rs.getInt(1);
                    return rs.wasNull() ? null : value;
                }
            } catch (SQLException e) {
                throw new DaoException("Could not read a currency", e);
            }
        });
    }
}
