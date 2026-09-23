package com.hamza.account.features.party.currency;

import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.STOPPED;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.USD;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A party's opening balance once its currency is known (docs/currency-plan.md §14 ق-ج٢). */
class PartyOpeningTest {

    @Test
    @DisplayName("in the base: what was typed, and nothing foreign")
    void inTheBase() {
        PartyOpening opening = PartyOpening.base(bd("1500"));
        assertFalse(opening.isForeign());
        assertEquals(bd("1500"), opening.amount());
        assertNull(opening.foreign());
        assertNull(opening.rate());
    }

    @Test
    @DisplayName("in dollars: valued at the opening day's rate, which is copied")
    void inDollars() throws Exception {
        PartyOpening opening = PartyOpening.foreign(USD, bd("250"), bd("48.4"));
        assertTrue(opening.isForeign());
        assertEquals(3, opening.currencyId());
        assertEquals(bd("12100.00"), opening.amount());
        assertEquals(bd("250"), opening.foreign());
        assertEquals(bd("48.4"), opening.rate());
    }

    @Test
    @DisplayName("an opening the shop owes is valued the same way")
    void aNegativeOpening() throws Exception {
        assertEquals(bd("-4840.00"), PartyOpening.foreign(USD, bd("-100"), bd("48.4")).amount());
    }

    @Test
    @DisplayName("an opening of zero needs no rate; any other opening without one is refused")
    void zeroNeedsNoRate() throws Exception {
        PartyOpening empty = PartyOpening.foreign(USD, null, null);
        assertEquals(0, empty.amount().signum());
        assertEquals(0, empty.foreign().signum());
        assertNull(empty.rate());
        UserValidationException refused = assertThrows(UserValidationException.class,
                () -> PartyOpening.foreign(USD, bd("10"), null));
        assertEquals("currency.error.no.rate", refused.getMessage());
    }

    @Test
    @DisplayName("a stopped currency, a missing one and too many places are refused")
    void refusals() {
        assertEquals("party.currency.error.inactive", assertThrows(UserValidationException.class,
                () -> PartyOpening.foreign(STOPPED, bd("10"), bd("2"))).getMessage());
        assertEquals("currency.error.not.found", assertThrows(UserValidationException.class,
                () -> PartyOpening.foreign(null, bd("10"), bd("2"))).getMessage());
        assertThrows(UserValidationException.class, () -> PartyOpening.foreign(USD, bd("10.555"), bd("2")));
    }
}
