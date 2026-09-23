package com.hamza.account.features.currency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static com.hamza.account.features.currency.CurrencyFixtures.EGP;
import static com.hamza.account.features.currency.CurrencyFixtures.JPY;
import static com.hamza.account.features.currency.CurrencyFixtures.KWD;
import static com.hamza.account.features.currency.CurrencyFixtures.SAR;
import static com.hamza.account.features.currency.CurrencyFixtures.USD;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Currency#symbolFor}: the symbol beside an amount follows the interface's language, the way the
 * program's own text does. The dashboard used to take it from the bundle - "ج.م" in Arabic, "L.E." in
 * English - and a currency answers the same two ways now.
 */
class CurrencySymbolTest {

    private static final Locale ARABIC = Locale.of("ar");
    private static final Locale ARABIC_SAUDI = Locale.of("ar", "SA");

    @Test
    @DisplayName("the Arabic interface writes the Arabic symbol, whatever the country")
    void arabic() {
        assertEquals("ج.م", EGP.symbolFor(ARABIC));
        assertEquals("ر.س", SAR.symbolFor(ARABIC_SAUDI));
        assertEquals("$", USD.symbolFor(ARABIC));
    }

    @Test
    @DisplayName("any other interface writes the English symbol the shop set")
    void english() {
        assertEquals("L.E.", EGP.symbolFor(Locale.ENGLISH));
        assertEquals("SAR", SAR.symbolFor(Locale.ENGLISH));
        assertEquals("SAR", SAR.symbolFor(Locale.FRENCH), "not English alone: every language that is not Arabic");
    }

    @Test
    @DisplayName("with none set: the Arabic symbol when it has no Arabic letter, else the ISO code")
    void fallback() {
        assertEquals("$", USD.symbolFor(Locale.ENGLISH));
        assertEquals("¥", JPY.symbolFor(Locale.ENGLISH));
        assertEquals("KWD", KWD.symbolFor(Locale.ENGLISH), "never 'د.ك' in an English line");
    }

    @Test
    @DisplayName("an Arabic letter is what decides, not a symbol or a digit")
    void arabicLetter() {
        assertEquals(true, Currency.hasArabicLetter("ج.م"));
        assertEquals(true, Currency.hasArabicLetter("L.ج"));
        assertEquals(false, Currency.hasArabicLetter("L.E."));
        assertEquals(false, Currency.hasArabicLetter("₺"));
        assertEquals(false, Currency.hasArabicLetter(""));
        assertEquals(false, Currency.hasArabicLetter(null));
    }
}
