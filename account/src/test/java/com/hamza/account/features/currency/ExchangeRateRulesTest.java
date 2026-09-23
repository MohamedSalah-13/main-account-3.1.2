package com.hamza.account.features.currency;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.hamza.account.features.currency.CurrencyFixtures.DAY;
import static com.hamza.account.features.currency.CurrencyFixtures.EGP;
import static com.hamza.account.features.currency.CurrencyFixtures.USD;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExchangeRateRulesTest {

    private static ExchangeRateDraft draft(String rate) {
        return new ExchangeRateDraft(0, USD.id(), DAY, rate == null ? null : new BigDecimal(rate), null);
    }

    private static String refusal(ExchangeRateDraft draft, Currency currency) {
        return assertThrows(UserValidationException.class,
                () -> ExchangeRateRules.requireValid(draft, currency)).getMessage();
    }

    @Test
    @DisplayName("a positive rate within DECIMAL(20, 10), for a currency that is not the base, is accepted")
    void accepted() {
        assertDoesNotThrow(() -> ExchangeRateRules.requireValid(draft("48.5"), USD));
        assertDoesNotThrow(() -> ExchangeRateRules.requireValid(draft("0.0000186700"), USD));
        assertDoesNotThrow(() -> ExchangeRateRules.requireValid(draft("9999999999.9999999999"), USD));
    }

    @Test
    @DisplayName("the base has no rate - it is worth one of itself")
    void notForTheBase() {
        assertEquals("currency.rate.error.base", refusal(draft("1"), EGP));
    }

    @Test
    @DisplayName("a rate needs a currency that exists and a day")
    void currencyAndDay() {
        assertEquals("currency.error.not.found", refusal(draft("48.5"), null));
        assertEquals("currency.rate.error.date",
                refusal(new ExchangeRateDraft(0, USD.id(), null, new BigDecimal("48.5"), null), USD));
    }

    @Test
    @DisplayName("zero, a negative and nothing are refused - none of them is a rate")
    void positive() {
        assertEquals("currency.rate.error.positive", refusal(draft("0"), USD));
        assertEquals("currency.rate.error.positive", refusal(draft("-48.5"), USD));
        assertEquals("currency.rate.error.positive", refusal(draft(null), USD));
    }

    @Test
    @DisplayName("more places than the column holds is refused rather than rounded behind the person's back")
    void precision() {
        assertEquals("currency.rate.error.precision", refusal(draft("0.00001867001"), USD));
        // Trailing zeros are not places: 48.50000000000 is 48.5.
        assertDoesNotThrow(() -> ExchangeRateRules.requireValid(draft("48.500000000000"), USD));
        assertEquals("currency.rate.error.too.large", refusal(draft("10000000000"), USD));
    }

    @Test
    @DisplayName("a note is kept within its column, and a blank one is no note")
    void notes() {
        assertEquals("currency.rate.error.notes",
                refusal(new ExchangeRateDraft(0, USD.id(), DAY, BigDecimal.TEN, "ن".repeat(201)), USD));
        assertNull(new ExchangeRateDraft(0, USD.id(), DAY, BigDecimal.TEN, "   ").notes());
    }
}
