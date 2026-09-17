package com.hamza.account.features.expense.report;

import com.hamza.account.features.expense.ExpenseFilter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

/** The small arithmetic every expense report shares: a period inside the scope, and a percentage that may be absent. */
public final class ExpensePeriods {

    /** Zero with the two places money carries, so an empty cell prints as 0.00. */
    static final BigDecimal NONE = new BigDecimal("0.00");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ExpensePeriods() {
    }

    /**
     * The scope narrowed to a period, never widened past it. A month at the edge of a scope that starts on
     * the 10th opens on the 10th: the list has to add up to the figure it was opened from, and that figure
     * counted only the days inside the scope.
     */
    public static ExpenseFilter within(ExpenseFilter scope, LocalDate from, LocalDate to) {
        LocalDate start = scope.from() != null && scope.from().isAfter(from) ? scope.from() : from;
        LocalDate end = scope.to() != null && scope.to().isBefore(to) ? scope.to() : to;
        return scope.withPeriod(start, end);
    }

    /**
     * {@code part} as a percentage of {@code whole}, to one place - or empty when there is nothing to divide
     * by. A share of nothing, or a change against a period with nothing in it, is not a number, and printing
     * "0%" or "100%" there would be inventing one.
     */
    public static Optional<BigDecimal> percent(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(part.multiply(HUNDRED).divide(whole.abs(), 1, RoundingMode.HALF_UP));
    }

    /** How {@code current} moved against {@code previous}; empty with no previous figure, or a zero one. */
    public static Optional<BigDecimal> change(BigDecimal current, BigDecimal previous) {
        if (previous == null || current == null) {
            return Optional.empty();
        }
        return percent(current.subtract(previous), previous);
    }

    static BigDecimal orNone(BigDecimal value) {
        return value == null ? NONE : value;
    }
}
