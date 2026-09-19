package com.hamza.account.features.delegate;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * The performance report for one month.
 *
 * <p>The totals are sums of the rows and of nothing else, so they cannot describe a different
 * set from the table beside them. {@link #unattributedCollections} is the one figure that is not
 * a row: cash taken on customers' accounts that names no delegate. It is shown rather than
 * dropped, because without it the collected column could not be reconciled with what the tills
 * took - and every install starts with all of its history there.
 *
 * @param ratesVisible whether the reader may see targets and commission; when false the rows
 *                     carry no rule, because none was fetched
 */
public record DelegatePerformanceMonth(YearMonth month, List<DelegatePerformanceRow> rows,
                                       BigDecimal unattributedCollections, boolean ratesVisible) {

    public DelegatePerformanceMonth {
        Objects.requireNonNull(month, "month");
        rows = List.copyOf(rows);
        unattributedCollections = unattributedCollections == null ? BigDecimal.ZERO : unattributedCollections;
    }

    public BigDecimal totalSales() {
        return sum(DelegateActivity::sales);
    }

    public BigDecimal totalReturns() {
        return sum(DelegateActivity::salesReturns);
    }

    public BigDecimal totalNetSales() {
        return sum(DelegateActivity::netSales);
    }

    public BigDecimal totalCollected() {
        return sum(DelegateActivity::collected);
    }

    /** The preview commissions added up; zero when rates are not visible. */
    public BigDecimal totalCommission() {
        return rows.stream().map(DelegatePerformanceRow::commission).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Delegates with a rule who have not reached its lowest tier. */
    public long belowLowestTier() {
        return rows.stream().filter(DelegatePerformanceRow::belowLowestTier).count();
    }

    private BigDecimal sum(java.util.function.Function<DelegateActivity, BigDecimal> figure) {
        return rows.stream().map(row -> figure.apply(row.activity())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
