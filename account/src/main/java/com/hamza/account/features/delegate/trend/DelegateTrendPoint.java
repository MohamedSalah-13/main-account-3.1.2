package com.hamza.account.features.delegate.trend;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One period on the chart and one row of the table under it.
 *
 * @param start             the period's first day - what it is filed under
 * @param end               its last day charted: the period's own, or the filter's last day when the
 *                          range stops inside it
 * @param label             what the axis writes under it
 * @param sales             his invoices' net in the period
 * @param salesReturns      his customers' returns' net in it
 * @param collected         the cash he brought in, refunds taken off
 * @param previousNetSales  net sales over the same dates a year earlier; zero without a comparison
 * @param previousCollected the same for collected
 */
public record DelegateTrendPoint(LocalDate start, LocalDate end, String label,
                                 BigDecimal sales, BigDecimal salesReturns, BigDecimal collected,
                                 BigDecimal previousNetSales, BigDecimal previousCollected) {

    /** Sales less returns - the performance report's "net", and the first of the two lines. */
    public BigDecimal netSales() {
        return sales.subtract(salesReturns);
    }
}
