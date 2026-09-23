package com.hamza.account.features.report.monthly;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One side's documents by the month, a row per year, newest first - and what this year so far says against
 * the same days last year.
 *
 * <p>The years run from the first holding a document to this one, <b>every one of them a row</b>: a year
 * with nothing in it is a row of zeros, not a gap the reader has to notice. The year still running stops
 * at this month (or at a later month a document is already dated in); every other year has twelve - but
 * the first year of all starts at the month of its first document, since the months before it are not
 * months of no sales but months before anything was recorded here.</p>
 *
 * <p>The comparison is by the day: this year from the 1st of January to today against last year from its
 * 1st of January to the same date - so a month half gone is never set against the whole of last year's.
 * The 29th of February compares with the 28th.</p>
 */
public final class MonthlyTotalsReport {

    /** How many years the chart can draw - more lines than this is a tangle, not a comparison. */
    public static final int CHART_YEARS = 5;

    private final MonthlySide side;
    private final LocalDate today;
    private final List<YearRow> years;
    private final List<DayFigures> days;

    private MonthlyTotalsReport(MonthlySide side, LocalDate today, List<YearRow> years, List<DayFigures> days) {
        this.side = side;
        this.today = today;
        this.years = List.copyOf(years);
        this.days = List.copyOf(days);
    }

    /**
     * @param days  the side's days, in any order - one entry per day is expected, and two for one day
     *              are added together
     * @param today the day the running year stops at
     */
    public static MonthlyTotalsReport of(MonthlySide side, List<DayFigures> days, LocalDate today) {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(today, "today");
        TreeMap<Integer, MonthFigures[]> byYear = new TreeMap<>();
        for (DayFigures day : days) {
            MonthFigures[] months = byYear.computeIfAbsent(day.day().getYear(), year -> emptyYear());
            int index = day.day().getMonthValue() - 1;
            months[index] = months[index].plus(day.figures());
        }
        if (byYear.isEmpty()) {
            return new MonthlyTotalsReport(side, today, List.of(), List.of());
        }
        int first = byYear.firstKey();
        int last = Math.max(byYear.lastKey(), today.getYear());
        List<YearRow> rows = new ArrayList<>();
        for (int year = last; year >= first; year--) {
            MonthFigures[] months = byYear.getOrDefault(year, emptyYear());
            int lastMonth = lastMonth(year, months, today);
            int firstMonth = year == first ? Math.min(firstWritten(months), lastMonth) : 1;
            rows.add(new YearRow(year, firstMonth, List.of(months).subList(0, lastMonth)));
        }
        return new MonthlyTotalsReport(side, today, rows, days);
    }

    /** Twelve for a year that is over; for the running year this month, or a later one already written in. */
    private static int lastMonth(int year, MonthFigures[] months, LocalDate today) {
        if (year < today.getYear()) {
            return 12;
        }
        int written = 0;
        for (int month = 12; month >= 1; month--) {
            if (!months[month - 1].isEmpty()) {
                written = month;
                break;
            }
        }
        return year == today.getYear() ? Math.max(today.getMonthValue(), written) : Math.max(written, 1);
    }

    /** The first month of a year holding a document - which the first year of all is read from. */
    private static int firstWritten(MonthFigures[] months) {
        for (int month = 1; month <= 12; month++) {
            if (!months[month - 1].isEmpty()) {
                return month;
            }
        }
        return 1;
    }

    private static MonthFigures[] emptyYear() {
        MonthFigures[] months = new MonthFigures[12];
        java.util.Arrays.fill(months, MonthFigures.ZERO);
        return months;
    }

    public MonthlySide side() {
        return side;
    }

    public LocalDate today() {
        return today;
    }

    /** Newest first. */
    public List<YearRow> years() {
        return years;
    }

    public boolean isEmpty() {
        return years.isEmpty();
    }

    /** The newest years, as many as the chart draws. */
    public List<YearRow> chartYears() {
        return years.subList(0, Math.min(CHART_YEARS, years.size()));
    }

    /** What the documents dated from {@code from} to {@code to}, both included, came to. */
    public MonthFigures between(LocalDate from, LocalDate to) {
        MonthFigures sum = MonthFigures.ZERO;
        for (DayFigures day : days) {
            if (!day.day().isBefore(from) && !day.day().isAfter(to)) {
                sum = sum.plus(day.figures());
            }
        }
        return sum;
    }

    /** This year from the 1st of January to today. */
    public MonthFigures yearToDate() {
        return between(today.withDayOfYear(1), today);
    }

    /** The day last year that {@link #yearToDate()} is compared to: today a year back. */
    public LocalDate sameDayLastYear() {
        return today.minusYears(1);
    }

    /** Last year from its 1st of January to {@link #sameDayLastYear()}. */
    public MonthFigures sameDaysLastYear() {
        LocalDate end = sameDayLastYear();
        return between(end.withDayOfYear(1), end);
    }

    /** The whole of last year. */
    public MonthFigures previousYear() {
        LocalDate first = LocalDate.of(today.getYear() - 1, 1, 1);
        return between(first, first.withMonth(12).withDayOfMonth(31));
    }

    /**
     * This year's month with the largest figure under the measure, or empty when no month of it has a
     * figure above zero - "the highest month" of a year with nothing in it is not a month.
     */
    public Optional<Integer> highestMonth(MonthlyMeasure measure) {
        return years.stream().filter(row -> row.year() == today.getYear()).findFirst().flatMap(row -> {
            int best = 0;
            BigDecimal most = BigDecimal.ZERO;
            for (int month = row.firstMonth(); month <= row.lastMonth(); month++) {
                BigDecimal value = row.value(measure, month);
                if (value.compareTo(most) > 0) {
                    most = value;
                    best = month;
                }
            }
            return best == 0 ? Optional.empty() : Optional.of(best);
        });
    }

    /** This year's row, if the report has one. */
    public Optional<YearRow> currentYear() {
        return years.stream().filter(row -> row.year() == today.getYear()).findFirst();
    }

    /**
     * How far {@code current} is from {@code previous}, in percent of the previous one's size, to two
     * places - or empty when the previous is zero, since a change from nothing is not a percentage.
     */
    public static Optional<BigDecimal> change(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(current.subtract(previous).multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 2, RoundingMode.HALF_UP));
    }
}
