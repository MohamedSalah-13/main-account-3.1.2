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
    public boolean lockSource(DocumentType sourceType, int sourceId) throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(sourceType);
        String sql = "SELECT 1 FROM " + spec.table() + " WHERE " + spec.key() + " = ? FOR UPDATE";
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
    public Optional<SourceLine> lineById(DocumentType sourceType, int sourceId, int sourceLineId)
            throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(sourceType);
        // Only a `sales` line carries its own cost to preserve; `purchase` has no
        // buy_price column at all - it is itself the cost, and keeps none of its own.
        boolean hasBuyPrice = sourceType == DocumentType.SALES;
        String sql = "SELECT " + spec.lineItem() + " AS item_id, quantity, price, discount, "
                + (hasBuyPrice ? "buy_price" : "0") + " AS buy_price, type AS unit_id,"
                + " type_value, expiration_date FROM " + spec.lineTable()
                + " WHERE " + DocumentTableSpec.LINE_KEY + " = ? AND "
                + DocumentTableSpec.LINE_DOCUMENT + " = ?";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, sourceLineId);
                statement.setInt(2, sourceId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    Date expiry = rows.getDate("expiration_date");
                    return Optional.of(new SourceLine(rows.getInt("item_id"),
                            rows.getDouble("quantity"), rows.getDouble("price"),
                            rows.getDouble("discount"), rows.getDouble("buy_price"),
                            rows.getInt("unit_id"), rows.getDouble("type_value"),
                            expiry == null ? null : expiry.toLocalDate()));
                }
            }
        });
    }

    @Override
    public Map<Integer, Double> alreadyReturnedBySourceLine(
            DocumentType returnType, int sourceId, int excludingReturnId) throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(returnType);
        String sql = "SELECT r.source_line_id, SUM(r.quantity) AS quantity FROM "
                + spec.lineTable() + " r JOIN " + spec.table() + " h ON h." + spec.key()
                + " = r." + DocumentTableSpec.LINE_DOCUMENT
                + " WHERE h.source_invoice_number = ? AND h." + spec.key() + " <> ?"
                + " AND r.source_line_id IS NOT NULL GROUP BY r.source_line_id";
        return withConnection(connection -> {
            Map<Integer, Double> quantities = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, sourceId);
                statement.setInt(2, excludingReturnId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        quantities.put(rows.getInt("source_line_id"), rows.getDouble("quantity"));
                    }
                }
            }
            return quantities;
        });
    }

    @Override
    public Optional<SourceAmounts> sourceAmounts(DocumentType sourceType, int sourceId)
            throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(sourceType);
        String sql = "SELECT total, discount FROM " + spec.table()
                + " WHERE " + spec.key() + " = ?";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, sourceId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new SourceAmounts(
                            rows.getDouble("total"), rows.getDouble("discount")));
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
                + spec.lineItem() + " AS item_id, quantity, price, discount, "
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
                                rows.getDouble("price"), rows.getDouble("discount"),
                                rows.getDouble("buy_price"), rows.getInt("unit_id"),
                                rows.getDouble("type_value"),
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

    @Override
    public Optional<com.hamza.account.type.InvoiceType> sourceInvoiceType(
            DocumentType sourceType, int sourceId) throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(sourceType);
        String sql = "SELECT invoice_type FROM " + spec.table()
                + " WHERE " + spec.key() + " = ?";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, sourceId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    return Optional.ofNullable(com.hamza.account.type.InvoiceType
                            .getInvoiceTypeById(rows.getInt("invoice_type")));
                }
            }
        });
    }

    @Override
    public Optional<Integer> sourcePartyId(DocumentType sourceType, int sourceId)
            throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(sourceType);
        String sql = "SELECT " + spec.party() + " FROM " + spec.table()
                + " WHERE " + spec.key() + " = ?";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, sourceId);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    int partyId = rows.getInt(1);
                    return rows.wasNull() ? Optional.empty() : Optional.of(partyId);
                }
            }
        });
    }

    @Override
    public List<SourceDocument> searchSources(DocumentType sourceType, int documentNumber,
                                             String partyText, int limit) throws DaoException {
        DocumentTableSpec spec = DocumentTableSpec.of(sourceType);
        DocumentTableSpec returnSpec = DocumentTableSpec.of(
                DocumentType.of(sourceType.partyKind(), true));
        // Sold and returned per document, each aggregated over its own line table before the
        // join - the lesson DelegateActivityQuery's ACTIVITY_SQL states: joining first and
        // grouping after multiplies a document by every return line against it.
        String sql = "SELECT d." + spec.key() + " AS number, d." + spec.dateColumn() + " AS doc_date,"
                + " p.name AS party_name, d.total - d.discount AS net,"
                + " COALESCE(sold.units, 0) AS sold_units,"
                + " COALESCE(back.units, 0) AS returned_units"
                + " FROM " + spec.table() + " d"
                + " LEFT JOIN " + spec.partyTable() + " p ON p.id = d." + spec.party()
                + " LEFT JOIN (SELECT " + DocumentTableSpec.LINE_DOCUMENT + " AS doc,"
                + " SUM(quantity * type_value) AS units FROM " + spec.lineTable()
                + " GROUP BY " + DocumentTableSpec.LINE_DOCUMENT + ") sold ON sold.doc = d." + spec.key()
                + " LEFT JOIN (SELECT h.source_invoice_number AS doc,"
                + " SUM(r.quantity * r.type_value) AS units FROM " + returnSpec.lineTable() + " r"
                + " JOIN " + returnSpec.table() + " h ON h." + returnSpec.key() + " = r."
                + DocumentTableSpec.LINE_DOCUMENT
                + " WHERE h.source_invoice_number IS NOT NULL"
                + " GROUP BY h.source_invoice_number) back ON back.doc = d." + spec.key()
                + " WHERE (? = 0 OR d." + spec.key() + " = ?)"
                + " AND (? = '' OR p.name LIKE ?)"
                + " ORDER BY d." + spec.dateColumn() + " DESC, d." + spec.key() + " DESC"
                + " LIMIT ?";
        return withConnection(connection -> {
            List<SourceDocument> found = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, documentNumber);
                statement.setInt(2, documentNumber);
                String party = partyText == null ? "" : partyText;
                statement.setString(3, party);
                statement.setString(4, "%" + party + "%");
                statement.setInt(5, Math.max(limit, 1));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        double sold = rows.getDouble("sold_units");
                        double back = rows.getDouble("returned_units");
                        Date date = rows.getDate("doc_date");
                        found.add(new SourceDocument(rows.getInt("number"),
                                date == null ? null : date.toLocalDate(),
                                rows.getString("party_name"), rows.getDouble("net"),
                                sold > 0 && back >= sold - 0.000_001));
                    }
                }
            }
            return found;
        });
    }

    /**
     * Joins the save's transaction when there is one, and borrows a pooled connection
     * when there is not - which is exactly what {@link ConnectionManager#acquire()}
     * already does, and what this repository's two kinds of caller need:
     * {@code ReturnGuard} and {@code ReturnCostResolver} read from inside
     * {@code InvoiceSaveService.persist}'s transaction, while
     * {@code ReturnLineSelectionService}, {@code ReturnedStatusService} and
     * {@code ReturnReasonReportService} read from the UI thread with no transaction
     * open at all.
     * <p>
     * There was a {@code requireTransaction} guard here, copied from
     * {@link com.hamza.account.features.invoice.JdbcInvoiceStockRepository}, which
     * broke every one of the read-only callers the moment the picker dialog was
     * opened. That guard belongs where it came from and not here: the stock
     * repository takes {@code FOR UPDATE} row locks, which are meaningless outside a
     * transaction, and every statement in this file is a plain read.
     */
    private static <T> T withConnection(SqlWork<T> work) throws DaoException {
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            return work.run(connection);
        } catch (SQLException e) {
            throw new DaoException("Could not read the source document", e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
