package com.hamza.account.features.delegate.trend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * The figures above the chart, summed from its own points - so the cards cannot describe other days
 * than the lines under them, the rule the party trend's cards follow.
 * <p>
 * A percentage with nothing to divide by is <b>absent</b>, not zero: collected against no sales is
 * not "0%", and a change against a year with no sales is not "+100%" - it is not a comparison.
 */
public record DelegateTrendSummary(BigDecimal sales, BigDecimal salesReturns, BigDecimal collected,
                                   BigDecimal previousNetSales, BigDecimal previousCollected,
                                   boolean compared) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    static DelegateTrendSummary of(List<DelegateTrendPoint> points, boolean compared) {
        BigDecimal sales = DelegateTrend.NONE;
        BigDecimal returns = DelegateTrend.NONE;
        BigDecimal collected = DelegateTrend.NONE;
        BigDecimal previousNet = DelegateTrend.NONE;
        BigDecimal previousCollected = DelegateTrend.NONE;
        for (DelegateTrendPoint point : points) {
            sales = sales.add(point.sales());
            returns = returns.add(point.salesReturns());
            collected = collected.add(point.collected());
            previousNet = previousNet.add(point.previousNetSales());
            previousCollected = previousCollected.add(point.previousCollected());
        }
        return new DelegateTrendSummary(sales, returns, collected, previousNet, previousCollected, compared);
    }

    public BigDecimal netSales() {
        return sales.subtract(salesReturns);
    }

    /** What share of his net sales he brought in as cash, as a percentage. */
    public Optional<BigDecimal> collectionPercent() {
        return percentOf(collected, netSales());
    }

    /** How his net sales moved against the same dates a year earlier. */
    public Optional<BigDecimal> netSalesChange() {
        return compared ? percentOf(netSales().subtract(previousNetSales), previousNetSales) : Optional.empty();
    }

    /** How his collections moved against the same dates a year earlier. */
    public Optional<BigDecimal> collectedChange() {
        return compared ? percentOf(collected.subtract(previousCollected), previousCollected) : Optional.empty();
    }

    private static Optional<BigDecimal> percentOf(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(part.multiply(HUNDRED).divide(whole.abs(), 2, RoundingMode.HALF_UP));
    }
}
