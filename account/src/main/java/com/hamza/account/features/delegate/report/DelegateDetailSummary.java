package com.hamza.account.features.delegate.report;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a breakdown's rows come to, and how that reaches the delegate's net sales - the figure
 * the performance report and the commission are computed on.
 *
 * <p>Rows read off the documents already are the net. Rows read off the lines are the value
 * <b>before</b> what was taken off whole invoices, so the header discounts stand between them and
 * the net, as a figure of their own rather than shared out among items by a rule nobody decided.
 * Either way {@link #net()} is one number, and it is the activity query's
 * {@code sales - sales_returns}; {@code DelegateDetailDatabaseAcceptanceTest} holds all four
 * breakdowns to it.
 */
public record DelegateDetailSummary(BigDecimal sales, BigDecimal returns,
                                    BigDecimal headerDiscountOnSales, BigDecimal headerDiscountOnReturns) {

    /** Header discounts are zero for a breakdown read off the documents: its rows carry them. */
    public static DelegateDetailSummary of(DelegateBreakdown breakdown, List<DelegateDetailRow> rows,
                                           BigDecimal headerDiscountOnSales, BigDecimal headerDiscountOnReturns) {
        BigDecimal sales = rows.stream().map(DelegateDetailRow::sales).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returns = rows.stream().map(DelegateDetailRow::returns).reduce(BigDecimal.ZERO, BigDecimal::add);
        return breakdown.readOffLines()
                ? new DelegateDetailSummary(sales, returns, headerDiscountOnSales, headerDiscountOnReturns)
                : new DelegateDetailSummary(sales, returns, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** What was taken off whole documents, net of what returns gave back of it. */
    public BigDecimal headerDiscount() {
        return headerDiscountOnSales.subtract(headerDiscountOnReturns);
    }

    public BigDecimal net() {
        return sales.subtract(returns).subtract(headerDiscount());
    }
}
