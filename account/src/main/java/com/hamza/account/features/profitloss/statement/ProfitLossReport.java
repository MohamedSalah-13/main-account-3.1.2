package com.hamza.account.features.profitloss.statement;

import com.hamza.account.features.profitloss.ProfitLossFigures;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The profit and loss statement over a period, set against another: the rows of the table, the page of
 * the statement, and the figures the cards show.
 *
 * @param current  the period's figures - the sum of its days, and so of its rows
 * @param previous the compared period's figures, the same sum over its own days
 */
public record ProfitLossReport(ProfitLossPeriod period, ComparisonBasis basis, ProfitLossPeriod previousPeriod,
                               ProfitLossGrouping grouping, List<ProfitLossPeriodRow> rows,
                               ProfitLossFigures current, ProfitLossFigures previous,
                               List<StatementLine> statement, OutsideProfitFigures outside) {

    public ProfitLossReport {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(previousPeriod, "previousPeriod");
        Objects.requireNonNull(grouping, "grouping");
        rows = List.copyOf(rows);
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(previous, "previous");
        statement = List.copyOf(statement);
        Objects.requireNonNull(outside, "outside");
    }

    /** Nothing sold, bought back, spent, counted short or found over in the period. */
    public boolean isEmpty() {
        return !current.hasActivity() && outside.isEmpty();
    }

    /** Whether the compared period had anything in it - a change against an empty period says nothing. */
    public boolean hasPrevious() {
        return previous.hasActivity();
    }

    public Optional<BigDecimal> netSalesChange() {
        return ProfitLossFigures.change(current.netSales(), previous.netSales());
    }

    public Optional<BigDecimal> grossProfitChange() {
        return ProfitLossFigures.change(current.grossProfit(), previous.grossProfit());
    }

    /** By size, as the statement compares an expense: more spent is a rise. */
    public Optional<BigDecimal> expensesChange() {
        return ProfitLossFigures.change(current.expenses(), previous.expenses());
    }

    public Optional<BigDecimal> netProfitChange() {
        return ProfitLossFigures.change(current.netProfit(), previous.netProfit());
    }
}
