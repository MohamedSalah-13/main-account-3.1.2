package com.hamza.account.features.party.trend;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One period on the chart and one row of the table under it.
 *
 * @param start          the period's first day - what it is filed under
 * @param end            its last day charted: the period's own last day, or the filter's last
 *                       day when the range stops inside it
 * @param label          what the axis writes under it
 * @param debit          what was charged in the period
 * @param credit         what was paid or credited in it
 * @param previousDebit  the same, over the same dates a year earlier; zero without a comparison
 * @param previousCredit the same for credit
 */
public record PartyTrendPoint(LocalDate start, LocalDate end, String label,
                              BigDecimal debit, BigDecimal credit,
                              BigDecimal previousDebit, BigDecimal previousCredit) {

    /** What the period added to what is owed: charged less paid. Negative when more came in. */
    public BigDecimal net() {
        return debit.subtract(credit);
    }
}
