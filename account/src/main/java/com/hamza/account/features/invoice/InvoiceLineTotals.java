package com.hamza.account.features.invoice;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;

import java.math.BigDecimal;
import java.util.List;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

/** Pure summary of the editable invoice rows, independent from JavaFX controls. */
public record InvoiceLineTotals(int lineCount, double quantity,
                                BigDecimal grossAmount, BigDecimal discountAmount,
                                BigDecimal netAmount, boolean hasInvalidLine) {

    public InvoiceLineTotals {
        grossAmount = MoneyMath.money(grossAmount);
        discountAmount = MoneyMath.money(discountAmount);
        netAmount = MoneyMath.money(netAmount);
    }

    /** Compatibility constructor for existing callers while models still expose doubles. */
    public InvoiceLineTotals(int lineCount, double quantity, double gross,
                             double discount, double net, boolean hasInvalidLine) {
        this(lineCount, quantity, MoneyMath.decimal(gross), MoneyMath.decimal(discount),
                MoneyMath.decimal(net), hasInvalidLine);
    }

    /**
     * Whether a row is an entry placeholder rather than a line of the invoice.
     *
     * <p>The quick screen keeps a trailing empty row for the scanner operator to type
     * into, and that row is a control, not a sale: it carries no item, no price and no
     * quantity. Counting it made {@link #hasInvalidLine()} permanently true - which is
     * bound to the save buttons' {@code disable} - so the quick invoice could never be
     * saved at all. It is excluded here, once, rather than at each of the six bindings
     * that read a total.
     *
     * <p>A row that names no item cannot be saved either way: {@code InvoiceLineService}
     * refuses a draft whose item id is not positive, and {@code InvoiceLineAssembler}
     * refuses one whose item is null. So nothing real is ever hidden by this.
     */
    public static boolean isPlaceholder(BasePurchasesAndSales line) {
        return line == null || line.getItems() == null || line.getItems().getId() <= 0;
    }

    /** The rows that are actually lines of the invoice - see {@link #isPlaceholder}. */
    public static <T extends BasePurchasesAndSales> List<T> realLines(List<T> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream().filter(line -> !isPlaceholder(line)).toList();
    }

    public static InvoiceLineTotals from(List<? extends BasePurchasesAndSales> source) {
        List<? extends BasePurchasesAndSales> lines = realLines(source);
        if (lines.isEmpty()) {
            return new InvoiceLineTotals(0, 0, MoneyMath.ZERO, MoneyMath.ZERO,
                    MoneyMath.ZERO, false);
        }

        double quantity = roundToTwoDecimalPlaces(
                lines.stream().mapToDouble(BasePurchasesAndSales::getQuantity).sum());
        BigDecimal gross = MoneyMath.money(lines.stream()
                .map(BasePurchasesAndSales::getTotal)
                .map(MoneyMath::decimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal discount = MoneyMath.money(lines.stream()
                .map(BasePurchasesAndSales::getDiscount)
                .map(MoneyMath::decimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        boolean invalid = lines.stream().anyMatch(
                line -> line.getPrice() <= 0 || line.getQuantity() <= 0);
        return new InvoiceLineTotals(lines.size(), quantity, gross, discount,
                MoneyMath.subtract(gross, discount), invalid);
    }

    public double gross() {
        return MoneyMath.asDouble(grossAmount);
    }

    public double discount() {
        return MoneyMath.asDouble(discountAmount);
    }

    public double net() {
        return MoneyMath.asDouble(netAmount);
    }
}
