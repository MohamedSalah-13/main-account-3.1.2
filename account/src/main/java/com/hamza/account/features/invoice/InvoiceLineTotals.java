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

    public static InvoiceLineTotals from(List<? extends BasePurchasesAndSales> lines) {
        if (lines == null || lines.isEmpty()) {
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
