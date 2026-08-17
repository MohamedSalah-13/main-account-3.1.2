package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /**
     * One exact original line - what {@code ReturnCostResolver} reads to recover the
     * price and cost it was sold or bought at, rather than today's. Empty when the id
     * names no line, which is refused rather than silently ignored: a return that
     * claims to reverse a line that does not exist has a data problem worth surfacing,
     * not a cost worth guessing at.
     *
     * @param itemId    the item the line is for
     * @param price     what this line charged per selling unit
     * @param buyPrice  what this line's items cost, in the same unit - {@code 0} for a
     *                  {@code purchase} line, which is itself the cost and keeps none
     *                  of its own; only a {@code sales} line carries one to preserve
     * @param unitId    the unit the line was in
     * @param typeValue that unit's factor into the item's base unit, on this line
     */
    Optional<SourceLine> lineById(DocumentType sourceType, int sourceLineId) throws DaoException;

    /**
     * The expiry batches a source invoice actually sold or bought of one item - what a
     * return of it may honestly claim to be returning, in place of a date the user
     * typed with nothing behind it. Each batch's quantity is what the source invoice
     * recorded, <em>not</em> reduced by what other returns against it have already
     * taken - {@link ReturnGuard}'s item-total check is what stays authoritative for
     * quantity, exactly as {@code InvoiceExpiryService}'s own batch list is already a
     * picker rather than the final word for an ordinary sale. Two batches of the same
     * expiry date are summed into one.
     */
    List<ExpiryBatch> sourceExpiryBatches(DocumentType sourceType, int sourceId, int itemId)
            throws DaoException;

    /** One item's quantity on the source invoice, already converted to base units. */
    record SoldLine(int itemId, double baseQuantity) {
    }

    record SourceLine(int itemId, double price, double buyPrice,
                      int unitId, double typeValue, LocalDate expirationDate) {
    }

    record ExpiryBatch(LocalDate expirationDate, double baseQuantity) {
    }
}
