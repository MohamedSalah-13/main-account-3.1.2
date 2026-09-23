package com.hamza.account.features.profitloss.yearly;

import java.util.List;

/** A year's profit and loss by the month, with the dates it covers and the figures above the table. */
public record YearlyReport(YearlyReportPeriod period, List<YearlyReportRow> rows, YearlySummary summary) {

    public YearlyReport {
        rows = List.copyOf(rows);
    }

    public static YearlyReport of(YearlyReportPeriod period, List<YearlyReportRow> rows) {
        return new YearlyReport(period, rows, YearlySummary.of(rows));
    }

    public int year() {
        return period.year();
    }

    /** Whether nothing at all was sold, returned or spent in the months shown. */
    public boolean isEmpty() {
        return summary.activeMonths() == 0;
    }
}
