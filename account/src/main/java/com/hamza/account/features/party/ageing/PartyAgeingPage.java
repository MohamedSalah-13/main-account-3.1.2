package com.hamza.account.features.party.ageing;

import java.util.List;

/**
 * One page of the ageing report, with the figures for the whole filtered set.
 *
 * @param rows        this page's parties
 * @param summary     the filter's totals - not the page's
 * @param page        zero-based index
 * @param hasPrevious whether a page precedes it
 * @param hasNext     whether one follows, answered by fetching one row more than fits
 */
public record PartyAgeingPage(List<PartyAgeingRow> rows, PartyAgeingSummary summary,
                              int page, boolean hasPrevious, boolean hasNext) {

    public static final PartyAgeingPage EMPTY =
            new PartyAgeingPage(List.of(), PartyAgeingSummary.EMPTY, 0, false, false);

    public PartyAgeingPage {
        rows = List.copyOf(rows);
    }
}
