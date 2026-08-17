package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
