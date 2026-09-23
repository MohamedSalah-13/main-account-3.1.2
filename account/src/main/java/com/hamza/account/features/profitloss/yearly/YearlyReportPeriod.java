package com.hamza.account.features.profitloss.yearly;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Which months a year's report shows, and which dates it compares them with.
 *
 * <p><b>A year still running stops at this month, and the year it is compared with at today's date a year
 * back.</b> Set nine months
 * of this year against twelve of the last and every comparison reads as a collapse; set September to date
 * against all of last September and the one month still being traded reads the same way. So the year before
 * is cut at the same calendar day - the rule the collections trend follows for the same reason. A past year,
 * or a year not yet reached, is its whole twelve months against the whole of the year before it.</p>
 *
 * <p>The months after today are left out rather than shown as zeros: an empty October is not a bad
 * October, and a chart line dropping to nothing at the end of the year says it is. The current month is
 * read to its last day, not to today: {@code view_yearly_monthly_report} knows only whole months, and a
 * document dated later this month would otherwise be in its breakdown and missing from its profit.</p>
 */
public record YearlyReportPeriod(int year, int lastMonth, LocalDate from, LocalDate to,
                                 LocalDate previousFrom, LocalDate previousTo) {

    public YearlyReportPeriod {
        if (lastMonth < 1 || lastMonth > 12) {
            throw new IllegalArgumentException("The last month is 1-12, not " + lastMonth);
        }
    }

    public static YearlyReportPeriod of(int year, LocalDate today) {
        LocalDate first = LocalDate.of(year, Month.JANUARY, 1);
        LocalDate previousFirst = first.minusYears(1);
        if (year == today.getYear()) {
            return new YearlyReportPeriod(year, today.getMonthValue(), first,
                    YearMonth.from(today).atEndOfMonth(), previousFirst, today.minusYears(1));
        }
        return new YearlyReportPeriod(year, 12, first, LocalDate.of(year, Month.DECEMBER, 31),
                previousFirst, LocalDate.of(year - 1, Month.DECEMBER, 31));
    }

    /** Whether the year before is cut short at today's date - the comparison is then "to the same day". */
    public boolean toDate() {
        return !previousTo.equals(LocalDate.of(year - 1, Month.DECEMBER, 31));
    }

    /** January to the last month shown, in order. */
    public List<YearMonth> months() {
        List<YearMonth> months = new ArrayList<>(lastMonth);
        for (int month = 1; month <= lastMonth; month++) {
            months.add(YearMonth.of(year, month));
        }
        return months;
    }
}
