package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
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

    /**
     * Whether a document of this type and id exists at all - a return naming one that
     * does not is refused before anything else. Takes no lock: the picker calls this
     * from the UI thread with no transaction open.
     */
    boolean sourceExists(DocumentType sourceType, int sourceId) throws DaoException;

    /**
     * The same question, asked with the source's row locked for the rest of the
     * transaction - what {@link ReturnGuard} uses at save time so that "how much is
     * left to return" cannot be read by two tills at once. Answers exactly as
     * {@link #sourceExists} does; only the lock differs.
     */
    boolean lockSource(DocumentType sourceType, int sourceId) throws DaoException;

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

    /**
     * Every line of a source invoice, individually - what a "return from this invoice"
     * picker shows one row per, as opposed to {@link #sourceLines}, which sums them to
     * one row per item for {@link ReturnGuard}'s quantity check. Order is the order the
     * invoice itself lists them in.
     */
    List<SourceLineRow> rawLines(DocumentType sourceType, int sourceId) throws DaoException;

    /**
     * The delegate on a sales invoice - what the commission a sales return reverses
     * should default to, rather than whichever delegate the return screen happens to
     * have selected. Sales-only: {@code total_buy} has no {@code delegate_id} column
     * at all ({@link DocumentType#hasDelegate()} is false for the purchase side), so
     * this is never asked of a purchase.
     */
    Optional<Integer> sourceDelegateId(int sourceSalesInvoiceNumber) throws DaoException;

    /**
     * How the source invoice was settled. A cash invoice was paid in full at the
     * counter, so a return of it has an account balance of exactly zero to reverse -
     * settling that return on account instead would create a debt out of a transaction
     * that had already been closed. Empty when the invoice does not exist.
     */
    Optional<com.hamza.account.type.InvoiceType> sourceInvoiceType(
            DocumentType sourceType, int sourceId) throws DaoException;

    /**
     * Whose invoice it is - the customer or supplier on the source document. A return
     * has to go back to the same party: goods bought from one supplier cannot be
     * returned to another, or the one actually owed is never credited and the other is
     * credited for goods they never supplied. Empty when the invoice does not exist.
     */
    Optional<Integer> sourcePartyId(DocumentType sourceType, int sourceId) throws DaoException;

    /**
     * How many returns of one type were entered for each reason in a date range, and
     * what they totalled - what a reasons report groups by. A return with no reason
     * recorded (every one before an entry screen asked for one, and any entered
     * without picking one) reports under a {@code null} {@link ReasonCount#reason()}.
     */
    List<ReasonCount> reasonCounts(DocumentType returnType, LocalDate from, LocalDate to)
            throws DaoException;

    /** One item's quantity on the source invoice, already converted to base units. */
    record SoldLine(int itemId, double baseQuantity) {
    }

    /** One exact line, as {@link #rawLines} lists it - {@link SourceLine} plus its own id and quantity. */
    record SourceLineRow(int lineId, int itemId, double quantity, double price,
                         double discount, double buyPrice, int unitId, double typeValue,
                         LocalDate expirationDate) {
    }

    /**
     * {@code quantity} and {@code discount} are here so a return can be held to the
     * <em>net</em> the line actually charged, not just its unit price: a line discount
     * belongs to the whole line, so returning part of it refunds its proportional share.
     */
    record SourceLine(int itemId, double quantity, double price, double discount,
                      double buyPrice, int unitId, double typeValue,
                      LocalDate expirationDate) {
    }

    record ExpiryBatch(LocalDate expirationDate, double baseQuantity) {
    }

    /** {@code reason} is {@code null} for returns entered with none recorded. */
    record ReasonCount(ReturnReason reason, int count, BigDecimal total) {
    }
}
