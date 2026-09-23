package com.hamza.account.features.currency.difference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The average-rate walk on figures worked out by hand (docs/currency-plan.md §16 ق-هـ٢). */
class AveragePositionTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, d(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    @Test
    @DisplayName("a thousand dollars owed at 48 and paid at 50 realizes 2,000 and leaves nothing unrealized")
    void settledAtAnotherRate() {
        AveragePosition position = new AveragePosition();
        position.apply(d("1000"), d("48000"));
        position.apply(d("-1000"), d("-50000"));
        AveragePosition.Snapshot after = position.snapshot();

        assertMoney("0", after.own());
        assertMoney("-2000", after.book());
        assertMoney("2000", after.realizedToDate());
        assertMoney("0", after.unrealizedAt(d("51")));
        assertNull(after.averageRate());
    }

    @Test
    @DisplayName("200 bought at 48 and 100 sold for 5,000: 200 realized, 200 unrealized at 50, 400 in all")
    void partOfABalanceSold() {
        AveragePosition position = new AveragePosition();
        position.apply(d("200"), d("9600"));
        position.apply(d("-100"), d("-5000"));
        AveragePosition.Snapshot after = position.snapshot();

        assertMoney("100", after.own());
        assertMoney("4800", after.roundedCost());
        assertMoney("4600", after.book());
        assertMoney("200", after.realizedToDate());
        assertMoney("200", after.unrealizedAt(d("50")));
        assertMoney("5000", after.valueAt(d("50")));
        assertMoney("48", after.averageRate());
        // Together they are the valuation difference: 5,000 at today's rate less a book value of 4,600.
        assertMoney("400", after.realizedToDate().add(after.unrealizedAt(d("50"))));
    }

    @Test
    @DisplayName("two purchases average: 100 at 48 and 100 at 50 are 200 at 49, and 100 sold at 51 realizes 200")
    void acquisitionsAverage() {
        AveragePosition position = new AveragePosition();
        position.apply(d("100"), d("4800"));
        position.apply(d("100"), d("5000"));
        assertMoney("49", position.snapshot().averageRate());

        position.apply(d("-100"), d("-5100"));
        assertMoney("200", position.snapshot().realizedToDate());
        assertMoney("4900", position.snapshot().roundedCost());
    }

    @Test
    @DisplayName("a payment past the balance closes it at the average and opens the rest at its own rate")
    void overshootOpensTheOtherWay() {
        AveragePosition position = new AveragePosition();
        position.apply(d("100"), d("4800"));
        position.apply(d("-150"), d("-7500"));
        AveragePosition.Snapshot after = position.snapshot();

        assertMoney("-50", after.own());
        assertMoney("-2500", after.roundedCost());
        assertMoney("50", after.averageRate());
        // Only the hundred that closed realized anything: 100 x (50 - 48).
        assertMoney("200", after.realizedToDate());
        assertMoney("0", after.unrealizedAt(d("50")));
    }

    @Test
    @DisplayName("a movement with nothing in the currency moves the book value alone, realized on its day")
    void baseOnlyMovementIsRealized() {
        AveragePosition position = new AveragePosition();
        position.apply(d("100"), d("4800"));
        position.apply(BigDecimal.ZERO, d("0.03"));

        assertMoney("100", position.snapshot().own());
        assertMoney("4800", position.snapshot().roundedCost());
        assertMoney("-0.03", position.snapshot().realizedToDate());
    }

    @Test
    @DisplayName("the cost is rounded once, where it is read, and the identity holds to the piastre")
    void costRoundedOnce() {
        AveragePosition position = new AveragePosition();
        position.apply(d("1"), d("10.00"));
        position.apply(d("1"), d("10.01"));
        position.apply(d("1"), d("10.01"));
        position.apply(d("-1"), d("-10.00"));
        AveragePosition.Snapshot after = position.snapshot();

        // Sold at 10.00 against an average of 10.00667: a loss of 0.00667, a piastre.
        assertMoney("20.01", after.roundedCost());
        assertMoney("-0.01", after.realizedToDate());
        BigDecimal rate = d("10.5");
        assertEquals(after.valueAt(rate).subtract(after.book()),
                after.realizedToDate().add(after.unrealizedAt(rate)));
    }

    @Test
    @DisplayName("nothing held is worth nothing at any rate, and a balance with no rate has no value at all")
    void valueNeedsARateOnlyForABalance() {
        AveragePosition empty = new AveragePosition();
        assertMoney("0", empty.snapshot().valueAt(null));
        assertMoney("0", empty.snapshot().unrealizedAt(null));

        AveragePosition held = new AveragePosition();
        held.apply(d("10"), d("480"));
        assertNull(held.snapshot().valueAt(null));
        assertNull(held.snapshot().unrealizedAt(null));
    }

    @Test
    @DisplayName("a customer who paid in advance holds a negative balance, and a rising rate is a loss on it")
    void advanceIsALiability() {
        AveragePosition position = new AveragePosition();
        position.apply(d("-100"), d("-4800"));
        AveragePosition.Snapshot after = position.snapshot();

        assertMoney("-200", ExchangeAccountKind.CUSTOMER.gain(after.unrealizedAt(d("50"))));
        assertMoney("200", ExchangeAccountKind.SUPPLIER.gain(after.unrealizedAt(d("50"))));
    }
}
