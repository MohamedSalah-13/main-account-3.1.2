package com.hamza.account.features.party.currency;

import com.hamza.account.features.events.PartyKind;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.DAY;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.USD;
import static com.hamza.account.features.party.currency.PartyCurrencyFixtures.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The currency side of saving a movement on a party's account (docs/currency-plan.md §14 ق-ج٤). */
class PartyMovementCurrencyTest {

    private static final int DOLLAR_DRAWER = 2;
    private static final int MAIN = 1;

    private final PartyCurrencyFixtures.Memory memory = new PartyCurrencyFixtures.Memory()
            .party(PartyKind.CUSTOMER, 7, USD)
            .party(PartyKind.SUPPLIER, 9, null)
            .treasury(MAIN, null)
            .treasury(DOLLAR_DRAWER, USD)
            .rate(USD, DAY, "48.5");
    private final PartyMovementCurrency currency = new PartyMovementCurrency(memory);

    @Test
    @DisplayName("the party's currency, the treasury's and the day's rate decide what is stored")
    void figuresFromTheDatabase() throws Exception {
        PartyMovementFigures f = currency.figures(PartyKind.CUSTOMER, 7, DOLLAR_DRAWER, "درج الدولار",
                bd("100"), bd("0"), DAY, bd("0"));
        assertEquals(bd("4850.00"), f.paid());
        assertEquals(bd("100"), f.paidForeign());
    }

    @Test
    @DisplayName("a wallet fee on a treasury in a foreign currency is refused: a fee is an expense")
    void noFeeOnAForeignTreasury() {
        assertThrows(BusinessRuleException.class, () -> currency.figures(PartyKind.CUSTOMER, 7, DOLLAR_DRAWER,
                "درج الدولار", bd("100"), bd("0"), DAY, bd("2")));
    }

    @Test
    @DisplayName("a note does not ask about the treasury the form happens to name")
    void aNoteIgnoresTheTreasury() throws Exception {
        PartyMovementFigures f = currency.figures(PartyKind.SUPPLIER, 9, DOLLAR_DRAWER, "درج الدولار",
                bd("0"), bd("75"), DAY, null);
        assertFalse(f.isForeign());
        assertEquals(bd("75"), f.purchase());
    }

    @Test
    @DisplayName("the foreign half is written for a foreign movement, and on every edit - to clear it too")
    void whatIsWritten() throws Exception {
        currency.write(PartyKind.SUPPLIER, 5, PartyMovementFigures.base(bd("10"), bd("0")), false);
        assertTrue(memory.movements.isEmpty(), "a new movement in the base writes nothing more");

        currency.write(PartyKind.SUPPLIER, 5, PartyMovementFigures.base(bd("10"), bd("0")), true);
        assertTrue(memory.movements.containsKey(5L));
        assertNull(memory.movements.get(5L).paidForeign());

        PartyMovementFigures foreign = currency.figures(PartyKind.CUSTOMER, 7, MAIN, "الرئيسية",
                bd("970"), bd("0"), DAY, null);
        currency.write(PartyKind.CUSTOMER, 6, foreign, false);
        assertEquals(bd("20.00"), memory.movements.get(6L).paidForeign());
    }
}
