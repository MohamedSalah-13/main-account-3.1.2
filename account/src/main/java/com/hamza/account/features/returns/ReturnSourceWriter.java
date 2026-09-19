package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Stamps {@code source_invoice_number} and {@code return_reason} onto a return's
 * header - including a reason with <em>no</em> source, which is the ordinary free return and
 * used to be dropped on the floor: this class returned before writing anything when the source
 * was absent, so every return entered without an invoice reached the reasons report as "no
 * reason given" however carefully the person had chosen one. The column is nullable for exactly
 * that case, and the two are written together because they are one answer about one document.
 * The header, still inside the transaction {@code InvoiceSaveService.persist} is already
 * in - the second write after the header's own insert/update, in the shape
 * {@code writeStockMovements} already is in that class.
 * <p>
 * A dedicated statement rather than new columns on {@code InvoiceBuy.object_Totals}:
 * that seam is generic across all four {@code impl_dataInterface} implementations
 * (see {@code CLAUDE.md}'s note on {@code DataInterface}), and two columns that only
 * the return families have would touch the sales and purchase sides for nothing. This
 * writes directly through {@link DocumentTableSpec}, the same way
 * {@link JdbcReturnableRepository} reads through it.
 */
public final class ReturnSourceWriter {

    public void writeSource(DocumentType returnType, int invoiceNumber,
                            int sourceInvoiceNumber, ReturnReason reason) throws DaoException {
        if (sourceInvoiceNumber <= 0 && reason == null) {
            return;
        }
        DocumentTableSpec spec = DocumentTableSpec.of(returnType);
        String sql = "UPDATE " + spec.table()
                + " SET source_invoice_number = ?, return_reason = ? WHERE "
                + spec.key() + " = ?";
        int affected = withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                // A free return names no invoice, and the column is nullable exactly for it.
                if (sourceInvoiceNumber > 0) {
                    statement.setInt(1, sourceInvoiceNumber);
                } else {
                    statement.setNull(1, java.sql.Types.INTEGER);
                }
                statement.setString(2, reason == null ? null : reason.storedValue());
                statement.setInt(3, invoiceNumber);
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
