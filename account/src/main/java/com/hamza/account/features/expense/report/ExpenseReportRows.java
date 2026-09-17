package com.hamza.account.features.expense.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The rows the report statements answer, before any report shapes them. Plain values; the reports decide
 * what a row means.
 */
public final class ExpenseReportRows {

    private ExpenseReportRows() {
    }

    /** What one heading - under itself, not with the headings below it - came to over a filter. */
    public record HeadingTotal(int headingId, int count, BigDecimal total) {
        public HeadingTotal {
            total = ExpensePeriods.orNone(total);
        }
    }

    /** What one heading came to on one day. */
    public record HeadingDay(int headingId, LocalDate day, BigDecimal total) {
        public HeadingDay {
            Objects.requireNonNull(day, "day");
            total = ExpensePeriods.orNone(total);
        }
    }

    /** One day's amount: what was spent, or - for the ratio - what was sold net of returns. */
    public record Day(LocalDate day, BigDecimal total) {
        public Day {
            Objects.requireNonNull(day, "day");
            total = ExpensePeriods.orNone(total);
        }
    }

    /**
     * One value of a dimension as the query answered it.
     *
     * @param key   the value's key as text - a till's id, a user's id, a shift's id, a payee, {@code yyyymm} -
     *              or {@code null} for the expenses that carry none
     * @param label the name read beside it, or {@code null} where the key is its own label
     */
    public record DimensionTotal(String key, String label, int count, BigDecimal total) {
        public DimensionTotal {
            total = ExpensePeriods.orNone(total);
        }
    }
}
