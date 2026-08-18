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
            requireNotDearerThanSold(persisted, source);
            persisted.setBuy_price(source.buyPrice());
        }
    }

    /**
     * A return may refund less than was charged - a restocking fee, or a partial
     * goodwill credit, are real things - but never more. The picker fills the price in
     * from the source line, and the price column is left editable for the free-return
     * case, so without this a line sold at 120 can be refunded at 150 and the
     * difference walks out of the till.
     * <p>
     * Enforced here rather than only by locking the cell, for the reason
     * {@code CLAUDE.md} states about the whole authorization layer: hiding or disabling
     * a control is a hint, not enforcement.
     */
    private static void requireNotDearerThanSold(
            BasePurchasesAndSales line, ReturnableRepository.SourceLine source)
            throws BusinessRuleException {
        if (line.getPrice() - source.price() <= PRICE_EPSILON) {
            return;
        }
        throw new BusinessRuleException(LanguageManager.getInstance().getString(
                "return.error.price.above.source",
                MoneyMath.text(MoneyMath.decimal(line.getPrice())),
                MoneyMath.text(MoneyMath.decimal(source.price()))));
    }
}
