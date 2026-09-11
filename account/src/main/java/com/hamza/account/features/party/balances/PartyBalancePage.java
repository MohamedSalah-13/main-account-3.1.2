package com.hamza.account.features.party.balances;

import java.util.List;

/**
 * One page of the balances list, with the figures for the whole filtered set.
 *
 * @param rows        this page's parties
 * @param summary     the filter's totals - not the page's: a footer that sums one page of a list is
 *                    a number nobody asked for
 * @param page        zero-based index
 * @param hasPrevious whether a page precedes it
 * @param hasNext     whether one follows, answered by fetching one row more than fits
 */
public record PartyBalancePage(List<PartyBalanceRow> rows, PartyBalanceSummary summary,
                               int page, boolean hasPrevious, boolean hasNext) {
    public PartyBalancePage {
        rows = List.copyOf(rows);
    }
}
