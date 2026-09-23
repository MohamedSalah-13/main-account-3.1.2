package com.hamza.account.features.currency.difference;

import com.hamza.account.features.currency.Currency;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * One foreign account over a period (docs/currency-plan.md §16).
 *
 * <p>The balance, the book value and the value at a rate are in the balance's own direction - what the
 * account's statement shows. Every difference is a gain (positive) or a loss to the shop (ق-هـ٤).</p>
 *
 * <p><b>Four identities hold by construction</b>, and the tests hold them: the realized to date and the
 * unrealized at the end are the {@link #totalEnd() whole difference}; the period's {@link #result() result} is
 * its realized figure and the change in the unrealized; and the lines' realized figures sum to the
 * period's. An unrealized figure is {@code null} when the account held a balance on a day its currency had
 * no rate (ق-هـ٥) - and then so are the change, the result and the whole difference, never a zero.</p>
 *
 * @param ownStart         the balance on the day before the period, in the account's currency
 * @param bookStart        its book value
 * @param averageStart     the average rate it was held at; {@code null} with nothing held
 * @param rateStart        the rate in force on the day before the period; {@code null} without one
 * @param ownEnd           the balance at the end of the period
 * @param bookEnd          its book value
 * @param averageEnd       the average rate it is held at
 * @param rateEnd          the rate in force on the last day
 * @param valueEnd         the balance at that rate, rounded as the treasuries screen rounds it; zero with
 *                         nothing held, {@code null} with a balance and no rate
 * @param realizedPeriod   realized by the period's movements
 * @param realizedToDate   realized by every movement up to the end
 * @param unrealizedStart  on the balance held the day before the period, at that day's rate
 * @param unrealizedEnd    on the balance held at the end, at the last day's rate
 * @param lines            the period's movements, oldest first
 */
public record ExchangeDifferenceRow(ExchangeAccount account, Currency currency,
                                    BigDecimal ownStart, BigDecimal bookStart, BigDecimal averageStart,
                                    BigDecimal rateStart,
                                    BigDecimal ownEnd, BigDecimal bookEnd, BigDecimal averageEnd,
                                    BigDecimal rateEnd, BigDecimal valueEnd,
                                    BigDecimal realizedPeriod, BigDecimal realizedToDate,
                                    BigDecimal unrealizedStart, BigDecimal unrealizedEnd,
                                    List<ExchangeMovementLine> lines) {

    public ExchangeDifferenceRow {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(currency, "currency");
        lines = List.copyOf(lines);
    }

    /** Whether both ends could be valued - the account's result is known. */
    public boolean valued() {
        return unrealizedStart != null && unrealizedEnd != null;
    }

    /** How far the unrealized moved over the period; {@code null} when either end has no rate. */
    public BigDecimal unrealizedChange() {
        return valued() ? unrealizedEnd.subtract(unrealizedStart) : null;
    }

    /** The period's whole result: realized, and the change in the unrealized (ق-هـ٣). */
    public BigDecimal result() {
        BigDecimal change = unrealizedChange();
        return change == null ? null : realizedPeriod.add(change);
    }

    /**
     * Realized to date and unrealized at the end: the whole difference between the balance at the last day's
     * rate and its book value - for a treasury on today's date, the treasuries screen's valuation difference.
     */
    public BigDecimal totalEnd() {
        return unrealizedEnd == null ? null : realizedToDate.add(unrealizedEnd);
    }
}
