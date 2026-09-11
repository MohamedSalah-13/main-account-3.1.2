package com.hamza.account.features.party.trend;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One day's movement on a ledger, summed over the parties the chart covers.
 *
 * @param day    the movement date ({@code account_date})
 * @param debit  what was charged that day - see {@code PartyTrendQuery.DEBIT}
 * @param credit what was paid or credited that day - see {@code PartyTrendQuery.CREDIT}
 */
public record PartyTrendDay(LocalDate day, BigDecimal debit, BigDecimal credit) {

    public PartyTrendDay {
        Objects.requireNonNull(day, "day");
        debit = debit == null ? PartyTrend.NONE : debit;
        credit = credit == null ? PartyTrend.NONE : credit;
    }
}
