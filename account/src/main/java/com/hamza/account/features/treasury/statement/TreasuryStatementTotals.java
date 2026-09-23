package com.hamza.account.features.treasury.statement;

import java.util.Objects;

/**
 * A statement's four figures twice: in the base, which is what the books hold, and in the currency of
 * the treasury the statement is of (V81). The second is only a figure for one treasury - summed over
 * several it adds dollars to pounds - and for a treasury in the base it is the first.
 */
public record TreasuryStatementTotals(TreasuryStatementSummary base, TreasuryStatementSummary own) {

    public static final TreasuryStatementTotals EMPTY = inBase(new TreasuryStatementSummary(null, null, null, null));

    public TreasuryStatementTotals {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(own, "own");
    }

    /** Totals of treasuries in the base alone, whose figures in their own currency are these. */
    public static TreasuryStatementTotals inBase(TreasuryStatementSummary summary) {
        return new TreasuryStatementTotals(summary, summary);
    }
}
