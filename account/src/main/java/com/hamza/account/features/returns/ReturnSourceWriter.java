package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Stamps {@code source_invoice_number} onto a return's header, still inside the
 * transaction {@code InvoiceSaveService.persist} is already in - the second write after
 * the header's own insert/update, in the shape {@code writeStockMovements} already is
 * in that class.
 * <p>
 * A dedicated statement rather than a new column on {@code InvoiceBuy.object_Totals}:
 * that seam is generic across all four {@code impl_dataInterface} implementations
 * (see {@code CLAUDE.md}'s note on {@code DataInterface}), and widening it for two
 * columns that only the return families have would touch the sales and purchase sides
 * for nothing. This writes directly through {@link DocumentTableSpec}, the same way
 * {@link JdbcReturnableRepository} reads through it.
 */
public final class ReturnSourceWriter {

    public void writeSource(DocumentType returnType, int invoiceNumber,
                            int sourceInvoiceNumber) throws DaoException {
        if (sourceInvoiceNumber <= 0) {
            return;
        }
        DocumentTableSpec spec = DocumentTableSpec.of(returnType);
        String sql = "UPDATE " + spec.table() + " SET source_invoice_number = ? WHERE "
                + spec.key() + " = ?";
        int affected = withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, sourceInvoiceNumber);
                statement.setInt(2, invoiceNumber);
                return statement.executeUpdate();
            }
        });
        if (affected != 1) {
            throw new DaoException("Could not link the return to its source invoice");
        }
    }

    private static <T> T withConnection(SqlWork<T> work) throws DaoException {
        if (!ConnectionManager.inTransaction()) {
            throw new DaoException("Writing a return's source requires an active transaction");
        }
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            return work.run(connection);
        } catch (SQLException e) {
            throw new DaoException("Could not link the return to its source invoice", e);
        } finally {
            ConnectionManager.release(connection);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
