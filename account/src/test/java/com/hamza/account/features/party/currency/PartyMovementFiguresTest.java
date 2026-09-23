package com.hamza.account.features.party.currency;

import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.EGP;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.KWD;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.SAR;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.USD;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a hand-entered movement stores for a party (docs/currency-plan.md §14 ق-ج٤ and ق-ج٥): which
 * currency its cash is typed in, and the base and own figures that follow.
 */
class PartyMovementFiguresTest {

    @Test
    @DisplayName("a party in the base, through a till in the base: what was typed, and nothing foreign")
    void aPartyInTheBase() throws Exception {
        PartyMovementFigures f = PartyMovementFigures.of(null, null, "الرئيسية", bd("500"), bd("0"), null);

        assertFalse(f.isForeign());
        assertEquals(bd("500"), f.paid());
        assertNull(f.paidForeign());
        assertEquals(bd("500"), f.paidOwn());
    }

    @Test
    @DisplayName("the flagged base is the base: a treasury or a party naming it is not foreign")
    void theFlaggedBaseIsTheBase() throws Exception {
        assertNull(PartyMovementFigures.cashCurrency(EGP, EGP, "الرئيسية"));
        assertFalse(PartyMovementFigures.of(EGP, EGP, "الرئيسية", bd("10"), bd("0"), null).isForeign());
    }

    @Test
    @DisplayName("dollars into the dollar drawer: typed in dollars, valued at the day's rate")
    void dollarsIntoADollarDrawer() throws Exception {
        assertSame(USD, PartyMovementFigures.cashCurrency(USD, USD, "درج الدولار"));
        PartyMovementFigures f = PartyMovementFigures.of(USD, USD, "درج الدولار", bd("100"), bd("0"), bd("48.5"));

        assertTrue(f.isForeign());
        assertEquals(bd("4850.00"), f.paid());
        assertEquals(bd("100"), f.paidForeign());
        assertEquals(bd("0.00"), f.purchase());
        assertEquals(0, f.purchaseForeign().signum());
        assertEquals(bd("48.5"), f.rate());
        assertEquals(bd("100"), f.paidOwn(), "an allocation is measured in dollars");
    }

    @Test
    @DisplayName("pounds into the pound till from a dollar customer: typed as they arrived, worth dollars at the rate")
    void poundsFromADollarCustomer() throws Exception {
        assertNull(PartyMovementFigures.cashCurrency(USD, null, "الرئيسية"));
        PartyMovementFigures f = PartyMovementFigures.of(USD, null, "الرئيسية", bd("4850"), bd("0"), bd("48.5"));

        assertEquals(bd("4850"), f.paid());
        assertEquals(bd("100.00"), f.paidForeign());
    }

    @Test
    @DisplayName("a treasury in a third currency is refused, and so is a foreign drawer for a party in the base")
    void aThirdCurrencyIsRefused() {
        assertThrows(BusinessRuleException.class,
                () -> PartyMovementFigures.of(USD, SAR, "درج الريال", bd("10"), bd("0"), bd("48.5")));
        assertThrows(BusinessRuleException.class,
                () -> PartyMovementFigures.of(null, USD, "درج الدولار", bd("10"), bd("0"), null));
    }

    @Test
    @DisplayName("a note moves no cash, so the treasury is not asked about, and it is typed in the party's currency")
    void aNoteIsInThePartysCurrency() throws Exception {
        PartyMovementFigures f = PartyMovementFigures.of(USD, SAR, "درج الريال", bd("0"), bd("-50"), bd("48.5"));

        assertEquals(bd("-2425.00"), f.purchase());
        assertEquals(bd("-50"), f.purchaseForeign());
        assertEquals(0, f.paidForeign().signum());
    }

    @Test
    @DisplayName("no rate for a party in a foreign currency is a refusal - never a zero, never a one")
    void noRateIsARefusal() {
        UserValidationException refused = assertThrows(UserValidationException.class,
                () -> PartyMovementFigures.of(USD, USD, "درج الدولار", bd("10"), bd("0"), null));
        assertEquals("currency.error.no.rate", refused.getMessage());
    }

    @Test
    @DisplayName("more places than the currency has is refused - in dollars, and in the base")
    void placesAreTheCurrencys() {
        assertThrows(UserValidationException.class,
                () -> PartyMovementFigures.of(USD, USD, "درج الدولار", bd("10.555"), bd("0"), bd("48.5")));
        assertThrows(UserValidationException.class,
                () -> PartyMovementFigures.of(USD, null, "الرئيسية", bd("10.555"), bd("0"), bd("48.5")));
    }

    @Test
    @DisplayName("a Kuwaiti dinar customer's note keeps its three places")
    void threePlaces() throws Exception {
        PartyMovementFigures f = PartyMovementFigures.of(KWD, null, "", bd("0"), bd("1.255"), bd("160"));
        assertEquals(bd("1.255"), f.purchaseForeign());
        assertEquals(bd("200.80"), f.purchase());
    }
}
