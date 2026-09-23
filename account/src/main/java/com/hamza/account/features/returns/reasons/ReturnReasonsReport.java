package com.hamza.account.features.returns.reasons;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A period's returns of one side, by reason and by item, beside the documents they are set against.
 *
 * <p>A share with nothing to divide by is <b>absent</b>, not zero: a reason's share of no returns, or a
 * return rate over a period that sold nothing, is not a number.</p>
 *
 * @param documentsNet what the side's documents came to in the period, net of their discounts - the
 *                     figure the return rate is measured against
 */
public record ReturnReasonsReport(ReturnSide side, LocalDate from, LocalDate to, List<ReasonTotal> reasons,
                                  List<ReturnedItem> items, BigDecimal documentsNet) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public ReturnReasonsReport {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        reasons = reasons.stream().sorted(Comparator.comparing(ReasonTotal::value).reversed()).toList();
        items = List.copyOf(items);
        documentsNet = documentsNet == null ? BigDecimal.ZERO : documentsNet;
    }

    public int count() {
        return reasons.stream().mapToInt(ReasonTotal::count).sum();
    }

    public BigDecimal value() {
        return reasons.stream().map(ReasonTotal::value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isEmpty() {
        return count() == 0;
    }

    /** The returns that name no reason, together. */
    public ReasonTotal withoutReason() {
        int count = 0;
        BigDecimal value = BigDecimal.ZERO;
        for (ReasonTotal reason : reasons) {
            if (reason.isWithoutReason()) {
                count += reason.count();
                value = value.add(reason.value());
            }
        }
        return new ReasonTotal(null, count, value);
    }

    /** The reason given for the most value; "none given" is not a reason and never the answer. */
    public Optional<ReasonTotal> leadingReason() {
        return reasons.stream().filter(reason -> !reason.isWithoutReason() && reason.value().signum() > 0)
                .findFirst();
    }

    /** A reason's share of the returns' value. */
    public Optional<BigDecimal> share(ReasonTotal reason) {
        return percent(reason.value(), value());
    }

    /** The returns' value as a share of what the side's documents came to. */
    public Optional<BigDecimal> returnRate() {
        return percent(value(), documentsNet);
    }

    private static Optional<BigDecimal> percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP));
    }
}
