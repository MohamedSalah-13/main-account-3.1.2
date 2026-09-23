package com.hamza.account.features.currency;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.hamza.account.features.currency.CurrencyFixtures.DAY;
import static com.hamza.account.features.currency.CurrencyFixtures.EGP;
import static com.hamza.account.features.currency.CurrencyFixtures.JPY;
import static com.hamza.account.features.currency.CurrencyFixtures.KWD;
import static com.hamza.account.features.currency.CurrencyFixtures.SAR;
import static com.hamza.account.features.currency.CurrencyFixtures.USD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrencyConverterTest {

    private static BigDecimal of(String value) {
        return new BigDecimal(value);
    }

    @Nested
    @DisplayName("the arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("a foreign amount goes to the base by multiplying: 100 dollars at 48.5 is 4,850.00")
        void toBase() {
            assertEquals(of("4850.00"), CurrencyConverter.toBase(of("100"), of("48.5"), EGP));
        }

        @Test
        @DisplayName("and comes back by dividing, to the target's own places")
        void fromBase() {
            assertEquals(of("100.00"), CurrencyConverter.fromBase(of("4850"), of("48.5"), USD));
            // 1000 pounds at 155.3 pounds to the dinar is 6.439150... dinars: three places, not two.
            assertEquals(of("6.439"), CurrencyConverter.fromBase(of("1000"), of("155.3"), KWD));
            // A yen has none.
            assertEquals(of("3094"), CurrencyConverter.fromBase(of("1000"), of("0.3232"), JPY));
        }

        @Test
        @DisplayName("rounding is half up: a half piastre goes up, not to the nearest even")
        void halfUp() {
            // 0.5 * 0.05 = 0.025 -> 0.03 half up; banker's rounding would say 0.02.
            assertEquals(of("0.03"), CurrencyConverter.toBase(of("0.5"), of("0.05"), EGP));
            assertEquals(of("0.13"), CurrencyConverter.toBase(of("0.25"), of("0.5"), EGP));
        }

        @Test
        @DisplayName("between two foreign currencies it passes through the base unrounded, and rounds once")
        void crossRoundsOnce() {
            // 1,000.03 riyals at 12.93 -> 12,930.3879 pounds -> at 48.51 dollars: 266.5510... = 266.55.
            BigDecimal once = CurrencyConverter.convert(of("1000.03"), of("12.93"), of("48.51"), USD);
            assertEquals(of("266.55"), once);
            // 1,004.66 riyals is 267.78507... dollars. Rounded to the pound first (12,990.25) and then to
            // the dollar, it comes out a cent short - which is why the path through the base is unrounded.
            BigDecimal direct = CurrencyConverter.convert(of("1004.66"), of("12.93"), of("48.51"), USD);
            BigDecimal twice = CurrencyConverter.fromBase(
                    CurrencyConverter.toBase(of("1004.66"), of("12.93"), EGP), of("48.51"), USD);
            assertEquals(of("267.79"), direct);
            assertEquals(of("267.78"), twice);
        }

        @Test
        @DisplayName("the inverse is written to the column's ten places, without trailing zeros")
        void inverse() {
            assertEquals(of("0.02"), CurrencyConverter.inverse(of("50")));
            assertEquals(of("0.0206185567"), CurrencyConverter.inverse(of("48.5")));
        }

        @Test
        @DisplayName("a rate of zero or below is a bug upstream, not a conversion")
        void noZeroRate() {
            assertThrows(IllegalArgumentException.class, () -> CurrencyConverter.toBase(of("1"), of("0"), EGP));
            assertThrows(IllegalArgumentException.class, () -> CurrencyConverter.inverse(of("-1")));
        }
    }

    @Nested
    @DisplayName("the converter's table")
    class Table {

        private final Map<Integer, RateInForce> rates = Map.of(
                USD.id(), new RateInForce(USD.id(), DAY.minusDays(2), of("48.5"), of("48.0")),
                SAR.id(), new RateInForce(SAR.id(), DAY, of("12.93"), null));

        @Test
        @DisplayName("an amount of the base is written in every other currency with a rate")
        void fromTheBase() throws Exception {
            List<CurrencyConversion> lines = CurrencyConversion.table(of("4850"), EGP,
                    List.of(EGP, SAR, USD), rates);
            assertEquals(2, lines.size(), "the currency converted from is not listed against itself");
            assertEquals(of("375.10"), lines.get(0).amount());
            assertEquals(of("100.00"), lines.get(1).amount());
            assertEquals(USD.id(), lines.get(1).rate().currencyId());
        }

        @Test
        @DisplayName("a foreign amount is written in the base at one and in the others through it")
        void fromAForeignCurrency() throws Exception {
            List<CurrencyConversion> lines = CurrencyConversion.table(of("100"), USD,
                    List.of(EGP, SAR, USD), rates);
            assertEquals(of("4850.00"), lines.get(0).amount());
            assertNull(lines.get(0).rate(), "the base has no rate row - it is one");
            assertEquals(of("375.10"), lines.get(1).amount());
        }

        @Test
        @DisplayName("a currency without a rate is listed without an amount, never with a zero")
        void missingRateIsMissing() throws Exception {
            List<CurrencyConversion> lines = CurrencyConversion.table(of("100"), USD,
                    List.of(EGP, SAR, USD, KWD), rates);
            CurrencyConversion dinar = lines.get(2);
            assertEquals(KWD, dinar.target());
            assertFalse(dinar.available());
            assertTrue(lines.get(0).available());
        }

        @Test
        @DisplayName("nothing can be said about an amount in a currency with no rate itself")
        void sourceWithoutARate() {
            assertEquals("currency.convert.error.no.rate", assertThrows(UserValidationException.class,
                    () -> CurrencyConversion.table(of("100"), KWD, List.of(EGP, USD), rates)).getMessage());
        }
    }

    @Nested
    @DisplayName("a rate in force")
    class InForce {

        @Test
        @DisplayName("says how far it moved from the one before it, and nothing when there was none")
        void change() {
            assertEquals(of("1.04"), new RateInForce(USD.id(), DAY, of("48.5"), of("48.0")).changePercent());
            assertEquals(of("-2.00"), new RateInForce(USD.id(), DAY, of("49"), of("50")).changePercent());
            assertNull(new RateInForce(USD.id(), DAY, of("48.5"), null).changePercent());
        }

        @Test
        @DisplayName("says how old it is on a day, and a rate dated after the day is not old")
        void age() {
            RateInForce rate = new RateInForce(USD.id(), DAY.minusDays(3), of("48.5"), null);
            assertEquals(3, rate.ageOn(DAY));
            assertEquals(0, rate.ageOn(DAY.minusDays(10)));
        }
    }
}
