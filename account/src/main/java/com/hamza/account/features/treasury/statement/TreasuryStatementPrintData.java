package com.hamza.account.features.treasury.statement;

import java.util.List;
import java.util.Objects;

/** The whole filtered statement for the paper and the spreadsheet, in the currency the screen shows. */
public record TreasuryStatementPrintData(List<TreasuryStatementRow> rows,
                                         TreasuryStatementTotals totals,
                                         boolean truncated,
                                         TreasuryStatementCurrency currency) {
    public TreasuryStatementPrintData {
        rows = List.copyOf(rows);
        Objects.requireNonNull(totals, "totals");
        currency = Objects.requireNonNullElse(currency, TreasuryStatementCurrency.BASE);
    }

    /** The four figures in the currency the statement is shown in. */
    public TreasuryStatementSummary summary() {
        return currency.summary(totals);
    }
}
