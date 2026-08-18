package com.hamza.account.features.returns;

import com.hamza.account.document.DocumentType;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.List;
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

    private final ReturnableRepository repository;

    public ReturnCostResolver(ReturnableRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public <T extends BasePurchasesAndSales> void apply(
            DocumentType returnType,
            List<? extends BasePurchasesAndSales> originalRows,
            List<T> persistedLines) throws DaoException {
        if (!returnType.isReturn() || originalRows.size() != persistedLines.size()) {
            return;
        }
        DocumentType sourceType = returnType.reverses();
        for (int index = 0; index < originalRows.size(); index++) {
            BasePurchasesAndSales original = originalRows.get(index);
            if (original == null || original.getSourceLineId() <= 0) {
                continue;
            }
            ReturnableRepository.SourceLine source = repository
                    .lineById(sourceType, original.getSourceLineId())
                    .orElseThrow(() -> new BusinessRuleException(LanguageManager.getInstance()
                            .getString("return.error.source.line.missing",
                                    original.getSourceLineId())));
            T persisted = persistedLines.get(index);
            requireSameTermsAsSold(persisted, source);
            persisted.setBuy_price(source.buyPrice());
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

    private static String money(double value) {
        return MoneyMath.text(MoneyMath.decimal(value));
    }

    private static String message(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
