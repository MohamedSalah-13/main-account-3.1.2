package com.hamza.account.features.party.rfm;

import java.util.List;

/**
 * One page of the table, with the figures for the whole filtered set.
 *
 * @param rows        this page's customers
 * @param summary     the filter's figures - not the page's
 * @param page        zero-based index
 * @param hasPrevious whether a page precedes it
 * @param hasNext     whether one follows, answered by fetching one row more than fits
 */
public record CustomerRfmPage(List<CustomerRfmRow> rows, CustomerRfmSummary summary,
                              int page, boolean hasPrevious, boolean hasNext) {

    public static final CustomerRfmPage EMPTY =
            new CustomerRfmPage(List.of(), CustomerRfmSummary.EMPTY, 0, false, false);

    public CustomerRfmPage {
        rows = List.copyOf(rows);
    }
}
