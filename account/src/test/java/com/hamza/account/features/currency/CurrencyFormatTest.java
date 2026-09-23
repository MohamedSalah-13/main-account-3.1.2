package com.hamza.account.features.currency;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static com.hamza.account.features.currency.CurrencyFixtures.DAY;
import static com.hamza.account.features.currency.CurrencyFixtures.EGP;
import static com.hamza.account.features.currency.CurrencyFixtures.JPY;
import static com.hamza.account.features.currency.CurrencyFixtures.KWD;
import static com.hamza.account.features.currency.CurrencyFixtures.USD;
import static com.hamza.account.features.currency.CurrencyFixtures.rate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CurrencyFormatTest {

    private Locale previous;

    /** The separators follow the machine's symbols; the test fixes them so it reads the same everywhere. */
    @BeforeEach
    void english() {
        previous = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.Category.FORMAT, Locale.US);
    }

    @AfterEach
    void restore() {
        Locale.setDefault(Locale.Category.FORMAT, previous);
    }

    @Test
    @DisplayName("an amount is written to its own currency's places, not to two")
    void amountsKeepTheirPlaces() {
        assertEquals("1,250.500", CurrencyFormat.amount(new BigDecimal("1250.5"), KWD));
        assertEquals("1,500", CurrencyFormat.amount(new BigDecimal("1500"), JPY));
        assertEquals("4,850.00", CurrencyFormat.amount(new BigDecimal("4850"), EGP));
        assertEquals("", CurrencyFormat.amount(null, USD));
    }

    @Test
    @DisplayName("a rate is written with the places it has, up to ten")
    void rates() {
        assertEquals("48.5", CurrencyFormat.rate(new BigDecimal("48.5000000000")));
        assertEquals("0.0000187", CurrencyFormat.rate(new BigDecimal("0.0000187000")));
        assertEquals("89,500", CurrencyFormat.rate(new BigDecimal("89500")));
        assertEquals("", CurrencyFormat.rate(null));
    }

    @Test
    @DisplayName("an inverse is shown to six significant digits, not to the ten it was computed to")
    void indicative() {
        assertEquals("0.077101", CurrencyFormat.indicative(new BigDecimal("0.0771010023")));
        assertEquals("0.00631712", CurrencyFormat.indicative(new BigDecimal("0.0063171194")));
        assertEquals("0.02", CurrencyFormat.indicative(new BigDecimal("0.02")));
        assertEquals("", CurrencyFormat.indicative(null));
    }

    @Test
    @DisplayName("a change is signed, and absent when there is nothing before it")
    void changes() {
        assertEquals("+1.04%", CurrencyFormat.change(new BigDecimal("1.04")));
        assertEquals("-2.00%", CurrencyFormat.change(new BigDecimal("-2")));
        assertEquals("", CurrencyFormat.change(null));
    }

    @Test
    @DisplayName("the history puts each rate beside its change from the next-older one, the first beside none")
    void history() {
        LocalDate day = DAY;
        List<RateHistoryLine> lines = RateHistoryLine.of(List.of(
                rate(3, USD, day, "49"), rate(2, USD, day.minusDays(1), "50"), rate(1, USD, day.minusDays(9), "48")));
        assertEquals(new BigDecimal("-2.00"), lines.get(0).changePercent());
        assertEquals(new BigDecimal("4.17"), lines.get(1).changePercent());
        assertNull(lines.get(2).changePercent());
    }
}
