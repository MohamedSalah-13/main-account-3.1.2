package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.Map;

/**
 * Database boundary {@link ReturnGuard} reads through - what a source invoice actually
 * sold, and what has already been returned against it.
 * <p>
 * In the shape of {@code InvoiceStockRepository}: an interface here, a JDBC
 * implementation that joins whatever transaction is already open, and nothing about
 * {@code ReturnGuard} or {@link ReturnEligibility} knows it is talking to MySQL.
 */
public interface ReturnableRepository {

    /** Whether a document of this type and id exists at all - a return naming one that does not is refused before anything else. */
    boolean sourceExists(DocumentType sourceType, int sourceId) throws DaoException;

    /**
     * The source invoice's lines, summed to one row per item in base units. Two lines
     * of the same item in different units are one entry here, which is what lets a
     * return line in a different unit still be checked against what was sold.
     */
    List<SoldLine> sourceLines(DocumentType sourceType, int sourceId) throws DaoException;

    /**
     * What has already been returned against this source invoice, across every return
     * that names it - summed per item, in base units, and excluding
     * {@code excludingReturnId} so that re-saving an existing return is checked against
     * every return <em>but</em> itself. {@code 0} excludes nothing, which is what a
     * return being created for the first time wants.
     */
    Map<Integer, Double> alreadyReturnedBaseQuantities(
            DocumentType returnType, int sourceId, int excludingReturnId) throws DaoException;

    /** One item's quantity on the source invoice, already converted to base units. */
    record SoldLine(int itemId, double baseQuantity) {
    }
}
