package com.hamza.account.features.currency.difference;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One movement of a foreign account in the report's period, with what the average rate made of it - the
 * lines a row of the report opens, which add up to the row's realized figure to the piastre (ق-هـ٦).
 *
 * <p>The line a drawer opens with - what was {@link #broughtForward brought forward} - has no movement:
 * its own, book and realized figures are {@code null}, and its balance and average are the period's start.</p>
 *
 * @param own          what the balance moved by, in the account's currency, in the balance's direction
 * @param book         what the book value moved by, in the base
 * @param ownAfter     the balance after it
 * @param averageAfter the average rate after it; {@code null} with nothing left
 * @param realized     what it realized, as a gain (positive) or a loss to the shop; zero for a movement that
 *                     grew the balance
 */
public record ExchangeMovementLine(LocalDate date, String labelKey, long reference, BigDecimal own,
                                   BigDecimal book, BigDecimal ownAfter, BigDecimal averageAfter,
                                   BigDecimal realized) {

    public ExchangeMovementLine {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(labelKey, "labelKey");
    }

    /** The balance the period starts from, and its average rate, dated the day before it. */
    public static ExchangeMovementLine broughtForward(LocalDate dayBefore, BigDecimal own, BigDecimal average) {
        return new ExchangeMovementLine(dayBefore, "currency.difference.brought.forward", 0, null, null, own,
                average, null);
    }

    public boolean isBroughtForward() {
        return own == null;
    }

    /** Base units per unit the movement moved at - its own figures divided, never stored; {@code null} without one. */
    public BigDecimal rate() {
        if (own == null || own.signum() == 0) {
            return null;
        }
        return book.divide(own, MathContext.DECIMAL64).abs().round(new MathContext(6, RoundingMode.HALF_UP));
    }
}
