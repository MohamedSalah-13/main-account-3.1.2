package com.hamza.account.features.invoice;

import com.hamza.account.features.returns.ReturnableRepository;
import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A document's lines as typed in its currency, turned into the base lines that are stored for them
 * (V83, docs/currency-plan.md §15 ق-د٣ and ق-د٥).
 * <p>
 * Each line keeps what was typed - its price and its discount - in {@code priceForeign} and
 * {@code discountForeign}, and carries the base in the columns every reader already reads: the price
 * and the discount times the rate, rounded to money, and the total worked out from them exactly as a
 * line's total always is. So the cost, the profit and the stock balance never learn that a currency was
 * involved.
 * <p>
 * <b>A return line picked from its invoice is the exception, and the reason is arithmetic.</b> Its base
 * price is that invoice line's own base price, and its base discount the share of that line's base
 * discount the quantity takes - the very figures {@code ReturnCostResolver} holds it to. Converting the
 * typed dollars instead would round a share to a cent in dollars, which at 48 to the dollar is 24
 * piastres, against a check that tolerates half of one: every partial return would be refused. The
 * typed figures are still what is written beside them, and are what the paper prints.
 * <p>
 * The lines are fresh copies made through the document family's own factory: the rows handed in are the
 * screen's, and the save runs off the JavaFX thread.
 */
public final class ForeignDocumentLines {

    /** The base figures of one line of the invoice a return names, by that line's id. */
    @FunctionalInterface
    public interface SourceLines {
        Optional<ReturnableRepository.SourceLine> lineById(int sourceLineId) throws DaoException;
    }

    private ForeignDocumentLines() {
    }

    /**
     * @param typed       the lines as the screen holds them, in the document's currency
     * @param rate        base units per one unit of that currency
     * @param sourceLines the lines of the invoice a return names, or {@code null} for anything else
     */
    public static <T extends BasePurchasesAndSales> List<T> toBase(
            List<? extends BasePurchasesAndSales> typed, BigDecimal rate, SourceLines sourceLines,
            InvoiceLineAssembler.LineFactory<T> factory) throws DaoException {
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(factory, "factory");
        List<T> result = new ArrayList<>(typed == null ? 0 : typed.size());
        if (typed == null) {
            return result;
        }
        for (BasePurchasesAndSales row : typed) {
            if (row == null || row.getItems() == null || row.getUnitsType() == null) {
                throw new UserValidationException(LanguageManager.getInstance().getString("invoice.line.error.invalid"));
            }
            BigDecimal typedPrice = MoneyMath.money(row.getPrice());
            BigDecimal typedDiscount = MoneyMath.money(row.getDiscount());
            double quantity = row.getQuantity();

            Optional<ReturnableRepository.SourceLine> source = row.getSourceLineId() > 0 && sourceLines != null
                    ? sourceLines.lineById(row.getSourceLineId())
                    : Optional.empty();
            BigDecimal price;
            BigDecimal discount;
            if (source.isPresent()) {
                price = MoneyMath.money(source.get().price());
                discount = shareOf(source.get(), quantity);
            } else {
                price = ForeignDocumentFigures.toBase(typedPrice, rate);
                discount = ForeignDocumentFigures.toBase(typedDiscount, rate);
            }
            BigDecimal total = MoneyMath.multiply(price.doubleValue(), quantity);

            T line = factory.create(row.getId(), row.getInvoiceNumber(), row.getItems().getId(),
                    price.doubleValue(), quantity, discount.doubleValue(), total.doubleValue(),
                    row.getUnitsType(), row.getItems(), row.getExpiration_date());
            InvoiceLineAssembler.preserveHistoricalCost(row, line);
            InvoiceLineAssembler.preserveSourceLine(row, line);
            line.setPriceForeign(typedPrice);
            line.setDiscountForeign(typedDiscount);
            result.add(line);
        }
        return List.copyOf(result);
    }

    /**
     * The part of a source line's discount that {@code quantity} of it takes - the same arithmetic as
     * {@code ReturnCostResolver.proportionalDiscount} and {@code ReturnableLineSelection.discountShareFor}.
     */
    static BigDecimal shareOf(ReturnableRepository.SourceLine source, double quantity) {
        if (source.discount() == 0 || source.quantity() <= 0) {
            return MoneyMath.ZERO;
        }
        return MoneyMath.multiply(source.discount(), quantity / source.quantity());
    }
}
