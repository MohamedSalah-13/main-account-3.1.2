package com.hamza.account.features.profitloss.yearly;

import com.hamza.account.features.profitloss.DailyProfitSource;
import com.hamza.account.features.profitloss.ProfitLossFigures;
import com.hamza.account.features.profitloss.ProfitLossRow;
import com.hamza.controlsfx.database.DaoException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * A year's profit and loss by the month, beside the same dates a year earlier.
 *
 * <p><b>The profit is the statement's.</b> Both years are read in one call to the profit and loss
 * statement's own days ({@link DailyProfitSource}) and folded into months here - so the report is the
 * statement grouped by the month, and nothing in this package decides what a sale earned. The permission
 * is asked there, before anything is read; the breakdown is read only after it has been granted.</p>
 *
 * <p>Every month from January to the last one {@link YearlyReportPeriod} shows is on the report, a quiet one
 * as zeros - the view it replaced grouped the rows that existed, so a month with no trade simply vanished
 * from the table.</p>
 */
public final class YearlyReportService {

    private final DailyProfitSource profits;
    private final YearlyBreakdownRepository breakdowns;
    private final Clock clock;

    public YearlyReportService(DailyProfitSource profits, YearlyBreakdownRepository breakdowns, Clock clock) {
        this.profits = profits;
        this.breakdowns = breakdowns;
        this.clock = clock;
    }

    public YearlyReport report(int year) throws DaoException {
        YearlyReportPeriod period = YearlyReportPeriod.of(year, LocalDate.now(clock));
        List<ProfitLossRow> days = profits.load(period.previousFrom(), period.to());

        Map<YearMonth, ProfitLossFigures> current = new HashMap<>();
        Map<YearMonth, ProfitLossFigures> previous = new HashMap<>();
        Map<YearMonth, List<ProfitLossRow>> daysByMonth = new HashMap<>();
        for (ProfitLossRow day : days) {
            LocalDate date = day.date();
            YearMonth month = YearMonth.from(date);
            if (!date.isBefore(period.from()) && !date.isAfter(period.to())) {
                current.merge(month, ProfitLossFigures.ZERO.plus(day), ProfitLossFigures::plus);
                daysByMonth.computeIfAbsent(month, ignored -> new ArrayList<>()).add(day);
            } else if (!date.isBefore(period.previousFrom()) && !date.isAfter(period.previousTo())) {
                // Filed under this year's month, so each row carries its own month a year back.
                previous.merge(month.plusYears(1), ProfitLossFigures.ZERO.plus(day), ProfitLossFigures::plus);
            }
        }

        Map<Integer, MonthBreakdown> breakdownByMonth = new HashMap<>();
        for (MonthBreakdown breakdown : breakdowns.breakdown(year)) {
            breakdownByMonth.put(breakdown.month(), breakdown);
        }

        List<YearlyReportRow> rows = new ArrayList<>();
        for (YearMonth month : period.months()) {
            List<ProfitLossRow> monthDays = new ArrayList<>(daysByMonth.getOrDefault(month, List.of()));
            monthDays.sort(Comparator.comparing(ProfitLossRow::date));
            rows.add(new YearlyReportRow(month,
                    current.getOrDefault(month, ProfitLossFigures.ZERO),
                    previous.getOrDefault(month, ProfitLossFigures.ZERO),
                    breakdownByMonth.getOrDefault(month.getMonthValue(), MonthBreakdown.empty(month.getMonthValue())),
                    monthDays));
        }
        return YearlyReport.of(period, rows);
    }

    /**
     * The years a document was written in, newest first, and the current year whether or not anything has
     * been written in it yet - a report for this year is the one most often asked for, and an empty one
     * says so rather than being impossible to ask.
     */
    public List<Integer> years() throws DaoException {
        TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.addAll(breakdowns.years());
        years.add(LocalDate.now(clock).getYear());
        return List.copyOf(years);
    }

    /** The year the screen opens on: this one. */
    public int defaultYear() {
        return LocalDate.now(clock).getYear();
    }
}
