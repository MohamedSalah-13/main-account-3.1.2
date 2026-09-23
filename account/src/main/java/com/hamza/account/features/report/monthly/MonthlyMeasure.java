package com.hamza.account.features.report.monthly;

import java.math.BigDecimal;
import java.util.function.Function;

/**
 * Which figure every cell of the report shows. The net is the default because it is what the rest of the
 * reports mean by a month's sales; the others are what it is made of, and {@link #GROSS} is what the old
 * screen showed and called the total - the invoices before their discounts, with nothing returned taken
 * off.
 */
public enum MonthlyMeasure {

    NET("report.monthly.measure.net", false, MonthFigures::net),
    GROSS("report.monthly.measure.gross", false, MonthFigures::gross),
    DISCOUNT("report.monthly.measure.discount", false, MonthFigures::discount),
    RETURNS("report.monthly.measure.returns", false, MonthFigures::returns),
    INVOICES("report.monthly.measure.invoices", true, figures -> BigDecimal.valueOf(figures.invoices()));

    private final String labelKey;
    private final boolean count;
    private final Function<MonthFigures, BigDecimal> reading;

    MonthlyMeasure(String labelKey, boolean count, Function<MonthFigures, BigDecimal> reading) {
        this.labelKey = labelKey;
        this.count = count;
        this.reading = reading;
    }

    public String labelKey() {
        return labelKey;
    }

    /** A number of documents rather than an amount - written without decimals. */
    public boolean isCount() {
        return count;
    }

    public BigDecimal of(MonthFigures figures) {
        return reading.apply(figures);
    }
}
