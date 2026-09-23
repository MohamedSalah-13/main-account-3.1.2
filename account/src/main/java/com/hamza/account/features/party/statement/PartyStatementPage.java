package com.hamza.account.features.party.statement;

import java.util.List;

/**
 * One page of a statement, with the figures that belong to the whole period.
 *
 * @param rows        the rows of this page
 * @param totals      the period's figures — not this page's: a balance is not per page - in the base and
 *                    in the party's own currency (V82)
 * @param page        zero-based index of this page
 * @param hasPrevious whether a page precedes it
 * @param hasNext     whether one follows, answered by fetching one row more than fits
 * @param currency    which of the two sets of figures the statement shows, read with the rows
 */
public record PartyStatementPage(List<PartyStatementRow> rows,
                                 PartyStatementTotals totals,
                                 int page, boolean hasPrevious, boolean hasNext,
                                 PartyStatementCurrency currency) {
    public PartyStatementPage {
        rows = List.copyOf(rows);
        totals = totals == null ? PartyStatementTotals.EMPTY : totals;
        currency = currency == null ? PartyStatementCurrency.BASE : currency;
    }

    /** The figures at the foot of the statement, in the currency it is shown in. */
    public PartyStatementSummary summary() {
        return currency.summary(totals);
    }

    /** The rows as shown - in the party's own currency when it deals in a foreign one. */
    public List<PartyStatementRow> shownRows() {
        return currency.shown(rows);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
