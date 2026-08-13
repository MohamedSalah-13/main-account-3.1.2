package com.hamza.account.features.invoice;

import com.hamza.account.model.base.BasePurchasesAndSales;

import java.util.List;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

/** Pure summary of the editable invoice rows, independent from JavaFX controls. */
public record InvoiceLineTotals(int lineCount, double quantity, double gross,
                                double discount, double net, boolean hasInvalidLine) {

    public static InvoiceLineTotals from(List<? extends BasePurchasesAndSales> lines) {
        if (lines == null || lines.isEmpty()) {
            return new InvoiceLineTotals(0, 0, 0, 0, 0, false);
        }

        double quantity = roundToTwoDecimalPlaces(
                lines.stream().mapToDouble(BasePurchasesAndSales::getQuantity).sum());
        double gross = roundToTwoDecimalPlaces(
                lines.stream().mapToDouble(BasePurchasesAndSales::getTotal).sum());
        double discount = roundToTwoDecimalPlaces(
                lines.stream().mapToDouble(BasePurchasesAndSales::getDiscount).sum());
        boolean invalid = lines.stream().anyMatch(
                line -> line.getPrice() <= 0 || line.getQuantity() <= 0);
        return new InvoiceLineTotals(lines.size(), quantity, gross, discount,
                roundToTwoDecimalPlaces(gross - discount), invalid);
    }
}
