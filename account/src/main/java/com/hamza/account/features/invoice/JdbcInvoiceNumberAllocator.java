package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/** MySQL atomic sequence; callers normally invoke it inside {@code TransactionTemplate}. */
public final class JdbcInvoiceNumberAllocator implements InvoiceNumberAllocator {

    private static final String ADVANCE = "UPDATE document_sequences "
            + "SET current_value=LAST_INSERT_ID(current_value + 1) WHERE document_type=?";
    private static final String READ = "SELECT LAST_INSERT_ID()";

    @Override
    public int next(DocumentType documentType) throws DaoException {
        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            try (PreparedStatement statement = connection.prepareStatement(ADVANCE)) {
                statement.setString(1, documentType.name());
                if (statement.executeUpdate() != 1) {
                    throw new DaoException("عداد أرقام " + documentType.label() + " غير موجود");
                }
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(READ)) {
                if (!result.next()) {
                    throw new DaoException("تعذر قراءة رقم " + documentType.label());
                }
                long number = result.getLong(1);
                if (number <= 0 || number > Integer.MAX_VALUE) {
                    throw new DaoException("رقم " + documentType.label() + " خارج النطاق المدعوم");
                }
                return (int) number;
            }
        } catch (DaoException e) {
            throw e;
        } catch (Exception e) {
            throw new DaoException("تعذر توليد رقم " + documentType.label(), e);
        } finally {
            ConnectionManager.release(connection);
        }
    }
}
