package com.hamza.account.features.profitloss.statement;

import java.math.BigDecimal;

/**
 * What a period's net sales and their cost are made of, read from the documents' headers and lines.
 *
 * <p>Every figure is positive; which way it moves the statement is {@link ProfitLossStatement}'s to say.
 * {@code grossSales - invoiceDiscounts - returns} is the net sales and {@code costOfSold - costOfReturned}
 * the cost of sales, over the same rows and the same arithmetic {@code document_profit} states - so they
 * are the statement's own figures, broken down. Where they are not, the statement shows the difference
 * as a line of its own rather than letting a section fail to add up.</p>
 *
 * @param grossSales       the sales invoices' totals, before the invoices' own discounts
 * @param invoiceDiscounts the sales invoices' discounts
 * @param returns          the sales returns, net of their own discounts
 * @param costOfSold       what the goods on the sales invoices cost
 * @param costOfReturned   what the goods that came back cost
 */
public record SalesBreakdown(BigDecimal grossSales, BigDecimal invoiceDiscounts, BigDecimal returns,
                             BigDecimal costOfSold, BigDecimal costOfReturned) {

    public static final SalesBreakdown ZERO = new SalesBreakdown(BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    public SalesBreakdown {
        grossSales = orZero(grossSales);
        invoiceDiscounts = orZero(invoiceDiscounts);
        returns = orZero(returns);
        costOfSold = orZero(costOfSold);
        costOfReturned = orZero(costOfReturned);
    }

    public BigDecimal netSales() {
        return grossSales.subtract(invoiceDiscounts).subtract(returns);
    }

    public BigDecimal costOfSales() {
        return costOfSold.subtract(costOfReturned);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
