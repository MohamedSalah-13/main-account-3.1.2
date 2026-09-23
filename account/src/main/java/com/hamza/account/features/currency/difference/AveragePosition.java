package com.hamza.account.features.currency.difference;

import com.hamza.account.features.treasury.TreasuryExchange;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * One foreign account walked movement by movement at its average rate (docs/currency-plan.md §16 ق-هـ٢).
 *
 * <p>Three figures are carried. The <b>balance</b> in the account's own currency and the <b>book value</b> in
 * the base are what its statement already shows. The <b>cost</b> is what the balance cost at the rates it was
 * acquired at: a movement that grows the balance adds its base value to it, and one that shrinks the balance
 * takes out the share of it that left, at the average - so the difference between what left in the base and
 * that share stays behind in the book value as the realized difference. From those three, by construction:</p>
 * <ul>
 *     <li>realized to date = cost − book value;</li>
 *     <li>unrealized on a day = balance × that day's rate − cost;</li>
 *     <li>and the two together = balance × rate − book value: the valuation difference the treasuries screen
 *     has shown since phase B.</li>
 * </ul>
 *
 * <p>Every figure here is in the balance's own direction; {@link ExchangeAccountKind#gain} turns it into the
 * shop's. The cost is carried unrounded and rounded once, where it is read (ق-هـ٦), so the identity holds to
 * the piastre and so do the realized figures of the movements summed over a period.</p>
 */
final class AveragePosition {

    private static final MathContext PRECISION = MathContext.DECIMAL128;

    private BigDecimal own = BigDecimal.ZERO;
    private BigDecimal cost = BigDecimal.ZERO;
    private BigDecimal book = BigDecimal.ZERO;

    /**
     * Moves the position by one movement.
     * <ul>
     *     <li>With nothing in the account's currency it moves the book value alone, so its base value is
     *     realized on its day.</li>
     *     <li>Growing the balance - in its direction, or from zero - it is acquired at its own rate.</li>
     *     <li>Shrinking it, the share of the cost that left goes at the average; what goes past zero opens a
     *     new balance the other way at the movement's own rate.</li>
     * </ul>
     */
    void apply(BigDecimal ownDelta, BigDecimal bookDelta) {
        book = book.add(bookDelta);
        if (ownDelta.signum() == 0) {
            return;
        }
        if (own.signum() == 0 || own.signum() == ownDelta.signum()) {
            own = own.add(ownDelta);
            cost = cost.add(bookDelta);
            return;
        }
        BigDecimal closed = ownDelta.abs().min(own.abs());
        BigDecimal kept = own.abs().subtract(closed).divide(own.abs(), PRECISION);
        cost = cost.multiply(kept, PRECISION);
        BigDecimal after = own.add(ownDelta);
        if (after.signum() != 0 && after.signum() != own.signum()) {
            cost = bookDelta.multiply(after, PRECISION).divide(ownDelta, PRECISION);
        }
        own = after;
        if (own.signum() == 0) {
            cost = BigDecimal.ZERO;
        }
    }

    Snapshot snapshot() {
        return new Snapshot(own, cost, book);
    }

    /**
     * The position after some movement.
     *
     * @param own  the balance in the account's currency
     * @param cost what that balance cost in the base, unrounded
     * @param book the book value in the base, as the statement shows it
     */
    record Snapshot(BigDecimal own, BigDecimal cost, BigDecimal book) {

        static final Snapshot EMPTY = new Snapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        /** The cost as the books write money: to two places, half up, once. */
        BigDecimal roundedCost() {
            return cost.setScale(TreasuryExchange.BOOK_SCALE, RoundingMode.HALF_UP);
        }

        /** Cost less book value: what the settlements so far left behind, in the balance's direction. */
        BigDecimal realizedToDate() {
            return roundedCost().subtract(book);
        }

        /** Base units per unit the balance was acquired at; {@code null} with nothing held. */
        BigDecimal averageRate() {
            return own.signum() == 0 ? null : cost.divide(own, PRECISION).abs();
        }

        /**
         * What the balance is worth at {@code rate}, rounded as the treasuries screen rounds it
         * ({@link TreasuryExchange#baseOf}). Zero with nothing held, whatever the rate; {@code null} when there
         * is a balance and no rate - never a guess (ق-هـ٥).
         */
        BigDecimal valueAt(BigDecimal rate) {
            if (own.signum() == 0) {
                return BigDecimal.ZERO.setScale(TreasuryExchange.BOOK_SCALE);
            }
            return rate == null ? null : TreasuryExchange.baseOf(own, rate);
        }

        /** Value at {@code rate} less the cost, in the balance's direction; {@code null} as {@link #valueAt}. */
        BigDecimal unrealizedAt(BigDecimal rate) {
            BigDecimal value = valueAt(rate);
            return value == null ? null : value.subtract(roundedCost());
        }
    }
}
