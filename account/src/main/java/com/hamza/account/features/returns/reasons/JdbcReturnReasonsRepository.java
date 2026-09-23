package com.hamza.account.features.returns.reasons;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@link ReturnReasonsQuery}'s statements. It joins an open transaction on the thread, as every
 * {@link AbstractDao} helper does, which is how its acceptance test reads rows it has not committed.
 */
public final class JdbcReturnReasonsRepository extends AbstractDao<Object> implements ReturnReasonsRepository {

    @Override
    public List<ReasonTotal> reasons(ReturnSide side, LocalDate from, LocalDate to) throws DaoException {
        return withConnection(connection -> {
            List<ReasonTotal> reasons = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(ReturnReasonsQuery.reasonsSql(side))) {
                statement.setObject(1, from);
                statement.setObject(2, to);
                try (ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        reasons.add(new ReasonTotal(row.getString("reason"), row.getInt("returns"),
                                row.getBigDecimal("value")));
                    }
                }
            }
            return reasons;
        });
    }

    @Override
    public List<ReturnedItem> items(ReturnSide side, LocalDate from, LocalDate to, int limit) throws DaoException {
        return withConnection(connection -> {
            List<ReturnedItem> items = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(ReturnReasonsQuery.itemsSql(side))) {
                statement.setObject(1, from);
                statement.setObject(2, to);
                statement.setInt(3, limit);
                try (ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        items.add(new ReturnedItem(row.getInt("item_id"), row.getString("item_name"),
                                row.getBigDecimal("quantity"), row.getBigDecimal("value"), row.getInt("returns")));
                    }
                }
            }
            return items;
        });
    }

    @Override
    public List<ReturnDocument> documents(ReturnSide side, LocalDate from, LocalDate to, String storedReason)
            throws DaoException {
        boolean withoutReason = storedReason == null || storedReason.isBlank();
        return withConnection(connection -> {
            List<ReturnDocument> documents = new ArrayList<>();
            try (PreparedStatement statement =
                         connection.prepareStatement(ReturnReasonsQuery.documentsSql(side, withoutReason))) {
                statement.setObject(1, from);
                statement.setObject(2, to);
                if (!withoutReason) {
                    statement.setString(3, storedReason);
                }
                try (ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        documents.add(new ReturnDocument(row.getLong("number"),
                                row.getObject("return_date", LocalDate.class), row.getString("party"),
                                row.getLong("source_invoice"), row.getBigDecimal("value"), row.getString("notes")));
                    }
                }
            }
            return documents;
        });
    }

    @Override
    public BigDecimal documentsNet(ReturnSide side, LocalDate from, LocalDate to) throws DaoException {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(ReturnReasonsQuery.documentsNetSql(side))) {
                statement.setObject(1, from);
                statement.setObject(2, to);
                try (ResultSet row = statement.executeQuery()) {
                    return row.next() ? row.getBigDecimal("net") : BigDecimal.ZERO;
                }
            }
        });
    }

    @Override
    public Object map(ResultSet resultSet) {
        throw new UnsupportedOperationException("Read through the named methods");
    }
}
