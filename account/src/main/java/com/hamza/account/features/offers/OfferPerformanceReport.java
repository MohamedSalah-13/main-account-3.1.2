package com.hamza.account.features.offers;

import com.hamza.account.features.profitloss.statement.ProfitLossPeriod;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * What the offers gave over a period and what they sold, each against the period before
 * (docs/pricing-and-offers-plan.md phase E) - the offer that gave the most first.
 *
 * <p><b>It explains a figure and defines none</b>: an offer's net is its lines' after their own discounts, of
 * which the offer's is a part, and its cost the lines' recorded cost - so for invoices whose every line an
 * offer reached and with no discount of their own, the report's net and cost are {@code document_profit}'s.
 * A discount on a whole invoice belongs to no offer and is not here.</p>
 *
 * @param invoices    how many invoices any offer reached in the period, each once - never the rows' own
 *                    counts added up, which would count an invoice holding two offers twice
 * @param costVisible whether the cost was read - the cost and profit columns and card exist only then
 */
public record OfferPerformanceReport(ProfitLossPeriod period, ProfitLossPeriod previous,
                                     List<OfferPerformanceRow> rows, int invoices, boolean costVisible) {

    /** The most given first, then the larger net, then the oldest offer - an order that holds between runs. */
    static final Comparator<OfferPerformanceRow> MOST_GIVEN_FIRST = Comparator
            .comparing((OfferPerformanceRow row) -> row.now().discount()).reversed()
            .thenComparing(Comparator.comparing((OfferPerformanceRow row) -> row.now().net()).reversed())
            .thenComparingInt(row -> row.offer().id());

    public OfferPerformanceReport {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(previous, "previous");
        rows = rows == null ? List.of() : rows.stream().sorted(MOST_GIVEN_FIRST).toList();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public BigDecimal discount() {
        return sum(row -> row.now().discount());
    }

    public BigDecimal discountBefore() {
        return sum(row -> row.before().discount());
    }

    public BigDecimal net() {
        return sum(row -> row.now().net());
    }

    public BigDecimal netBefore() {
        return sum(row -> row.before().net());
    }

    /** The offers' lines' profit, or empty when the cost was not read. */
    public Optional<BigDecimal> profit() {
        return costVisible ? Optional.of(sum(row -> row.now().profit().orElse(BigDecimal.ZERO))) : Optional.empty();
    }

    public Optional<BigDecimal> profitBefore() {
        return costVisible ? Optional.of(sum(row -> row.before().profit().orElse(BigDecimal.ZERO)))
                : Optional.empty();
    }

    public Optional<BigDecimal> netChange() {
        return OfferPerformanceRow.change(net(), netBefore());
    }

    public Optional<BigDecimal> discountChange() {
        return OfferPerformanceRow.change(discount(), discountBefore());
    }

    private BigDecimal sum(Function<OfferPerformanceRow, BigDecimal> figure) {
        return rows.stream().map(figure).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
