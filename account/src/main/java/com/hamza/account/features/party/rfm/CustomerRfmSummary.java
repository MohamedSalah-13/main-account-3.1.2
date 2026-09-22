package com.hamza.account.features.party.rfm;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The figures above the table, for every row the filter leaves rather than for the page.
 *
 * @param parties   customers listed
 * @param buying    of those, how many bought in the period
 * @param documents their invoices in the period
 * @param net       what those came to, less the period's returns
 */
public record CustomerRfmSummary(int parties, int buying, int documents, BigDecimal net) {

    public static final CustomerRfmSummary EMPTY = new CustomerRfmSummary(0, 0, 0, BigDecimal.ZERO);

    public CustomerRfmSummary {
        net = Objects.requireNonNull(net, "net");
        if (buying > parties) {
            throw new IllegalArgumentException(buying + " buying out of " + parties);
        }
    }

    /** How many listed customers bought nothing in the period - the ones the table is opened to find. */
    public int idle() {
        return parties - buying;
    }
}
