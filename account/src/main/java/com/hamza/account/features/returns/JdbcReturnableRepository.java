package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JDBC implementation that joins the transaction already opened by InvoiceSaveService. */
public final class JdbcReturnableRepository implements ReturnableRepository {

    @Override
    public boolean sourceExists(DocumentType sourceType, int sourceId) throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(sourceType);
        String sql = "SELECT 1 FROM " + spec.table() + " WHERE " + spec.key() + " = ?";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, sourceId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next();
                }
            }
        });
    }

    @Override
    public List<SoldLine> sourceLines(DocumentType sourceType, int sourceId) throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(sourceType);
        String sql = "SELECT " + spec.lineItem() + " AS item_id,"
                + " SUM(quantity * type_value) AS base_quantity FROM " + spec.lineTable()
                + " WHERE " + DocumentTableSpec.LINE_DOCUMENT + " = ? GROUP BY "
                + spec.lineItem();
        return withConnection(connection -> {
            List<SoldLine> lines = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, sourceId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        lines.add(new SoldLine(rows.getInt("item_id"),
                                rows.getDouble("base_quantity")));
                    }
                }
            }
            return lines;
        });
    }

    @Override
    public Map<Integer, Double> alreadyReturnedBaseQuantities(
            DocumentType returnType, int sourceId, int excludingReturnId) throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(returnType);
        String sql = "SELECT r." + spec.lineItem() + " AS item_id,"
                + " SUM(r.quantity * r.type_value) AS base_quantity FROM "
                + spec.lineTable() + " r JOIN " + spec.table() + " h ON h." + spec.key()
                + " = r." + DocumentTableSpec.LINE_DOCUMENT
                + " WHERE h.source_invoice_number = ? AND h." + spec.key() + " <> ?"
                + " GROUP BY r." + spec.lineItem();
        return withConnection(connection -> {
            Map<Integer, Double> quantities = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, sourceId);
                statement.setInt(2, excludingReturnId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        quantities.put(rows.getInt("item_id"), rows.getDouble("base_quantity"));
                    }
                }
            }
            return quantities;
        });
    }

    @Override
    public Optional<SourceLine> lineById(DocumentType sourceType, int sourceLineId)
            throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(sourceType);
        // Only a `sales` line carries its own cost to preserve; `purchase` has no
        // buy_price column at all - it is itself the cost, and keeps none of its own.
        boolean hasBuyPrice = sourceType == DocumentType.SALES;
        String sql = "SELECT " + spec.lineItem() + " AS item_id, price, "
                + (hasBuyPrice ? "buy_price" : "0") + " AS buy_price, type AS unit_id,"
                + " type_value, expiration_date FROM " + spec.lineTable()
                + " WHERE " + DocumentTableSpec.LINE_KEY + " = ?";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, sourceLineId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    Date expiry = rows.getDate("expiration_date");
                    return Optional.of(new SourceLine(rows.getInt("item_id"),
                            rows.getDouble("price"), rows.getDouble("buy_price"),
                            rows.getInt("unit_id"), rows.getDouble("type_value"),
                            expiry == null ? null : expiry.toLocalDate()));
                }
            }
        });
    }

    @Override
    public List<ExpiryBatch> sourceExpiryBatches(DocumentType sourceType, int sourceId, int itemId)
            throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(sourceType);
        String sql = "SELECT expiration_date, SUM(quantity * type_value) AS base_quantity"
                + " FROM " + spec.lineTable() + " WHERE " + DocumentTableSpec.LINE_DOCUMENT
                + " = ? AND " + spec.lineItem() + " = ? AND expiration_date IS NOT NULL"
                + " GROUP BY expiration_date ORDER BY expiration_date";
        return withConnection(connection -> {
            List<ExpiryBatch> batches = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, sourceId);
                statement.setInt(2, itemId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        batches.add(new ExpiryBatch(
                                rows.getDate("expiration_date").toLocalDate(),
                                rows.getDouble("base_quantity")));
                    }
                }
            }
            return batches;
        });
    }

    @Override
    public List<SourceLineRow> rawLines(DocumentType sourceType, int sourceId) throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(sourceType);
        boolean hasBuyPrice = sourceType == DocumentType.SALES;
        String sql = "SELECT " + DocumentTableSpec.LINE_KEY + " AS line_id, "
                + spec.lineItem() + " AS item_id, quantity, price, "
                + (hasBuyPrice ? "buy_price" : "0") + " AS buy_price, type AS unit_id,"
                + " type_value, expiration_date FROM " + spec.lineTable()
                + " WHERE " + DocumentTableSpec.LINE_DOCUMENT + " = ? ORDER BY "
                + DocumentTableSpec.LINE_KEY;
        return withConnection(connection -> {
            List<SourceLineRow> lines = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, sourceId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        Date expiry = rows.getDate("expiration_date");
                        lines.add(new SourceLineRow(rows.getInt("line_id"),
                                rows.getInt("item_id"), rows.getDouble("quantity"),
                                rows.getDouble("price"), rows.getDouble("buy_price"),
                                rows.getInt("unit_id"), rows.getDouble("type_value"),
                                expiry == null ? null : expiry.toLocalDate()));
                    }
                }
            }
            return lines;
        });
    }

    @Override
    public Optional<Integer> sourceDelegateId(int sourceSalesInvoiceNumber) throws DaoException {
        String sql = "SELECT delegate_id FROM " + DocumentTableSpec.SALES.table()
                + " WHERE " + DocumentTableSpec.SALES.key() + " = ?";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, sourceSalesInvoiceNumber);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    int delegateId = rows.getInt("delegate_id");
                    return rows.wasNull() ? Optional.empty() : Optional.of(delegateId);
                }
            }
        });
    }

    private static <T> T withConnection(SqlWork<T> work) throws DaoException {
        if (!ConnectionManager.inTransaction()) {
            throw new DaoException("Return validation requires an active transaction");
        }
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            return work.run(connection);
        } catch (SQLException e) {
            throw new DaoException("Could not validate the return", e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
