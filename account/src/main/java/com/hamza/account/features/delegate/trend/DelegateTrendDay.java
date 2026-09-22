package com.hamza.account.features.delegate.trend;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One day of one delegate - see {@link DelegateTrendQuery} for what each figure is.
 *
 * @param day          the document or collection date
 * @param sales        his invoices' net that day
 * @param salesReturns his customers' returns' net that day
 * @param collected    the cash he brought in that day, refunds taken off
 */
public record DelegateTrendDay(LocalDate day, BigDecimal sales, BigDecimal salesReturns, BigDecimal collected) {

    public DelegateTrendDay {
        Objects.requireNonNull(day, "day");
        sales = sales == null ? DelegateTrend.NONE : sales;
        salesReturns = salesReturns == null ? DelegateTrend.NONE : salesReturns;
        collected = collected == null ? DelegateTrend.NONE : collected;
    }
}
