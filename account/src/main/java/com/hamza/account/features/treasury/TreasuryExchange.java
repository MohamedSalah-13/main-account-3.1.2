package com.hamza.account.features.treasury;

import com.hamza.account.features.currency.Currency;
import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * What a movement of cash stores when a treasury may be in a currency other than the base
 * (docs/currency-plan.md §11). No database and no JavaFX: every figure a transfer or a deposit writes is
 * decided here, over plain values, and {@code TreasuryExchangeTest} asks each case.
 * <p>
 * <b>The books move one figure, in the base.</b> A transfer's {@code amount} leaves the source and
 * reaches the destination unchanged, so {@code treasury_current_balance} and every report over it read
 * what they always read, and the owner's choice holds: a difference between the rate dollars were
 * bought at and the rate they were sold at is shown as a valuation difference, never posted (ق-ب٢).
 * Beside it, each side that is not in the base records what it gave or received in its own currency.
 * <p>
 * <b>An exchange is two real amounts</b> (ق-ب٥): what left the source and what reached the destination,
 * as the money changer counted them. Its base figure is the base side when there is one - the pounds
 * paid for dollars, the pounds received for them - and otherwise what left, at its currency's recorded
 * rate on the day. The rate the two amounts imply is shown and never stored: it is their quotient.
 */
public final class TreasuryExchange {

    /** Every amount column in the books is {@code DECIMAL(14, 2)}, whatever the base's own places. */
    public static final int BOOK_SCALE = 2;

    private TreasuryExchange() {
    }

    /**
     * What a transfer stores.
     *
     * @param baseAmount the figure the books move - out of the source and into the destination
     * @param amountFrom what left the source in its own currency; {@code null} when the source is in the base
     * @param amountTo   what reached the destination in its own currency; {@code null} when it is in the base
     */
    public record Figures(BigDecimal baseAmount, BigDecimal amountFrom, BigDecimal amountTo) {

        /** What left the source, in the source's own currency. */
        public BigDecimal sent() {
            return amountFrom != null ? amountFrom : baseAmount;
        }

        /** What reached the destination, in the destination's own currency. */
        public BigDecimal received() {
            return amountTo != null ? amountTo : baseAmount;
        }
    }

    /**
     * The figures of a transfer from a treasury in {@code from} to one in {@code to}.
     *
     * @param from       the source's currency - {@code null} for the base
     * @param to         the destination's currency - {@code null} for the base
     * @param sent       what left the source, in its currency
     * @param received   what reached the destination, in its currency; read only when the two currencies
     *                   differ - between two treasuries in one currency what arrives is what left
     * @param sourceRate the source currency's recorded rate on the day, needed only when neither side is the
     *                   base; {@code null} there is a refusal, never a guess (ق-٣)
     */
    public static Figures transfer(Currency from, Currency to, BigDecimal sent, BigDecimal received,
                                   BigDecimal sourceRate) throws UserValidationException {
        requirePositive(sent, "treasury.transfer.error.amount");
        requirePlaces(sent, from);
        boolean sameCurrency = sameCurrency(from, to);
        if (!sameCurrency) {
            requirePositive(received, "treasury.exchange.error.received");
            requirePlaces(received, to);
        }
        if (isBase(from) && isBase(to)) {
            return new Figures(sent, null, null);
        }
        if (isBase(from)) {
            return new Figures(sent, null, received);
        }
        if (isBase(to)) {
            return new Figures(received, sent, null);
        }
        if (sourceRate == null || sourceRate.signum() <= 0) {
            throw new UserValidationException("currency.error.no.rate");
        }
        BigDecimal base = baseOf(sent, sourceRate);
        return new Figures(base, sent, sameCurrency ? sent : received);
    }

    /**
     * An amount of a foreign currency in the base, at {@code rate}: rounded once, half up, to the books'
     * two places (ق-٥). The rate is the one copied onto the row, so correcting that day's rate later
     * rewrites nothing already recorded (ق-٤).
     */
    public static BigDecimal baseOf(BigDecimal foreignAmount, BigDecimal rate) {
        return foreignAmount.multiply(rate).setScale(BOOK_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The rate two amounts imply - base units per one unit of the foreign side - for the screen to show.
     * {@code null} when there is nothing to divide by.
     */
    public static BigDecimal impliedRate(BigDecimal baseAmount, BigDecimal foreignAmount) {
        if (baseAmount == null || foreignAmount == null || foreignAmount.signum() == 0) {
            return null;
        }
        return baseAmount.divide(foreignAmount, MathContext.DECIMAL128).setScale(10, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    /**
     * Refuses an amount with more places than its currency has: 10.555 dollars is a figure nobody
     * counted, and a column would round it silently. The base is held to the books' two places.
     */
    public static void requirePlaces(BigDecimal amount, Currency currency) throws UserValidationException {
        int places = isBase(currency) ? BOOK_SCALE : currency.decimalPlaces();
        if (amount.stripTrailingZeros().scale() > places) {
            throw new UserValidationException("treasury.exchange.error.places");
        }
    }

    /** {@code null} and the flagged base are the same currency - a treasury names none for the base. */
    static boolean isBase(Currency currency) {
        return currency == null || currency.base();
    }

    static boolean sameCurrency(Currency a, Currency b) {
        if (isBase(a) || isBase(b)) {
            return isBase(a) && isBase(b);
        }
        return a.id() == b.id();
    }

    private static void requirePositive(BigDecimal amount, String key) throws UserValidationException {
        if (amount == null || amount.signum() <= 0) {
            throw new UserValidationException(key);
        }
    }
}
