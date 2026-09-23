package com.hamza.account.features.profitloss.yearly;

import java.math.BigDecimal;

/**
 * What a month's net sales are made of, and what it bought: {@code view_yearly_monthly_report}'s columns,
 * folded into the three that explain the net and the one that does not enter the profit.
 *
 * <p>{@code grossSales - salesDiscount - salesReturns} is the net sales, and it is the same figure the profit
 * and loss statement reads from {@code document_profit}: both are {@code total - discount} over the same two
 * tables on the same dates. {@link YearlyReportRow#unexplainedSales()} is whatever does not add up, and the
 * report says it rather than hides it.</p>
 *
 * <p>The purchases are shown and never subtracted. A month's profit is what its sales cost, not what it
 * happened to buy - the definition this view used to get wrong.</p>
 *
 * @param month          1-12
 * @param grossSales     the invoices' totals before their own discounts
 * @param salesDiscount  the invoices' discounts
 * @param salesReturns   the sales returns, net of their own discounts - a positive amount
 * @param netPurchases   the purchases less their discounts, less the purchase returns net of theirs
 */
public record MonthBreakdown(int month, BigDecimal grossSales, BigDecimal salesDiscount,
                             BigDecimal salesReturns, BigDecimal netPurchases) {

    public MonthBreakdown {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("A month is 1-12, not " + month);
        }
        grossSales = orZero(grossSales);
        salesDiscount = orZero(salesDiscount);
        salesReturns = orZero(salesReturns);
        netPurchases = orZero(netPurchases);
    }

    public static MonthBreakdown empty(int month) {
        return new MonthBreakdown(month, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Built from the view's eight document columns. */
    public static MonthBreakdown fromView(int month, BigDecimal sales, BigDecimal salesDiscount,
                                          BigDecimal salesReturn, BigDecimal salesReturnDiscount,
                                          BigDecimal purchases, BigDecimal purchasesDiscount,
                                          BigDecimal purchasesReturn, BigDecimal purchasesReturnDiscount) {
        BigDecimal netReturns = orZero(salesReturn).subtract(orZero(salesReturnDiscount));
        BigDecimal netPurchases = orZero(purchases).subtract(orZero(purchasesDiscount))
                .subtract(orZero(purchasesReturn).subtract(orZero(purchasesReturnDiscount)));
        return new MonthBreakdown(month, sales, salesDiscount, netReturns, netPurchases);
    }

    /** What the three columns say the net sales are. */
    public BigDecimal netSales() {
        return grossSales.subtract(salesDiscount).subtract(salesReturns);
    }

    public MonthBreakdown plus(MonthBreakdown other) {
        return new MonthBreakdown(month, grossSales.add(other.grossSales), salesDiscount.add(other.salesDiscount),
                salesReturns.add(other.salesReturns), netPurchases.add(other.netPurchases));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
