package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.ConnectionManager;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Refuses to delete an invoice that has been returned against.
 * <p>
 * Not a foreign key, deliberately. {@code total_sales_re.source_invoice_number} is
 * {@code ON DELETE SET NULL} (see {@code V16__return_source.sql}) and stays that way:
 * making it {@code RESTRICT} would also block <em>editing</em> a returned-against sale,
 * because {@code DocumentLineDao} synchronizes lines by deleting the removed ones, and
 * a removed line referenced by a return would fail the whole edit with a raw
 * constraint violation. So the database keeps the forgiving key and the application
 * does the refusing, which is also the only place that can say <em>why</em> in Arabic.
 * <p>
 * What goes wrong without it is the stock, not the link. A sale takes goods out and
 * its return puts them back; deleting only the sale removes the stock-out and leaves
 * the stock-in, so every returned item's balance rises by the returned quantity out of
 * nothing. {@code WipeCatalog.SALES} requires {@code salesReturns} for the same reason,
 * covering the "delete data" screen the way this covers the delete button.
 */
public final class ReturnLinkGuard {

    private ReturnLinkGuard() {
    }

    /**
     * @param sourceType the invoice family being deleted - {@code SALES} or {@code PURCHASE}
     * @param ids        the invoice numbers about to be deleted
     */
    public static void requireNoReturns(DocumentType sourceType, Integer... ids)
            throws DaoException {
        if (sourceType == null || sourceType.isReturn() || ids == null || ids.length == 0) {
            return;
        }
        List<Integer> blocked = returnedInvoiceNumbers(sourceType, ids);
        if (blocked.isEmpty()) {
            return;
        }
        StringJoiner numbers = new StringJoiner("، ");
        blocked.forEach(id -> numbers.add(String.valueOf(id)));
        throw new BusinessRuleException(LanguageManager.getInstance()
                .getString("return.error.source.has.returns", numbers.toString()));
    }

    /** Which of {@code ids} already have a return pointing at them. */
    private static List<Integer> returnedInvoiceNumbers(DocumentType sourceType, Integer[] ids)
            throws DaoException {
        DocumentTableSpec returnSpec =
                DocumentTableSpec.of(DocumentType.of(sourceType.partyKind(), true));
        StringJoiner placeholders = new StringJoiner(",");
        for (int index = 0; index < ids.length; index++) {
            placeholders.add("?");
        }
        String sql = "SELECT DISTINCT source_invoice_number FROM " + returnSpec.table()
                + " WHERE source_invoice_number IN (" + placeholders + ")"
                + " ORDER BY source_invoice_number";

        Connection connection = null;
        try {
            connection = ConnectionManager.acquire();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < ids.length; index++) {
                    statement.setObject(index + 1, ids[index]);
                }
                try (ResultSet rows = statement.executeQuery()) {
                    List<Integer> blocked = new ArrayList<>();
                    while (rows.next()) {
                        blocked.add(rows.getInt(1));
                    }
                    return blocked;
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Could not check whether the invoice has returns", e);
        } finally {
            ConnectionManager.release(connection);
        }
    }
}
