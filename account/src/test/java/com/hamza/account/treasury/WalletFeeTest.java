package com.hamza.account.treasury;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wallet fee, and the thing it must never become: a deduction from what the
 * customer paid.
 * <p>
 * A customer settling 1000 on فودافون كاش has paid 1000. The wallet credits 990. If the
 * 10 is taken off the collection instead of posted as the shop's expense, that customer
 * is left owing 10 - every time, for ever. The split is what the acceptance test checks
 * against a real database; the arithmetic is here.
 */
class WalletFeeTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("the fee is a percentage of the amount, at two decimals")
    void theFeeIsAPercentage() {
        assertEquals(money("10.00"), WalletFee.on(money("1000"), money("1")));
        assertEquals(money("2.50"), WalletFee.on(money("500"), money("0.5")));
        assertEquals(money("0.75"), WalletFee.on(money("50"), money("1.5")));
    }

    @Test
    @DisplayName("it is rounded once, HALF_UP, to the scale the money columns use")
    void roundingIsHalfUpAtTwoDecimals() {
        // 333.33 * 1.5% = 4.99995
        assertEquals(money("5.00"), WalletFee.on(money("333.33"), money("1.5")));
        // 1.25% of 10.02 = 0.12525
        assertEquals(money("0.13"), WalletFee.on(money("10.02"), money("1.25")));
        assertEquals(2, WalletFee.on(money("7"), money("3")).scale(),
                "a scale other than 2 would not store as written in DECIMAL(14,2)");
    }

    @Test
    @DisplayName("no percentage, no amount, no fee - and never a negative one")
    void nothingChargesNothing() {
        assertEquals(0, WalletFee.on(money("1000"), BigDecimal.ZERO).signum(),
                "a cash drawer charges nothing");
        assertEquals(0, WalletFee.on(money("1000"), null).signum());
        assertEquals(0, WalletFee.on(null, money("1")).signum());
        assertEquals(0, WalletFee.on(money("0"), money("1")).signum());
        assertEquals(0, WalletFee.on(money("1000"), money("-1")).signum(),
                "a negative percentage would pay the shop for collecting");
    }

    @Test
    @DisplayName("the net is what reaches the treasury, and the customer's side is untouched by it")
    void theNetIsTheTreasurySide() {
        BigDecimal paid = money("1000");
        BigDecimal fee = WalletFee.on(paid, money("1"));

        assertEquals(money("990.00"), WalletFee.net(paid, fee));
        // The customer settled the whole amount; the net is not their number.
        assertEquals(money("1000"), paid);
    }

    @Test
    @DisplayName("a fee at or above the amount is a typo, not a fee")
    void anImplausibleFeeIsRefused() {
        assertTrue(WalletFee.isPlausible(money("1000"), money("10")));
        assertTrue(WalletFee.isPlausible(money("1000"), BigDecimal.ZERO));

        assertFalse(WalletFee.isPlausible(money("1000"), money("1000")),
                "a fee equal to the amount empties the treasury the collection just filled");
        assertFalse(WalletFee.isPlausible(money("1000"), money("1500")));
        assertFalse(WalletFee.isPlausible(money("1000"), money("-1")));
        assertFalse(WalletFee.isPlausible(null, money("10")));
    }

    @Test
    @DisplayName("the expense heading is named, not numbered")
    void theHeadingIsLookedUpByName() {
        assertEquals("عمولات تحويل", WalletFee.EXPENSE_NAME,
                "V21 seeds this heading by name because expenses.id is not auto-increment, "
                        + "so no id could be written into the code in advance");
    }
}
