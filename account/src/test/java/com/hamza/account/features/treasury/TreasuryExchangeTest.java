package com.hamza.account.features.treasury;

import com.hamza.account.features.currency.Currency;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link TreasuryExchange}: the figures a transfer stores between treasuries that may be in different
 * currencies (docs/currency-plan.md §11). The books move one base figure out of the source and into the
 * destination; each foreign side records its own amount beside it.
 */
class TreasuryExchangeTest {

    private static final Currency EGP = new Currency(1, "EGP", "جنيه مصري", "ج.م", "L.E.", 2, true, true, 1);
    private static final Currency USD = new Currency(3, "USD", "دولار أمريكي", "$", "$", 2, false, true, 3);
    private static final Currency SAR = new Currency(2, "SAR", "ريال سعودي", "ر.س", "SAR", 2, false, true, 2);
    private static final Currency KWD = new Currency(4, "KWD", "دينار كويتي", "د.ك", "", 3, false, true, 4);
    private static final Currency JPY = new Currency(5, "JPY", "ين ياباني", "¥", "", 0, false, true, 5);

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static String refusal(org.junit.jupiter.api.function.Executable call) {
        return assertThrows(UserValidationException.class, call).getMessage();
    }

    @Nested
    @DisplayName("a transfer")
    class Transfers {

        @Test
        @DisplayName("between two treasuries in the base is what it always was: one amount, no foreign side")
        void baseToBase() throws Exception {
            var figures = TreasuryExchange.transfer(null, null, d("500"), null, null);
            assertEquals(d("500"), figures.baseAmount());
            assertNull(figures.amountFrom());
            assertNull(figures.amountTo());
            // The flagged base is the base too - a treasury names none, a screen may pass it.
            assertEquals(d("500"), TreasuryExchange.transfer(EGP, null, d("500"), d("9"), null).baseAmount());
        }

        @Test
        @DisplayName("buying dollars: the books move the pounds paid, the dollar treasury records the dollars")
        void baseToForeign() throws Exception {
            var figures = TreasuryExchange.transfer(null, USD, d("4850"), d("100"), null);
            assertEquals(d("4850"), figures.baseAmount());
            assertNull(figures.amountFrom());
            assertEquals(d("100"), figures.amountTo());
            assertEquals(d("48.5"), TreasuryExchange.impliedRate(figures.baseAmount(), figures.amountTo()));
        }

        @Test
        @DisplayName("selling dollars: the books move the pounds received, whatever the dollars were bought at")
        void foreignToBase() throws Exception {
            var figures = TreasuryExchange.transfer(USD, null, d("100"), d("5000"), null);
            assertEquals(d("5000"), figures.baseAmount(), "the difference from the purchase stays a valuation (ق-ب٢)");
            assertEquals(d("100"), figures.amountFrom());
            assertNull(figures.amountTo());
        }

        @Test
        @DisplayName("riyals for dollars: both amounts as counted, the books at the riyal's rate on the day")
        void foreignToForeign() throws Exception {
            var figures = TreasuryExchange.transfer(SAR, USD, d("375"), d("100"), d("12.93"));
            assertEquals(d("4848.75"), figures.baseAmount());
            assertEquals(d("375"), figures.amountFrom());
            assertEquals(d("100"), figures.amountTo());
        }

        @Test
        @DisplayName("between two dollar treasuries: one amount, the books at the dollar's rate, received ignored")
        void sameForeignCurrency() throws Exception {
            var figures = TreasuryExchange.transfer(USD, USD, d("100"), d("999"), d("48.5"));
            assertEquals(d("4850.00"), figures.baseAmount());
            assertEquals(d("100"), figures.amountFrom());
            assertEquals(d("100"), figures.amountTo(), "what arrives is what left");
        }

        @Test
        @DisplayName("without the source's rate, a transfer with no base side is refused - never a guess")
        void noRateIsARefusal() {
            assertEquals("currency.error.no.rate",
                    refusal(() -> TreasuryExchange.transfer(SAR, USD, d("375"), d("100"), null)));
            assertEquals("currency.error.no.rate",
                    refusal(() -> TreasuryExchange.transfer(USD, USD, d("100"), null, BigDecimal.ZERO)));
        }

        @Test
        @DisplayName("two currencies need what was received, and every amount must be above zero")
        void amounts() {
            assertEquals("treasury.transfer.error.amount",
                    refusal(() -> TreasuryExchange.transfer(null, USD, BigDecimal.ZERO, d("1"), null)));
            assertEquals("treasury.transfer.error.amount",
                    refusal(() -> TreasuryExchange.transfer(null, null, null, null, null)));
            assertEquals("treasury.exchange.error.received",
                    refusal(() -> TreasuryExchange.transfer(null, USD, d("4850"), null, null)));
            assertEquals("treasury.exchange.error.received",
                    refusal(() -> TreasuryExchange.transfer(USD, null, d("100"), d("-5000"), null)));
        }

        @Test
        @DisplayName("an amount with more places than its currency has is refused, not rounded")
        void places() throws Exception {
            assertEquals("treasury.exchange.error.places",
                    refusal(() -> TreasuryExchange.transfer(null, USD, d("4850"), d("100.555"), null)));
            assertEquals("treasury.exchange.error.places",
                    refusal(() -> TreasuryExchange.transfer(null, JPY, d("4850"), d("100.5"), null)));
            assertEquals("treasury.exchange.error.places",
                    refusal(() -> TreasuryExchange.transfer(null, null, d("10.555"), null, null)));
            // Trailing zeros are not places, and the dinar has three.
            assertEquals(d("1.250"), TreasuryExchange.transfer(null, KWD, d("200"), d("1.250"), null).amountTo());
            assertEquals(d("100.00"), TreasuryExchange.transfer(null, USD, d("4850"), d("100.00"), null).amountTo());
        }
    }

    @Test
    @DisplayName("a foreign amount in the base is rounded once, half up, to the books' two places")
    void baseOf() {
        assertEquals(d("4850.00"), TreasuryExchange.baseOf(d("100"), d("48.5")));
        assertEquals(d("158.30"), TreasuryExchange.baseOf(d("1.000"), d("158.3")));
        assertEquals(d("0.01"), TreasuryExchange.baseOf(d("1"), d("0.005")), "half up");
        assertEquals(d("0.00"), TreasuryExchange.baseOf(d("1"), d("0.0049")));
    }

    @Test
    @DisplayName("the implied rate is the quotient, shown to at most ten places, and absent without a divisor")
    void impliedRate() {
        assertEquals(d("48.5"), TreasuryExchange.impliedRate(d("4850"), d("100")));
        assertEquals(d("0.3333333333"), TreasuryExchange.impliedRate(d("1"), d("3")));
        assertNull(TreasuryExchange.impliedRate(d("1"), BigDecimal.ZERO));
        assertNull(TreasuryExchange.impliedRate(null, d("3")));
    }
}
