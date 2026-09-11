package com.hamza.account.features.party.statement;

import java.util.List;

/**
 * One page of a statement, with the figures that belong to the whole period.
 *
 * @param rows        the rows of this page
 * @param summary     the period's figures — not this page's: a balance is not per page
 * @param page        zero-based index of this page
 * @param hasPrevious whether a page precedes it
 * @param hasNext     whether one follows, answered by fetching one row more than fits
 */
public record PartyStatementPage(List<PartyStatementRow> rows,
                                 PartyStatementSummary summary,
                                 int page, boolean hasPrevious, boolean hasNext) {
    public PartyStatementPage {
        rows = List.copyOf(rows);
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
