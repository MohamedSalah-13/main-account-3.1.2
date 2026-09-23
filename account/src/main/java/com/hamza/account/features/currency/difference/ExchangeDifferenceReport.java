package com.hamza.account.features.currency.difference;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** The exchange differences of every foreign account over a period, and their totals. */
public record ExchangeDifferenceReport(LocalDate from, LocalDate to, List<ExchangeDifferenceRow> rows,
                                       ExchangeDifferenceSummary summary) {

    public ExchangeDifferenceReport {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        rows = List.copyOf(rows);
        Objects.requireNonNull(summary, "summary");
    }
}
