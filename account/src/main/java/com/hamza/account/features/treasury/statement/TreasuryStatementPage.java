package com.hamza.account.features.treasury.statement;

import java.util.List;
import java.util.Objects;

/**
 * One page of a statement, the totals of every row the filter matches, and the currency its figures
 * are shown in - so the table, the cards and the paper cannot write them in two.
 */
public record TreasuryStatementPage(List<TreasuryStatementRow> rows,
                                    TreasuryStatementTotals totals,
                                    int page, boolean hasPrevious, boolean hasNext,
                                    TreasuryStatementCurrency currency) {
    public TreasuryStatementPage {
        rows = List.copyOf(rows);
        Objects.requireNonNull(totals, "totals");
        currency = Objects.requireNonNullElse(currency, TreasuryStatementCurrency.BASE);
    }

    /** The four figures in the currency the statement is shown in. */
    public TreasuryStatementSummary summary() {
        return currency.summary(totals);
    }
}
