package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentType;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Restores a return line's original cost after {@code InvoiceLineAssembler} has built
 * it, closing the gap {@code CLAUDE.md} documents on {@code SalesInvoiceReturn}:
 * {@code object_TableData} prices a return's cost from {@code itemsModel.getBuyPrice()}
 * - the item's price <em>today</em> - because that is the only cost a brand-new line
 * can possibly have. A return is not a new line; it is a reversal of one that already
 * happened, and its cost is whatever the sale it reverses actually cost, not whatever
 * the item happens to cost when the return is entered.
 * <p>
 * Reads {@link BasePurchasesAndSales#getSourceLineId()} on the <em>original</em>,
 * still-attached rows the screen submitted - {@code InvoiceLineAssembler} does not
 * carry that field through to the detached line it builds, so this runs after
 * assembly and writes the correction onto the assembled result, matched by position:
 * {@code InvoiceLineAssembler.assemble} adds exactly one output row per input row, in
 * order, or throws before adding any - the two lists are always the same length.
 * <p>
 * A no-op for anything that is not a return, and for any line the screen did not tie
 * to a source line - which is every line before a "return from this invoice" entry
 * flow exists to set {@link BasePurchasesAndSales#getSourceLineId()} at all.
 */
public final class ReturnCostResolver {

    /** Half a piastre - below this two prices are the same price. */
    private static final double PRICE_EPSILON = 0.005;

    private static final double QUANTITY_EPSILON = 0.000_001;

    private final ReturnableRepository repository;

    public ReturnCostResolver(ReturnableRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * @param sourceInvoiceNumber the invoice this return names, or {@code 0} for a
     *                            return entered without one. It is what tells a line
     *                            with no source apart from a whole document with none -
     *                            see {@link #requireLineComesFromTheSource}.
     * @param excludingReturnId   the return's own id when one already saved is being saved
     *                            again, so its stored lines are not counted against it;
     *                            {@code 0} for a new one
     */
    public <T extends BasePurchasesAndSales> void apply(
            DocumentType returnType, int sourceInvoiceNumber, int excludingReturnId,
            List<? extends BasePurchasesAndSales> originalRows,
            List<T> persistedLines) throws DaoException {
        if (!returnType.isReturn() || originalRows.size() != persistedLines.size()) {
            return;
        }
        DocumentType sourceType = returnType.reverses();
        Map<Integer, Double> takenByLine = new HashMap<>();
        Map<Integer, Double> returnedByLine = null;
        for (int index = 0; index < originalRows.size(); index++) {
            BasePurchasesAndSales original = originalRows.get(index);
            if (original == null || original.getSourceLineId() <= 0) {
                requireLineComesFromTheSource(sourceInvoiceNumber);
                // A free return is given no offer (ق-ع١١): there is no line it could have come from.
                persistedLines.get(index).setOfferId(null);
                persistedLines.get(index).setOfferDiscount(BigDecimal.ZERO);
                continue;
            }
            int sourceLineId = original.getSourceLineId();
            ReturnableRepository.SourceLine source = repository
                    .lineById(sourceType, sourceInvoiceNumber, sourceLineId)
                    .orElseThrow(() -> new BusinessRuleException(LanguageManager.getInstance()
                            .getString("return.error.source.line.missing", sourceLineId)));
            T persisted = persistedLines.get(index);
            requireSameItemAsSold(persisted, source);
            requireSameTermsAsSold(persisted, source);
            if (returnedByLine == null) {
                returnedByLine = repository.alreadyReturnedBySourceLine(
                        returnType, sourceInvoiceNumber, excludingReturnId);
            }
            double taken = takenByLine.merge(sourceLineId, persisted.getQuantity(), Double::sum);
            requireWithinTheLine(source, returnedByLine.getOrDefault(sourceLineId, 0.0), taken);
            persisted.setBuy_price(source.buyPrice());
            carryTheOffer(persisted, source);
        }
    }

    /**
     * The source line's offer, and this line's share of what the offer gave - in the proportion its share of
     * the whole discount is taken, by the same arithmetic (V85, ق-ع١١). Written from the source line, never
     * from the screen: what an offer gave back on a return is a figure a report of the offers will read.
     */
    static void carryTheOffer(BasePurchasesAndSales persisted, ReturnableRepository.SourceLine source) {
        if (source.offerId() == null || source.offerDiscount() == 0 || source.quantity() <= 0) {
            persisted.setOfferId(null);
            persisted.setOfferDiscount(BigDecimal.ZERO);
            return;
        }
        persisted.setOfferId(source.offerId());
        persisted.setOfferDiscount(MoneyMath.multiply(source.offerDiscount(),
                persisted.getQuantity() / source.quantity()));
    }

    /**
     * A line is returned against <em>its</em> line, up to what that line sold.
     * <p>
     * {@code ReturnGuard} counts per item across the whole invoice, which is the right
     * question for "did this invoice sell that much" and the wrong one for money: an invoice
     * listing one item twice - five at 100, five at 60 - let all ten come back against the
     * line at 100. Ten of ten sold, the price equal to the line it named, and 200 refunded
     * that nobody ever paid. The price belongs to a line, so the quantity it may be refunded
     * for belongs to that line too.
     * <p>
     * In the line's own unit: {@link #requireSameTermsAsSold} has already held the return to
     * it. {@code taken} is everything this document asks of the line so far, so the same
     * line picked twice on one return is one request, not two that each fit.
     */
    private static void requireWithinTheLine(ReturnableRepository.SourceLine source,
                                             double alreadyReturned, double taken)
            throws BusinessRuleException {
        double remaining = source.quantity() - alreadyReturned;
        if (taken - remaining > QUANTITY_EPSILON) {
            throw new BusinessRuleException(message("return.error.exceeds.line",
                    quantity(Math.max(remaining, 0)), quantity(taken)));
        }
    }

    /**
     * The line a return row points at has to be a line of the item it returns. Nothing on
     * the screen can produce the mismatch - the picker tags a row with the line it was built
     * from - but this is the enforcement, and a price and a cost read from some other item's
     * line are wrong in a way every later check would pass.
     */
    private static void requireSameItemAsSold(
            BasePurchasesAndSales line, ReturnableRepository.SourceLine source)
            throws BusinessRuleException {
        if (line.getItems() != null && line.getItems().getId() != source.itemId()) {
            throw new BusinessRuleException(message("return.error.line.item.differs"));
        }
    }

    /**
     * Once a return names an invoice, every line on it has to come from that invoice.
     * <p>
     * A line typed in by barcode carries no source line, and this class used to read
     * that as "a free return - nothing to check it against" and skip it. That is true
     * of a return with no source at all; it is exactly false of one line on a return
     * that does have a source, and it was a hole wide enough to walk money out of:
     * pick 9 of 10 from the invoice so they are locked at the purchase price, then add
     * the tenth by barcode at any price you like. Bought 10 at 100, returned all 10
     * for 910, and the 90 is simply gone.
     * <p>
     * {@code ReturnGuard} does not catch it either - it checks quantity per item, and
     * 9 + 1 against 10 sold is perfectly in order. Only the price was wrong, and only
     * on the line nothing was checking.
     */
    private static void requireLineComesFromTheSource(int sourceInvoiceNumber)
            throws BusinessRuleException {
        if (sourceInvoiceNumber > 0) {
            throw new BusinessRuleException(message("return.error.line.not.from.source"));
        }
    }

    /**
     * A return of a known line refunds <em>exactly</em> what that line charged: the same
     * price, the same unit, and the proportional share of the same line discount.
     * <p>
     * Not "no more than" - equal. Refunding less is as wrong as refunding more, just
     * quieter: it hands the customer back part of their money and silently keeps the
     * rest as revenue on goods the shop now has back on the shelf. Whatever a shop
     * wants to withhold - a restocking fee, a handling charge - is its own line or its
     * own document, not a quietly shrunken refund with no record of the difference.
     * <p>
     * The unit has to match too. The price is per unit, so returning in cartons at the
     * piece price (or the reverse) refunds a different amount per piece while still
     * passing a bare price comparison.
     * <p>
     * Enforced here rather than only by locking the cells, for the reason
     * {@code CLAUDE.md} states about the whole authorization layer: hiding or disabling
     * a control is a hint, not enforcement.
     */
    private static void requireSameTermsAsSold(
            BasePurchasesAndSales line, ReturnableRepository.SourceLine source)
            throws BusinessRuleException {
        if (line.getUnitsType() != null
                && line.getUnitsType().getUnit_id() != source.unitId()) {
            throw new BusinessRuleException(message("return.error.unit.differs"));
        }
        if (Math.abs(line.getPrice() - source.price()) > PRICE_EPSILON) {
            throw new BusinessRuleException(message("return.error.price.differs",
                    money(line.getPrice()), money(source.price())));
        }
        double expectedDiscount = proportionalDiscount(line.getQuantity(), source);
        if (Math.abs(line.getDiscount() - expectedDiscount) > PRICE_EPSILON) {
            throw new BusinessRuleException(message("return.error.discount.differs",
                    money(line.getDiscount()), money(expectedDiscount)));
        }
    }

    /**
     * The share of the source line's discount that belongs to the quantity being
     * returned - a line discount covers the whole line, so returning 2 of 5 takes back
     * two fifths of it. Mirrors {@code ReturnableLineSelection.discountShareFor}, which
     * is what fills the value in; this is the half that checks it.
     */
    private static double proportionalDiscount(
            double returnedQuantity, ReturnableRepository.SourceLine source) {
        if (source.discount() == 0 || source.quantity() <= 0) {
            return 0;
        }
        return MoneyMath.asDouble(MoneyMath.multiply(
                source.discount(), returnedQuantity / source.quantity()));
    }

    private static String quantity(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String money(double value) {
        return MoneyMath.text(MoneyMath.decimal(value));
    }

    private static String message(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
