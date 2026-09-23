package com.hamza.account.features.party.payment;

import com.hamza.account.features.currency.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the collection screen shows before a movement is saved (docs/currency-plan.md §14 ق-ج٤). */
class PartyEntryCurrencyTest {

    private static final Currency EGP = new Currency(1, "EGP", "جنيه مصري", "ج.م", "L.E.", 2, true, true, 1);
    private static final Currency USD = new Currency(3, "USD", "دولار أمريكي", "$", "", 2, false, true, 3);

    @Test
    @DisplayName("a party in the base: typed in the base, and a collection lowers the balance by what was typed")
    void inTheBase() {
        PartyEntryCurrency base = new PartyEntryCurrency(EGP, null, null);
        assertFalse(base.isForeign());
        assertNull(base.typedIn(PartyEntryKind.COLLECTION));
        assertEquals(new BigDecimal("-500"), base.ownChange(PartyEntryKind.COLLECTION, new BigDecimal("500")));
        assertEquals(new BigDecimal("500"), base.ownChange(PartyEntryKind.DEBIT_NOTE, new BigDecimal("500")));
        assertEquals(new BigDecimal("-500"), base.ownChange(PartyEntryKind.CREDIT_NOTE, new BigDecimal("500")));
    }

    @Test
    @DisplayName("dollars into the dollar drawer are typed in dollars and come off the dollar balance")
    void dollarsIntoTheDollarDrawer() {
        PartyEntryCurrency dollars = new PartyEntryCurrency(USD, USD, new BigDecimal("48.5"));
        assertTrue(dollars.isForeign());
        assertSame(USD, dollars.typedIn(PartyEntryKind.COLLECTION));
        assertEquals(new BigDecimal("-100"), dollars.ownChange(PartyEntryKind.COLLECTION, new BigDecimal("100")));
    }

    @Test
    @DisplayName("pounds into the till are typed in pounds and come off the dollar balance at the day's rate")
    void poundsIntoTheTill() {
        PartyEntryCurrency pounds = new PartyEntryCurrency(USD, EGP, new BigDecimal("48.5"));
        assertNull(pounds.typedIn(PartyEntryKind.COLLECTION), "the base, whatever the flagged base is called");
        assertEquals(new BigDecimal("-100.00"), pounds.ownChange(PartyEntryKind.COLLECTION, new BigDecimal("4850")));
        assertNull(new PartyEntryCurrency(USD, null, null).ownChange(PartyEntryKind.COLLECTION, BigDecimal.TEN),
                "no rate: the screen cannot say, and must not guess");
    }

    @Test
    @DisplayName("a note is typed in the party's currency, whatever treasury the form still names")
    void aNoteIsInThePartysCurrency() {
        PartyEntryCurrency note = new PartyEntryCurrency(USD, null, null);
        assertSame(USD, note.typedIn(PartyEntryKind.DEBIT_NOTE));
        assertEquals(new BigDecimal("50"), note.ownChange(PartyEntryKind.DEBIT_NOTE, new BigDecimal("50")));
    }
}
