package com.hamza.account.features.report.monthly;

import java.time.LocalDate;
import java.util.Objects;

/** One day's documents of one side - a row of {@link MonthlyTotalsQuery#daysSql}. */
public record DayFigures(LocalDate day, MonthFigures figures) {

    public DayFigures {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(figures, "figures");
    }
}
