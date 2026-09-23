package com.hamza.account.features.party.statement;

import java.util.Objects;

/**
 * A statement's four figures twice: in the base, which is what the books hold, and in the party's own
 * currency (V82, docs/currency-plan.md §14). For a party in the base the second is the first.
 */
public record PartyStatementTotals(PartyStatementSummary base, PartyStatementSummary own) {

    public static final PartyStatementTotals EMPTY = inBase(PartyStatementSummary.EMPTY);

    public PartyStatementTotals {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(own, "own");
    }

    /** Totals of a party in the base, whose figures in its own currency are these. */
    public static PartyStatementTotals inBase(PartyStatementSummary summary) {
        return new PartyStatementTotals(summary, summary);
    }
}
