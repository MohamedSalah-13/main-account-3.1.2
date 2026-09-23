package com.hamza.account.features.report.monthly;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * One year of the report: its months from {@link #firstMonth()} to {@link #lastMonth()}, each what that
 * month's documents came to. A past year has twelve; the year still running stops at this month, and the
 * first year of all starts at the month of its first document - so a month the report cannot speak for is
 * empty on screen rather than a zero set beside another year's figure.
 *
 * @param firstMonth the first month the year is read from - 1 for every year but the first
 * @param months     the months in order, January first - one per month up to the last
 */
public record YearRow(int year, int firstMonth, List<MonthFigures> months) {

    public YearRow {
        months = List.copyOf(Objects.requireNonNull(months, "months"));
        if (months.isEmpty() || months.size() > 12) {
            throw new IllegalArgumentException("A year holds 1-12 months, not " + months.size());
        }
        if (firstMonth < 1 || firstMonth > months.size()) {
            throw new IllegalArgumentException("The first month " + firstMonth + " is not within 1-" + months.size());
        }
    }

    /** A whole year, or the running one up to its last month. */
    public YearRow(int year, List<MonthFigures> months) {
        this(year, 1, months);
    }

    /** The last month the year is read to - 12 for a year that is over. */
    public int lastMonth() {
        return months.size();
    }

    /** A month's documents, or null for a month the year is not read for. */
    public MonthFigures month(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("A month is 1-12, not " + month);
        }
        return month >= firstMonth && month <= months.size() ? months.get(month - 1) : null;
    }

    /** A month's figure under the measure, or null for a month the year is not read for. */
    public BigDecimal value(MonthlyMeasure measure, int month) {
        MonthFigures figures = month(month);
        return figures == null ? null : measure.of(figures);
    }

    /** The months added up. */
    public MonthFigures total() {
        MonthFigures sum = MonthFigures.ZERO;
        for (int month = firstMonth; month <= months.size(); month++) {
            sum = sum.plus(months.get(month - 1));
        }
        return sum;
    }

    public BigDecimal total(MonthlyMeasure measure) {
        return measure.of(total());
    }
}
