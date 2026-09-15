package com.hamza.account.features.invoice;

import com.hamza.account.finance.MoneyMath;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The figures shown around a saved invoice, calculated outside the JavaFX controller. */
public record InvoiceDetailsSummary(
        int lineCount,
        int distinctItemCount,
        BigDecimal quantity,
        BigDecimal linesTotal,
        BigDecimal linesDiscount,
        BigDecimal linesNet,
        BigDecimal invoiceDiscount,
        BigDecimal invoiceNet,
        BigDecimal paid,
        BigDecimal remaining,
        Optional<BigDecimal> totalCost,
        Optional<BigDecimal> profit) {

    public InvoiceDetailsSummary {
        quantity = Objects.requireNonNull(quantity, "quantity");
        linesTotal = Objects.requireNonNull(linesTotal, "linesTotal");
        linesDiscount = Objects.requireNonNull(linesDiscount, "linesDiscount");
        linesNet = Objects.requireNonNull(linesNet, "linesNet");
        invoiceDiscount = Objects.requireNonNull(invoiceDiscount, "invoiceDiscount");
        invoiceNet = Objects.requireNonNull(invoiceNet, "invoiceNet");
        paid = Objects.requireNonNull(paid, "paid");
        remaining = Objects.requireNonNull(remaining, "remaining");
        totalCost = Objects.requireNonNull(totalCost, "totalCost");
        profit = Objects.requireNonNull(profit, "profit");
    }

    public static InvoiceDetailsSummary from(BaseTotals header,
                                             List<? extends BasePurchasesAndSales> source,
                                             boolean includeProfit) {
        Objects.requireNonNull(header, "header");
        List<? extends BasePurchasesAndSales> lines = source == null ? List.of() : source;

        HashSet<Integer> itemIds = new HashSet<>();
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal linesTotal = MoneyMath.ZERO;
        BigDecimal linesDiscount = MoneyMath.ZERO;
        BigDecimal linesNet = MoneyMath.ZERO;
        BigDecimal cost = MoneyMath.ZERO;

        for (BasePurchasesAndSales line : lines) {
            int itemId = line.getItems() == null ? line.getNumItem() : line.getItems().getId();
            if (itemId > 0) {
                itemIds.add(itemId);
            }
            quantity = quantity.add(MoneyMath.decimal(line.getQuantity()));
            BigDecimal total = MoneyMath.money(line.getTotal());
            BigDecimal discount = MoneyMath.money(line.getDiscount());
            linesTotal = MoneyMath.add(linesTotal, total);
            linesDiscount = MoneyMath.add(linesDiscount, discount);
            linesNet = MoneyMath.add(linesNet, MoneyMath.subtract(total, discount));
            if (includeProfit) {
                cost = MoneyMath.add(cost, MoneyMath.money(line.getTotal_buy_price()));
            }
        }

        BigDecimal invoiceDiscount = MoneyMath.money(header.getDiscount());
        BigDecimal invoiceNet = MoneyMath.subtract(MoneyMath.money(header.getTotal()), invoiceDiscount);
        BigDecimal paid = MoneyMath.money(header.getPaid());
        BigDecimal remaining = MoneyMath.subtract(invoiceNet, paid);
        Optional<BigDecimal> totalCost = includeProfit ? Optional.of(cost) : Optional.empty();
        Optional<BigDecimal> profit = includeProfit
                ? Optional.of(MoneyMath.subtract(invoiceNet, cost))
                : Optional.empty();

        return new InvoiceDetailsSummary(lines.size(), itemIds.size(), quantity, linesTotal,
                linesDiscount, linesNet, invoiceDiscount, invoiceNet, paid, remaining,
                totalCost, profit);
    }
}
