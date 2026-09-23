package com.hamza.account.features.profitloss.statement;

import com.hamza.account.features.profitloss.ProfitLossFigures;
import com.hamza.account.features.profitloss.ProfitLossRow;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A row of the statement's table: a day, a week or a month, cut at the period's edges, with the days it
 * is made of. A row with nothing in it is still a row - a closed Friday is part of the answer.
 */
public record ProfitLossPeriodRow(LocalDate start, LocalDate end, ProfitLossFigures figures,
                                  List<ProfitLossRow> days) {

    public ProfitLossPeriodRow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(figures, "figures");
        days = List.copyOf(days);
    }

    public boolean isOneDay() {
        return start.equals(end);
    }

    public boolean hasActivity() {
        return figures.hasActivity();
    }
}
